package plugins.Freetalk;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.Logger;

public final class SubscribedBoard extends Board {
	
	private final FTOwnIdentity mSubscriber;


    public synchronized String getDescription() {
        /* TODO: Implement: Use the description which most of the subscriber's trustees have chosen. */
        return "";
    }
    
    @SuppressWarnings("unchecked")
	public synchronized Date getLatestMessageDate(FTOwnIdentity viewer) throws NoSuchMessageException {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this);
        q.descend("mMessageDate").orderDescending();
        ObjectSet<MessageReference> allMessages = q.execute();
        
        if(viewer != null) {
	        for(MessageReference ref : allMessages) {
	        	final Message message = ref.getMessage();
	        	if(message != null && viewer.wantsMessagesFrom(message.getAuthor()))
	        		return message.getDate();
	        }
        } else {
        	for(MessageReference ref : allMessages) {
        		final Message message = ref.getMessage();
        		if(message != null)
        			return message.getDate();
        	}
        }
        
        
        throw new NoSuchMessageException();
    }
    
    /**
     * Called by the {@link MessageManager} to add a just received message to the board.
     * The job for this function is to find the right place in the thread-tree for the new message and to move around older messages
     * if a parent message of them is received.
     * 
     * Does not store the message, you have to do this before!
     */
    protected synchronized void addMessage(Message newMessage) {        
    	if(newMessage instanceof OwnMessage) {
    		/* We do not add the message to the boards it is posted to because the user should only see the message if it has been downloaded
    		 * successfully. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
    		throw new IllegalArgumentException("Adding OwnMessages to a board is not allowed.");
    	}
    	
    	BoardThreadLink ghostRef = null;
    	
    	try {
    		// If there was a ghost thread reference for the new message, we associate the message with it - even if it is no thread:
    		// People are allowed to reply to non-threads as if they were threads, which results in a 'forked' thread.
    		ghostRef = getThreadLink(newMessage.getID());
    		ghostRef.setMessage(newMessage);
    		ghostRef.storeWithoutCommit(db);
    		
    		linkThreadRepliesToNewParent(newMessage.getID(), newMessage);
    	}
    	catch(NoSuchMessageException e) {
    		// If there was no ghost reference, we must store a BoardThreadLink if the new message is a thread 
			if(newMessage.isThread()) {
	    		BoardThreadLink threadRef = new BoardThreadLink(this, newMessage, getFreeMessageIndex());
	    		threadRef.storeWithoutCommit(db);
	    		
	    		// We do not call linkThreadRepliesToNewParent() here because if there was no ghost reference for the new message this means that no replies to
	    		// it were received yet.
			}
    	}
    
    	if(newMessage.isThread() == false) {
    		// The new message is no thread. We must:
    		
    		// 1. Find it's parent thread, create a ghost reference for it if it does not exist.
    		BoardThreadLink parentThreadRef = findOrCreateParentThread(newMessage);
    		
    		// 2. Tell it about it's parent thread if it exists.
    		Message parentThread = parentThreadRef.getMessage(); // Can return null if the parent thread was not downloaded yet, then it's a ghost reference.
    		if(parentThread != null)
    			newMessage.setThread(parentThread);
    		
    		// 3. Update the last reply date of the parent thread
    		parentThreadRef.updateLastReplyDate(newMessage.getDate());
    		parentThreadRef.storeWithoutCommit(db);
    		
    		// 4. Store a BoardReplyLink for the new message
    		BoardReplyLink messageRef;
    		try {
    			// If addMessage() was called already for the given message (this might happen due to transaction management of the message manager), we must
    			// use the already stored reply link for the message.
    			messageRef = getReplyLink(newMessage);
    		}
    		catch(NoSuchMessageException e) {
    			messageRef = new BoardReplyLink(this, newMessage, getFreeMessageIndex());
    			messageRef.storeWithoutCommit(db);
    		}
    		
    		// 5. Try to find the new message's parent message and tell it about it's parent message if it exists.
    		try {
    			// Try to find the parent message of the message
    			
    			// FIXME: This allows crossposting. Figure out whether we need to handle it specially:
    			// What happens if the message has specified a parent thread which belongs to this board BUT a parent message which is in a different board
    			// and does not belong to the parent thread
    			newMessage.setParent(mMessageManager.get(newMessage.getParentID()));
    		}
    		catch(NoSuchMessageException e) {
    			// The parent message of the message was not downloaded yet
    			// TODO: The MessageManager should try to download the parent message if it's poster has enough trust.
    		}

    		linkThreadRepliesToNewParent(parentThreadRef.getThreadID(), newMessage);
    	}

    	storeWithoutCommit();
    }
    
    /**
     * Called by the {@link MessageManager} before a {@link Message} object is deleted from the database.
     * This usually happens when an {@link FTIdentity} is being deleted.
     * 
     * Does not delete the Message object itself, this is to be done by the callee.
     * 
     * @param message The message which is about to be deleted. It must still be stored within the database so that queries on it work.
     * @throws NoSuchMessageException If the message does not exist in this Board.
     */
    protected synchronized void deleteMessage(Message message) throws NoSuchMessageException {
    	
    	try {
    		// Check whether the message was listed as a thread.
    		BoardThreadLink threadLink = getThreadLink(message.getID());
    		
    		// If it was listed as a thread and had no replies, we can delete it's ThreadLink.
    		// We do not delete the ThreadLink if it has replies already: We want the replies to stay visible and therefore the ThreadLink has to be kept,
    		// db4o will set it's mMessage field to null after the message was deleted so it will become a ghost thread reference.
	    	if(getAllThreadReplies(message.getID(), false).size() == 0)
	    		threadLink.deleteWithoutCommit(db);
    	}
    	catch(NoSuchMessageException e) { // getThreadReference failed
    		if(message.isThread()) {
				Logger.error(this, "Should not happen: deleteMessage() called for a thread which does not exist in this Board.", e);
				throw e;
    		}
    	}
    	
    	if(message.isThread() == false) {
			try {
				getReplyLink(message).deleteWithoutCommit(db);
			} catch (NoSuchMessageException e) {
				Logger.error(this, "Should not happen: deleteMessage() called for a reply message which does not exist in this Board.", e);
				throw e;
			}
    	}
    }

    /**
     * For a new thread, calls setParent() for all messages which are a reply to it and setThread() for all messages which belong to the new thread.
     * For a new message, i.e. reply to a thread, calls setParent() for all messages which are a reply to it.
     *      
     * Assumes that the transient fields of the newMessage are initialized already.
     */
    private synchronized void linkThreadRepliesToNewParent(String parentThreadID, Message newMessage) {
    	
    	boolean newMessageIsThread = (newMessage.getID().equals(parentThreadID));
 
    	for(MessageReference ref : getAllThreadReplies(parentThreadID, false)) {
    		Message threadReply = ref.getMessage();
    		
    		try {
    			threadReply.getParent();
    		}
    		catch(NoSuchMessageException e) {
    			try {
    				if(threadReply.getParentID().equals(newMessage.getID()))
    					threadReply.setParent(newMessage);
    			}
    			catch(NoSuchMessageException ex) {
    				Logger.debug(this, "SHOULD NOT HAPPEN: getParentID() failed for a thread reply: " + threadReply, ex);
    			}
    		}
    		
    		if(newMessageIsThread) {
	    		try {
	    			threadReply.getThread();
	    		}
	    		catch(NoSuchMessageException e) {
	    			threadReply.setThread(newMessage);
	    		}
    		}
    	}
    }
    
    @SuppressWarnings("unchecked")
	public synchronized BoardReplyLink getReplyLink(Message message) throws NoSuchMessageException {
        Query q = db.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mMessage").constrain(message).identity();
        ObjectSet<BoardReplyLink> results = q.execute();
        
        switch(results.size()) {
	        case 1:
				BoardReplyLink messageRef = results.next();
				assert(messageRef.getMessage().equals(message)); // The query works
				return messageRef;
	        case 0:
	        	throw new NoSuchMessageException(message.getID());
	        default:
	        	throw new DuplicateMessageException(message.getID());
        }
    }
    
    @SuppressWarnings("unchecked")
	public synchronized BoardThreadLink getThreadLink(String threadID) throws NoSuchMessageException {
        Query q = db.query();
        q.constrain(BoardThreadLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        ObjectSet<BoardThreadLink> results = q.execute();
        
        switch(results.size()) {
	        case 1:
				BoardThreadLink threadRef = results.next();
				assert(threadRef.getMessage().getID().equals(threadID)); // The query works
				return threadRef;
	        case 0:
	        	throw new NoSuchMessageException(threadID);
	        default:
	        	throw new DuplicateMessageException(threadID);
        }
    }

    /**
     * Returns the {@link BoardThreadLink} of the parent thread of the given message.
     * If the parent thread was not downloaded yet, a ghost BoardThreadLink is created and stored for it, without committing the transaction. 
     * You have to lock the board and the database before calling this function.
     * 
     * If the parent thread was downloaded but is no thread actually, a new thread is 'forked' off, making the parent thread message of the given message
     * both appear as a reply to the original thread where it belonged AND as a thread on it's own to which the given message belong.
     * 
     * The transient fields of the returned message will be initialized already.
     * @throws NoSuchMessageException
     */
    private synchronized BoardThreadLink findOrCreateParentThread(Message newMessage) {
    	String parentThreadID;
    	
    	try {
    		parentThreadID = newMessage.getThreadID();
    	}
    	catch(NoSuchMessageException e) {
    		Logger.error(this, "SHOULD NOT HAPPEN: findOrCreateParentThread called for a message where getThreadID failed: " + e);
    		throw new IllegalArgumentException(e);
    	}

    	try {
    		// The parent thread was downloaded and marked as a thread already, we return its BoardThreadLink
    		return getThreadLink(parentThreadID);
    	}
    	catch(NoSuchMessageException e) {
    		// There is no thread reference for the parent thread yet. Either it was not downloaded yet or it was downloaded but is no thread.
    		try {
    			Message parentThread = mMessageManager.get(parentThreadID);
    			
    			if(Arrays.binarySearch(parentThread.getBoards(), this) < 0) {
    				// The parent thread is not a message in this board.
    				// TODO: Decide whether we should maybe store a flag in the BoardThreadLink which marks it.
    				// IMHO it is part of the UI's job to read the board list of the actual Message object and display something if the thread is not
    				// really a message to this board.
    			}

    			// The parent thread was downloaded and is no thread actually, we create a BoardThreadLink for it and therefore 'fork' a new thread off
    			// that message. The parent thread message will still be displayed as a reply to it's original thread, but it will also appear as a new thread
    			// which is the parent of the message which was passed to this function.

    			BoardThreadLink parentThreadRef = new BoardThreadLink(this, parentThread, getFreeMessageIndex()); 
    			parentThreadRef.storeWithoutCommit(db);
    			return parentThreadRef;
    		}
    		catch(NoSuchMessageException ex) { 
    			// The message manager did not find the parentThreadID, so the parent thread was not downloaded yet, we create a ghost thread reference for it.
    			BoardThreadLink ghostThreadRef = new BoardThreadLink(this, parentThreadID, newMessage.getDate(), getFreeMessageIndex());
    			ghostThreadRef.storeWithoutCommit(db);
    			return ghostThreadRef;
    		}		
    	}
    }


    /**
     * Get all threads in the board. The view is specified to the FTOwnIdentity displaying it, therefore you have to pass one as parameter.
     * The transient fields of the returned messages will be initialized already.
     * @param identity The identity viewing the board.
     * @return An iterator of the message which the identity will see (based on its trust levels).
     */
    @SuppressWarnings("unchecked")
    public synchronized Iterable<BoardThreadLink> getThreads(final FTOwnIdentity identity) {
    	return new Iterable<BoardThreadLink>() {
		public Iterator<BoardThreadLink> iterator() {
        return new Iterator<BoardThreadLink>() {
            private final FTOwnIdentity mIdentity = identity;
            private final Iterator<BoardThreadLink> iter;
            private BoardThreadLink next;

            {
                Query q = db.query();
                q.constrain(BoardThreadLink.class);
                q.descend("mBoard").constrain(Board.this).identity();
                q.descend("mLastReplyDate").orderDescending();
                               
                iter = q.execute().iterator();
                next = iter.hasNext() ? iter.next() : null;
            }

            public boolean hasNext() {
                for(; next != null; next = iter.hasNext() ? iter.next() : null)
                {
                	if(next.getMessage() == null)
                		return true; // FIXME: Get the author from the message ID 
                	
                    if(mIdentity.wantsMessagesFrom(next.getMessage().getAuthor()))
                        return true;
                }
                return false;
            }

            public BoardThreadLink next() {
                BoardThreadLink result = hasNext() ? next : null;
                next = iter.hasNext() ? iter.next() : null;
                return result;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
		};
    	}
        };
    }


    /* FIXME: This function returns all messages, not only the ones which the viewer wants to see. Convert the function to an iterator
     * which picks threads chosen by the viewer, see threadIterator() for how to do this */
    @SuppressWarnings("unchecked")
    public synchronized List<MessageReference> getAllMessages(final boolean sortByMessageIndexAscending) {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending(); /* Needed for NNTP */
        }
        return q.execute();
    }

    @SuppressWarnings("unchecked")
    public synchronized int getMessageIndex(Message message) throws NoSuchMessageException {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mMessage").constrain(message).identity();
        ObjectSet<MessageReference> result = q.execute();

        if(result.size() == 0)
            throw new NoSuchMessageException(message.getID());

        return result.next().getIndex();
    }

    /* FIXME: This function counts all messages, not only the ones which the viewer wants to see. */
    public synchronized int getLastMessageIndex() {
        return getFreeMessageIndex() - 1;
    }

    @SuppressWarnings("unchecked")
    public synchronized Message getMessageByIndex(int index) throws NoSuchMessageException {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessageIndex").constrain(index);
        ObjectSet<MessageReference> result = q.execute();
        
        switch(result.size()) {
	        case 1:
	        	return result.next().getMessage();
	        case 0:
	            throw new NoSuchMessageException();
	        default:
	        	throw new DuplicateMessageException("index " + Integer.toString(index));
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<MessageReference> getMessagesByMinimumIndex(
            int minimumIndex,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (minimumIndex > 0) {
            q.descend("mMessageIndex").constrain(minimumIndex).smaller().not();
        }
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
        return q.execute();
    }

    @SuppressWarnings("unchecked")
    public synchronized List<MessageReference> getMessagesByMinimumDate(
            long minimumDate,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (minimumDate > 0) {
            q.descend("mMessageDate").constrain(minimumDate).smaller().not();
        }
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
        return q.execute();
    }

    /**
     * Get the next free NNTP index for a message. Please synchronize on this Board when creating a message, this method
     * does not and cannot provide synchronization as creating a message is no atomic operation.
     */
    @SuppressWarnings("unchecked")
    private int getFreeMessageIndex() {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessageIndex").orderDescending(); /* FIXME: Use a db4o native query to find the maximum instead of sorting. O(n) vs. O(n log(n))! */
        ObjectSet<MessageReference> result = q.execute();
        return result.size() == 0 ? 1 : result.next().getIndex()+1;
    }

    /**
     * Get the number of messages in this board.
     */
    /* FIXME: This function counts all messages, not only the ones which the viewer wants to see. */
    public synchronized int messageCount() {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        return q.execute().size();
    }

    /**
     * Get the number of replies to the given thread.
     */
    /* FIXME: This function counts all replies, not only the ones which the viewer wants to see. */
    public synchronized int threadReplyCount(FTOwnIdentity viewer, String threadID) {
        return getAllThreadReplies(threadID, false).size();
    }

    /**
     * Get all replies to the given thread, sorted ascending by date if requested
     */
    // FIXME: This function returns all replies, not only the ones which the viewer wants to see. Convert the function to an iterator
    // which picks threads chosen by the viewer, see threadIterator() for how to do this.
    @SuppressWarnings("unchecked")
    public synchronized List<BoardReplyLink> getAllThreadReplies(String threadID, final boolean sortByDateAscending) {
        Query q = db.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        
        if (sortByDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
       
        return q.execute();
    }

    public static abstract class MessageReference {
    	
    	protected final Board mBoard;
    	
    	protected Message mMessage;
    	
    	protected Date mMessageDate;
    	
    	protected final int mMessageIndex;

    	
    	private MessageReference(Board myBoard, int myMessageIndex) {
        	if(myBoard == null)
        		throw new NullPointerException();
        	
    		mBoard = myBoard;
    		mMessage = null;
    		mMessageDate = null;
    		mMessageIndex = myMessageIndex;
    		
    		assert(mMessageIndex >= mBoard.getFreeMessageIndex());
    	}

		private MessageReference(Board myBoard, Message myMessage, int myMessageIndex) {
    		this(myBoard, myMessageIndex);
    		
    		if(myMessage == null)
    			throw new NullPointerException();
    		
    		mMessage = myMessage;
    		mMessageDate = mMessage.getDate();
    	}
    	
        /** Get the message to which this reference points */
        public synchronized Message getMessage() {
            /* We do not have to initialize mBoard and can assume that it is initialized because a MessageReference will only be loaded
             * by the board it belongs to. */
        	mBoard.db.activate(this, 3); // FIXME: Figure out a reasonable depth
        	mMessage.initializeTransient(mBoard.db, mBoard.mMessageManager);
            return mMessage;
        }
        
        /** Get an unique index number of this message in the board where which the query for the message was executed.
         * This index number is needed for NNTP and for synchronization with client-applications: They can check whether they have all messages by querying
         * for the highest available index number. */
        public int getIndex() {
        	return mMessageIndex;
        }
        
        /**
         * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
         */
        protected void storeWithoutCommit(ExtObjectContainer db) {
        	try {
        		DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.

        		db.store(this);
        	}
        	catch(RuntimeException e) {
        		DBUtil.rollbackAndThrow(db, this, e);
        	}
        }
        
    	protected void deleteWithoutCommit(ExtObjectContainer db) {
    		try {
    			DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
    			
    			DBUtil.checkedDelete(db, this);
    		}
    		catch(RuntimeException e) {
        		DBUtil.rollbackAndThrow(db, this, e);
        	}
			
		}
    }

    
    // FIXME: This class was made static so that it does not store an internal reference to it's Board object because we store that reference in mBoard already,
    // for being able to access it with db4o queries. Reconsider whether it should really be static: It would be nice if db4o did store an index on mMessageIndex
    // *per-board*, and not just a global index - the message index is board-local anyway! Does db4o store a per-board index if the class is not static???
    /**
     * Helper class to associate messages with boards in the database
     */
    public static class BoardReplyLink extends MessageReference { /* TODO: This is only public for configuring db4o. Find a better way */
        
        private final String mThreadID;

        protected BoardReplyLink(Board myBoard, Message myMessage, int myIndex) {
        	super(myBoard, myMessage, myIndex);
            
            try {
            	mThreadID = mMessage.getThreadID();
            }
            catch(NoSuchMessageException e) {
            	throw new IllegalArgumentException("Trying to create a BoardReplyLink for a thread, should be a BoardThreadLink.");
            }
            
        }
        
        public String getThreadID() {
        	return mThreadID;
        }

    }
    
    public final static class BoardThreadLink  extends MessageReference {
        
        private final String mThreadID;
        
    	private Date mLastReplyDate;
    	
    	
    	protected BoardThreadLink(Board myBoard, Message myThread, int myMessageIndex) {
    		super(myBoard, myThread, myMessageIndex);
    		
    		if(myThread == null)
    			throw new NullPointerException();
    		
    		mThreadID = mMessage.getID();
    		mLastReplyDate = myThread.getDate();
    	}
    	
    	/**
    	 * @param myLastReplyDate The date of the last reply to this thread. This parameter must be specified at creation to prevent threads from being hidden if
    	 * 							the user of this constructor forgot to call updateLastReplyDate() - thread display is sorted descending by reply date!
    	 */
    	protected BoardThreadLink(Board myBoard, String myThreadID, Date myLastReplyDate, int myMessageIndex) {
    		super(myBoard, myMessageIndex);
    		
    		if(myThreadID == null)
    			throw new NullPointerException();
    		
    		// TODO: We might validate the thread id here. Should be safe not to do so because it is taken from class Message which validates it.
    		
    		mThreadID = myThreadID;
    		mLastReplyDate = myLastReplyDate;
    	}
		
		protected synchronized void updateLastReplyDate(Date newDate) {
			if(newDate.after(mLastReplyDate))
				mLastReplyDate = newDate;
		}
		
		public synchronized Date getLastReplyDate() {
			return mLastReplyDate;
		}
    	
		public String getThreadID() {
			return mThreadID;
		}
		
		@Override
		public synchronized Message getMessage() {
			if(mMessage == null)
				return null;
			
			return super.getMessage();
		}
		
		public synchronized void setMessage(Message myThread) {
			if(myThread == null)
				throw new NullPointerException();
			
			if(myThread.getID().equals(mThreadID) == false)
				throw new IllegalArgumentException();
			
			mMessage = myThread;
			
			updateLastReplyDate(myThread.getDate());
		}
	
    }

    
    private final static class ThreadWasReadMarker {
    	
    	private final String mID;
    	
    	private final FTOwnIdentity mReader;
    	
    	private final BoardThreadLink mThread;
    	
    	
    	protected ThreadWasReadMarker(FTOwnIdentity viewer, BoardThreadLink thread) {
    		mID = calculateID(viewer, thread, replyCount);
    		mReader = viewer;
    		mMessage = thread;
    		mReplyCount = replyCount;
    	}
    	
    	protected static String calculateID(FTOwnIdentity viewer, Message thread, int replyCount) {
    		return Integer.toString(replyCount) + "@" + thread.getID() + "@" + viewer.getID();
    	}
    	
    }
    
    public synchronized void setThreadReadStatus(FTOwnIdentity viewer, Message thread, int replyCount, boolean wasRead) {
    	
    }
    
    public synchronized boolean threadWasRead(FTOwnIdentity viewer, Message thread) {
    	return false;
    }
}
