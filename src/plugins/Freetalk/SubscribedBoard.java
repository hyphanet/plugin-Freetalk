package plugins.Freetalk;

import java.util.Date;
import java.util.Random;

import plugins.Freetalk.Message.MessageID;
import plugins.Freetalk.Persistent.IndexedClass;
import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;
import freenet.support.codeshortification.IfNotEquals;
import freenet.support.codeshortification.IfNull;

/**
 * A SubscribedBoard is a {@link Board} which only stores messages which the subscriber (a {@link OwnIdentity}) wants to read,
 * according to the implementation of {@link OwnIdentity.wantsMessagesFrom}.
 */
@IndexedClass
public final class SubscribedBoard extends Board {

	private final OwnIdentity mSubscriber;
	
	private final Board mParentBoard;
	
	/**
	 * The description which the subscriber has specified for this Board. Null if he has not specified any.
	 */
	private String mDescription = null;
	
	/**
	 * Index of the latest message which this board has pulled from it's parent board. 
	 */
	private int	mHighestSynchronizedParentMessageIndex = 0;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(SubscribedBoard.class);
	}

	
	public SubscribedBoard(Board myParentBoard, OwnIdentity mySubscriber) throws InvalidParameterException {
		super(myParentBoard.getName(), myParentBoard.getDescription(mySubscriber), true);
		
		// .getName() does this for us.
		// if(myParentBoard == null) throw new NullPointerException();
		
		if(mySubscriber == null) throw new NullPointerException();
		
		mParentBoard = myParentBoard;
		mSubscriber = mySubscriber;
	}

	@Override public void databaseIntegrityTest() throws Exception {
    	super.databaseIntegrityTest();
    	
		checkedActivate(1);
    	
    	IfNull.thenThrow(mSubscriber, "mSubscriber");
    	IfNull.thenThrow(mParentBoard, "mParentBoard");

    	IfNotEquals.thenThrow(getName(), getParentBoard().getName(), "mName");
        	
    	if(mHighestSynchronizedParentMessageIndex < 0)
    		throw new IllegalStateException("mHighestSynchronizedParentMessageIndex == " + mHighestSynchronizedParentMessageIndex);
    
    	for(DownloadedMessageLink parentLink : getParentBoard().getDownloadedMessagesAfterIndex(0)) {
    		if(parentLink.getMessageIndex() > mHighestSynchronizedParentMessageIndex)
    			continue;
    		
    		boolean found = false;
    		
			for(BoardMessageLink ref : getMessageLinks(parentLink.getMessage().getID())) {
				try {
					// We must not only check whether there is a BoardMessageLink for the message ID but also whether the message is actually
					// set on the ref... it might be a ghost reference only.
					if(ref.getMessage() == parentLink.getMessage()) { 
						found = true;
						// TODO: Validate whether the type of the message reference fits the type of the message (thread, reply, etc.)
						break;
					}
				} catch(NoSuchMessageException e) {}
			}
			
			if(!found) {
				try {
					getUnwantedMessageLink(parentLink.getMessage());
				} catch(NoSuchMessageException e2) {
					throw new IllegalStateException("mHighestSynchronizedParentMessageIndex == " + mHighestSynchronizedParentMessageIndex 
							+ " but missing message with index " + parentLink.getMessageIndex());
				}
			}
    	}
    }

	@Override protected void storeWithoutCommit() {
		checkedActivate(1);
    	throwIfNotStored(mSubscriber);
    	throwIfNotStored(mParentBoard);
    	super.storeWithoutCommit();
    }

	@Override protected void deleteWithoutCommit() {
		// TODO: When deleting a subscribed board, check whether the objects of class Message are being used by a subscribed board of another own identity.
		// If not, delete the messages.
		try {
			checkedActivate(1);
			
			for(UnwantedMessageLink link : getAllUnwantedMessages()) {
				link.deleteWithoutCommit();
			}
			
			for(BoardMessageLink ref : getAllMessages(false)) {
				ref.deleteWithoutCommit();
			}

			checkedDelete();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}

	}
	
	public OwnIdentity getSubscriber() {
		checkedActivate(1);
    	if(mSubscriber instanceof Persistent) {
    		final Persistent subscriber = (Persistent)mSubscriber;
    		subscriber.initializeTransient(mFreetalk);
    	}
    	return mSubscriber;
	}
	
	public Board getParentBoard() {
		checkedActivate(1);
		mParentBoard.initializeTransient(mFreetalk);
		return mParentBoard;
	}

    public synchronized String getDescription() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
        return mDescription != null ? mDescription : super.getDescription(getSubscriber());
    }
    
    /**
     * Gets the reference to the latest message. Does not return ghost thread references - therefore, the returned BoardMessageLink will always
     * point to a valid Message object.
     * 
     * TODO: Make this function return class Message and not class BoardMessageLink because it won't return BoardMessageLink objects whose Message
     * is not downloaded yet anyway.
     * 
     * @throws NoSuchMessageException If the board is empty.
     */
	public synchronized BoardMessageLink getLatestMessage() throws NoSuchMessageException {
    	// TODO: We can probably cache the latest message date in this SubscribedBoard object.
    	
        final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this);
        q.descend("mDate").orderDescending();
      
        // Do not use a constrain() because the case where the latest message has no message object should not happen very often.
        for(BoardMessageLink ref : new Persistent.InitializingObjectSet<BoardMessageLink>(mFreetalk, q)) {
        	try {
        		ref.getMessage(); // Check whether the message was downloaded
        		return ref;
        	}
        	catch(MessageNotFetchedException e)  {
        		// Continue to next BoardMessageLink
        	}
        }
        
        throw new NoSuchMessageException();
    }
    
    /**
     * Called by the {@link MessageManager} when the parent board has received new messages.
     * Does not delete messages, only adds new messages.
     * 
     * @throws Exception If one of the addMessage calls fails. 
     */
    protected synchronized final void synchronizeWithoutCommit() throws Exception {
    	checkedActivate(1);
    	
    	for(Board.DownloadedMessageLink messageLink : getParentBoard().getDownloadedMessagesAfterIndex(mHighestSynchronizedParentMessageIndex)) {
    		addMessage(messageLink.getMessage());
    		mHighestSynchronizedParentMessageIndex = messageLink.getMessageIndex();
    	}
    	
    	storeWithoutCommit();
    }
    
    /**
     * Checks whether the subscriber wants the given message.
     * @return True if the message is wanted.
     */
    private final boolean isMessageWanted(Message newMessage) throws Exception {
    	return getSubscriber().wantsMessagesFrom(newMessage.getAuthor());
    }
    
    /**
     * Deletes the given message and then stores an {@link UnwantedMessageLink} for it.
     * If there is one already, its retry count is incremented (which effectively increases the delay until the next retry will be done).
     */
    private final void deleteMessageAndStoreOrUpdateUnwantedMessageLink(Message newMessage) {
		Logger.normal(this, "deleteMessageAndStoreOrUpdateUnwantedMessageLink: Ignoring message from " + newMessage.getAuthor().getNickname() + " because " +
				getSubscriber().getNickname() + " does not want his messages: " + newMessage);
		
		if(getMessageLinks(newMessage.getID()).size() > 0) {
			try {
				deleteMessage(newMessage, false);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		try {
			UnwantedMessageLink link = getUnwantedMessageLink(newMessage);
			if(logMINOR) Logger.minor(this, "Updating UnwantedMessageLink for " + newMessage);
			link.countRetry();
			link.storeWithoutCommit();
		} catch(NoSuchMessageException e) {
			if(logMINOR) Logger.minor(this, "Storing UnwantedMessageLink for " + newMessage);
			UnwantedMessageLink link = new UnwantedMessageLink(this, newMessage);
			link.initializeTransient(mFreetalk);
			link.storeWithoutCommit();
		}
		
		Logger.normal(this, "deleteMessageAndStoreOrUpdateUnwantedMessageLink finished.");
    }
    
    /**
     * Checks whether there is an {@link UnwantedMessageLink} stored for the given message.
     * Deletes it if yes, if not, does nothing.
     */
    private final void maybeDeleteUnwantedMessageLink(Message newMessage) {
    	if(logMINOR) Logger.minor(this, "maybeDeleteUnwantedMessageLink " + newMessage);
    	
		try {
			UnwantedMessageLink link = getUnwantedMessageLink(newMessage);
			if(logMINOR) Logger.minor(this, "Link found, deleting: " + link);
			link.deleteWithoutCommit();
		} catch(NoSuchMessageException e) {
			if(logMINOR) Logger.minor(this, "No link found.");
		}
		
		if(logMINOR) Logger.minor(this, "maybeDeleteUnwantedMessageLink finished.");
    }
    
    /**
     * Checks whether there are any {@link BoardThreadLink} or {@link BoardReplyLink} stored which reference the given message by ID.
     * If yes, they are given the reference to the given message object - their pointer to the message object was probably null because
     * the message is new.
     */
    private final void updateExistingReferencesToNewMessage(Message newMessage) {
    	final String newMessageID = newMessage.getID();
    	
    	try {
    		// If there was a ghost thread reference for the new message, we associate the message with it - even if it is no thread:
    		// People are allowed to reply to non-threads as if they were threads, which results in a 'forked' thread.
    		final BoardThreadLink ghostThreadRef = getThreadLink(newMessageID);
    		ghostThreadRef.setMessage(newMessage);
    		ghostThreadRef.storeWithoutCommit();
    	} catch(NoSuchMessageException e) { }
    	
    	
    	// Check whether someone has already replied to the message... If yes, then there is a BoardReplyLink for it with getMessage()==null
    	// and we must setMessage(newMessage) on it... 
    	
    	
    	String newMessageThreadID;
    	
		try {
			newMessageThreadID = newMessage.getThreadID();
		} catch(NoSuchMessageException e) {
			newMessageThreadID = null;
		}
		
		for(final BoardReplyLink ghostReplyRef : getReplyLinks(newMessageID)) {
			if(newMessageThreadID == null) {
				Logger.warning(this, "Not updating ghost BoardReplyLink: Parent message is a thread, not a reply: " + ghostReplyRef);
				continue;
			}
			
			if(newMessageThreadID.equals(ghostReplyRef.getThreadID())) {
				ghostReplyRef.setMessage(newMessage);
				ghostReplyRef.storeWithoutCommit();

				// The thread must update its lastReplyTime...
				try {
					final BoardThreadLink thread = getThreadLink(ghostReplyRef.getThreadID());
					thread.onMessageAdded(newMessage);
					thread.storeWithoutCommit();
				} catch(NoSuchMessageException e) {
					// There are BoardReplyLinks for a non-existent thread... this should not happen.
					throw new RuntimeException(e);
				}
			} else {
				Logger.warning(this, "Not updating ghost BoardReplyLink: Parent message is no reply to this thread: " + ghostReplyRef);
			}

		}	
		
		// Check whether there is a ghost link for the parent message. If yes, we must update it's date guess
		
		if(newMessageThreadID != null) {
			try {
				// getReplyLink and getParentID might throw NoSuchMessageException, both are included in the concept of this code 
				final BoardReplyLink ghostLink = getReplyLink(newMessageThreadID, newMessage.getParentID());
				
				try {
					ghostLink.getMessage();
				} catch(MessageNotFetchedException e) {
					final Date parentDateGuess = new Date(newMessage.getDate().getTime() - 1);
					// Update it's date guess if necessary
					if(parentDateGuess.before(ghostLink.getMessageDate())) {
						ghostLink.setMessageDate(parentDateGuess);
						ghostLink.storeWithoutCommit();
					}
					// TODO: Maybe we also want to do something about the title guess?
				}
			} catch(NoSuchMessageException e) {
				// The message had no parent ID or there was no ghost link for the parent yet.
			}
		}
    }
    
    /**
     * The job for this function is to find the right place in the thread-tree for the new message and to move around older messages
     * if a parent message of them is received.
     * 
     * Does not store the message, you have to do this before!
     * 
     * Only to be used by the SubscribedBoard itself, the MessageManager should use {@link synchronizeWithoutCommit}. 
     * 
     * @throws Exception If wantsMessagesFrom(author of newMessage) fails. 
     */
    @Override protected synchronized final void addMessage(Message newMessage) throws Exception {
    	Logger.normal(this, "addMessage " + newMessage + " for " + getSubscriber());
    	
    	// Sanity checks
    	throwIfNotAllowedInThisBoard(newMessage);
    	
    	// Check whether the subscriber wants the message
    	if(isMessageWanted(newMessage)) {
    		// Maybe delete an obsolete UnwantedMessageLink if it exists
    		maybeDeleteUnwantedMessageLink(newMessage);
    	} else {
    		deleteMessageAndStoreOrUpdateUnwantedMessageLink(newMessage);
    		return;
    	}
    	
    	// The message is valid and the subscriber wants it, we link it in now
    	
    	// Check whether there are BoardThreadLink or BoardReplyLink which reference the message by ID and give them the message object
    	updateExistingReferencesToNewMessage(newMessage);
    	
    	final String newMessageID = newMessage.getID();
    
		// If there was no ghost reference, we must store a BoardThreadLink if the new message is a thread 
		if(newMessage.isThread()) {
			try {
				getThreadLink(newMessageID);
			} catch(NoSuchMessageException e) {
	    		BoardThreadLink threadRef = new BoardThreadLink(this, newMessage, takeFreeMessageIndexWithoutCommit());
	    		threadRef.initializeTransient(mFreetalk);
	    		threadRef.storeWithoutCommit();
			}
		}
		else {
			final String threadID = newMessage.getThreadIDSafe();
			final String parentID = newMessage.getParentIDSafe();

    		// The new message is no thread. We must:
			
    		// 1. Find it's parent thread, create a ghost reference for it if it does not exist.
    		final BoardThreadLink parentThreadRef = findOrCreateParentThread(newMessage);
    		
    		// 2. If the parent message did not exist, create a ghost reply link for it.
    		// - The ghost link allows the UI to display a "Message is not downloaded yet" warning for the parent message
    		if(!parentID.equals(threadID)) {
    			findOrCreateParentMessage(newMessage);
    		}
    		
    		// 3. Store a BoardReplyLink for the new message
    		try {
    			getReplyLink(threadID, newMessageID);
    			// The reply link exists already, either because it was a ghost link or addMessage was called already for this message
    			// In either case it was already updated by updateExistingReferencesToNewMessage so we don't do anything here.
    		}
    		catch(NoSuchMessageException e) {
    			final BoardReplyLink messageRef = new BoardReplyLink(this, newMessage, takeFreeMessageIndexWithoutCommit());
    			messageRef.initializeTransient(mFreetalk);
    			messageRef.storeWithoutCommit();
    		}
    		
    		// 4. Tell the parent thread that a new message was added. This updates the last reply date and the "was read"-flag of the thread.
    		parentThreadRef.onMessageAdded(newMessage);
    		parentThreadRef.storeWithoutCommit();
    		
    	}

    	storeWithoutCommit();
    }

    
    @Override
    protected void deleteMessage(Message message) throws NoSuchMessageException {
    	deleteMessage(message, true);
    }
    
    /**
     * Called by the {@link MessageManager} before a {@link Message} object is deleted from the database.
     * This usually happens when an {@link Identity} is being deleted.
     * 
     * Does not delete the Message object itself, this is to be done by the callee.
     * 
     * TODO: Write a sophisticated unit test
     * 
     * 
     * @param message The message which is about to be deleted. It must still be stored within the database so that queries on it work.
     * @param deleteUnwantedLink True if an eventually existing UnwantedMessageLink for the message should also be delete, false if it should be kept.
     * @throws NoSuchMessageException If the message does not exist in this Board.
     */
    protected synchronized void deleteMessage(Message message, boolean deleteUnwantedLink) throws NoSuchMessageException {
    	Logger.normal(this, "deleteMessage " + message + "( deleteUnwantedLink==" + deleteUnwantedLink + ") for " + getSubscriber());
    	
    	boolean unwantedLinkDeleted = false;
    	
    	final String messageID = message.getID();
    	
    	// Maybe delete UnwantedMessageLink
    	
    	if(deleteUnwantedLink) {
    	try {
    		UnwantedMessageLink link = getUnwantedMessageLink(message);
    		link.deleteWithoutCommit();
    		unwantedLinkDeleted = true;
    	} catch(NoSuchMessageException e) { }
    	}

    	
    	// Maybe delete BoardThreadLink
    	
    	try {
    		// Check whether the message was listed as a thread.
    		BoardThreadLink threadLink = getThreadLink(messageID);
    		
    		try {
    			threadLink.getMessage();
    	    	
    			if(unwantedLinkDeleted) {
    	    		Logger.error(this, "Message was linked in even though it was marked as unwanted: " + message, new RuntimeException());
    	    	}
    		} catch(NoSuchMessageException e) { }

    		// If it was listed as a thread and had no replies, we can delete it's ThreadLink.
	    	if(threadReplyCount(messageID) == 0) {
	    		threadLink.deleteWithoutCommit();
	    	} else {
	    		// We do not delete the ThreadLink if it has replies already: We want the replies to stay visible and therefore the ThreadLink has to be kept,
	    		// so we mark it as a ghost thread.
	    		threadLink.removeThreadMessage();
	    		threadLink.storeWithoutCommit();
	    	}
    	}
    	catch(NoSuchMessageException e) { // getThreadReference failed
    		if(message.isThread()) {
				throw e;
    		}
    	}
    	
    	String threadID;
    	
    	try {
    		threadID = message.getThreadID();
    	} catch(NoSuchMessageException e) {
    		threadID = null;
    	}
    	
    	// Maybe delete BoardReplyLinks to the message

    	// NOTICE1: Even though we loop over all reply links to the message, getMessage() should only return the object on one of them: 
    	// If you reply to a message in a thread in which it did not exist, a BoardReplyLink is created there for allowing the UI to display
    	// "Message not fetched". However, as soon as the message IS fetched, it will only be added to the thread IF the thread is actually
    	// the thread in which the author wanted to post.
    	// All BoardReplyLinks in wrong threads will stay ghosts forever (i.e. getMessage() will fail)
    	
    	// NOITCE2: We loop instead of just doing getReplyLink(threadID, messageID) for getting bonus checks whether the database is consistent 
    	
    	for(final BoardReplyLink replyLink : getReplyLinks(message.getID())) {	
			final String replyLinkThreadID = replyLink.getThreadID();
			
			try {
				// Check whether the reply link has the Message object associated
				final Message replyLinkMessage = replyLink.getMessage();
				assert(replyLinkMessage == message);
				
				if(!replyLinkThreadID.equals(threadID)) {
					Logger.error(this, "Invalid BoardReplyLink found in database: Message was not null even though it is the wrong thread: " 
							+ replyLink);
				}
					
			} catch(NoSuchMessageException e) {
				continue; // Keep the ghost links intact
			}
				
			
			// Now we are in the right thread (thread ID matches) and the current replyLink has the message object we are looking for

			if(unwantedLinkDeleted) {
				Logger.error(this, "Message was linked in even though it was marked as unwanted: " + message, new RuntimeException());
			}
			
			// Remember:
			// - A ghost BoardReplyLink/BoardThreadLink is a message reference to a message which has not been downloaded 
			// - Ghosts are created when a reply references a thread or parent message which was not downloaded yet.
			// - Ghosts MAY ONLY EXIST if there is at least one really downloaded message referencing them - ghosts may not exist because
			//	 other ghosts reference them. We must ensure that now...
			
			// We must check whether there is a ghost BoardReplyLink for the parent of this reply and delete it maybe
			{
				try {
					final BoardReplyLink parentLink = getReplyLink(threadID, message.getParentID());
					try {
						parentLink.getMessage();
						// Parent is no ghost, keep it
					} catch(MessageNotFetchedException e) {
						// Parent IS a ghost, we must delete it, but only if it is not referenced by any other replies.
						boolean deleteParentLink = true;
						for(BoardReplyLink otherReply : getAllThreadReplies(threadID, false)) {
							try {
								// Only a non-ghost reply can cause a ghost reply to exist.
								// Therefore we do getMessage() to ensure that the reply is not a ghost.
								if(otherReply != replyLink && otherReply.getMessage().getParentID().equals(parentLink.getMessageID())) {
									deleteParentLink = false;
									break;
								}
							} catch(NoSuchMessageException e2) {}
						}
						
						if(deleteParentLink)
							parentLink.deleteWithoutCommit();
					}
				} catch(NoSuchMessageException e) {}
			}
				
			// Now we must check whether the reply link should be deleted or kept as ghost because it is the parent of other replies.
			{
				boolean deleteReplyLink = true;
				
				for(BoardReplyLink otherReply : getAllThreadReplies(threadID, false)) {
					try {
						// Only a non-ghost reply can cause a ghost reply to exist.
						// Therefore we do getMessage() to ensure that the reply is not a ghost.
						if(otherReply.getMessage().getParentID().equals(messageID)) {
							// (Messages cannot be their own parents so we don't have to check for otherReply != replyLink)
							deleteReplyLink = false;
							break;
						}
					} catch(NoSuchMessageException e) {} // TODO: This might be an error...
				}
				
				if(deleteReplyLink)
					replyLink.deleteWithoutCommit();
				else {
					replyLink.removeMessage();
					replyLink.storeWithoutCommit();
				}
			}
			
			// Now we must update the parent thread or delete it if has become a ghost 
			{
				BoardThreadLink threadLink = getThreadLink(replyLinkThreadID);
					
				try {
					threadLink.getMessage();
				}
				catch(MessageNotFetchedException e) {
					// If the thread itself is a ghost thread and it has no more replies, we must delete it:
					// It might happen that the caller first calls deleteMessage(thread) and then deleteMessage(all replies). The call to
					// deleteMessage(thread) did not delete the thread because it still had replies. Now it has no more replies and we
					// must delete it.
					if(threadReplyCount(replyLinkThreadID) == 0) {
						threadLink.deleteWithoutCommit();
						threadLink = null;
					}
				}
				
				if(threadLink != null) {
					threadLink.onMessageRemoved(message);
					threadLink.storeWithoutCommit();
				}
			}
    	}
    }
     
    public synchronized UnwantedMessageLink getUnwantedMessageLink(final Message message) throws NoSuchMessageException {
    	final Query q = mDB.query();
    	q.constrain(UnwantedMessageLink.class);
    	q.descend("mBoard").constrain(this).identity();
    	q.descend("mMessage").constrain(message).identity();
    	ObjectSet<UnwantedMessageLink> results = new Persistent.InitializingObjectSet<UnwantedMessageLink>(mFreetalk, q);
    	
    	switch(results.size()) {
    		case 0:
    			throw new NoSuchMessageException(message.getID());
    		case 1:
    			final UnwantedMessageLink link = results.next();
    			assert(message.equals(link.getMessage()));
    			return link;
    		default:
    			throw new DuplicateMessageException(message.getID());
    	}
    }
    
    public synchronized ObjectSet<BoardMessageLink> getMessageLinks(final String messageID) {
        final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessageID").constrain(messageID);
        return new Persistent.InitializingObjectSet<BoardMessageLink>(mFreetalk, q);
    }
    
	public synchronized ObjectSet<BoardReplyLink> getReplyLinks(final String messageID) {
        final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessageID").constrain(messageID);
        return new Persistent.InitializingObjectSet<SubscribedBoard.BoardReplyLink>(mFreetalk, q);
    }
   
	public synchronized BoardReplyLink getReplyLink(final String threadID, final String messageID) throws NoSuchMessageException {
        final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        q.descend("mMessageID").constrain(messageID);

        final ObjectSet<BoardReplyLink> results = new Persistent.InitializingObjectSet<SubscribedBoard.BoardReplyLink>(mFreetalk, q);
        
        switch(results.size()) {
	        case 1:
				final BoardReplyLink replyRef = results.next();
				assert(threadID.equals(replyRef.getThreadID())); // The query works
				assert(messageID.equals(replyRef.getMessageID())); // The query works
				return replyRef;
	        case 0:
	        	throw new NoSuchMessageException(messageID);
	        default:
	        	throw new DuplicateMessageException(messageID);
        }
    }
    
    
    @SuppressWarnings("unchecked")
	public synchronized BoardThreadLink getThreadLink(final String threadID) throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(BoardThreadLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        ObjectSet<BoardThreadLink> results = q.execute();
        
        switch(results.size()) {
	        case 1:
				final BoardThreadLink threadRef = results.next();
				threadRef.initializeTransient(mFreetalk);
				assert(threadID.equals(threadRef.getThreadID())); // The query works
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
     * @throws Exception If isMessageWanted() fails on the parent Message.
     */
    private synchronized BoardThreadLink findOrCreateParentThread(final Message newMessage) throws Exception {
    	String parentThreadID = newMessage.getThreadIDSafe();

    	try {
    		// The parent thread was downloaded and marked as a thread already, we return its BoardThreadLink
    		return getThreadLink(parentThreadID);
    	}
    	catch(NoSuchMessageException e) {
    		// There is no thread reference for the parent thread yet. Either it was not downloaded yet or it was downloaded but is no thread.
    		try {
    			final Message parentThread = mFreetalk.getMessageManager().get(parentThreadID);
    			
    			if(!getParentBoard().contains(parentThread)) {
    				// The parent thread is not a message in this board.
    				// TODO: Decide whether we should maybe store a flag in the BoardThreadLink which marks it.
    				// IMHO it is part of the UI's job to read the board list of the actual Message object and display something if the thread is not
    				// really a message to this board.
    			}

    			// The parent thread was downloaded and is no thread actually or/and does not reside in this board, we create a BoardThreadLink for it and
    			// therefore 'fork' a new thread off that message. The parent thread message will still be displayed as a reply to it's original thread (or as a 
    			// thread in its original board if it was from a different board), but it will also appear as a new thread which is the parent thread of
    			// the message which was passed to this function.

    			BoardThreadLink parentThreadRef;
    			
    	    	// Check whether the subscriber wants the message
    	    	if(isMessageWanted(parentThread)) {
    	    		// Maybe delete an obsolete UnwantedMessageLink if it exists
    	    		maybeDeleteUnwantedMessageLink(parentThread);
       			 	parentThreadRef = new BoardThreadLink(this, parentThread, takeFreeMessageIndexWithoutCommit());
    	    	} else {
    	    		deleteMessageAndStoreOrUpdateUnwantedMessageLink(parentThread);
    	    		// The thread is unwanted so we store a ghost reference for it.
    	    		parentThreadRef = new BoardThreadLink(this, parentThread.getID(), parentThread.getTitle(), parentThread.getDate(), takeFreeMessageIndexWithoutCommit());
    	    	}
    	    	
     			parentThreadRef.initializeTransient(mFreetalk);
     			parentThreadRef.storeWithoutCommit();    			
    			return parentThreadRef;
    		}
    		catch(NoSuchMessageException ex) { 
    			// The message manager did not find the parentThreadID, so the parent thread was not downloaded yet, we create a ghost thread reference for it.
    			BoardThreadLink ghostThreadRef = new BoardThreadLink(this, parentThreadID, newMessage.getTitle(), newMessage.getDate(), 
    					takeFreeMessageIndexWithoutCommit());
    			ghostThreadRef.initializeTransient(mFreetalk);
    			ghostThreadRef.storeWithoutCommit();
    			return ghostThreadRef;
    		}		
    	}
    }
    
    private BoardReplyLink findOrCreateParentMessage(Message newMessage) {
    	final String threadID = newMessage.getThreadIDSafe();
    	final String parentID = newMessage.getParentIDSafe();
    	
    	if(parentID.contains(threadID))
    		throw new RuntimeException("parentID equals threadID, you should use findOrCreateParentThread for this");
    	
		try {
			return getReplyLink(threadID, parentID);
		} catch(NoSuchMessageException e) {
			// We do not query the message manager whether a message with the parent ID exists and instead always create a ghost link
			// if no reply link was found for the parent message:
			// If it was downloaded already it should have been linked in to this thread anyway if it did belong there.
			// If the parent message actually is no reply to this thread we want the ghost link to stay ghost link forever
			// - forking parent messages into threads which do not belong there would make the thread displaying UI very complex...
			
			final Date parentDateGuess = new Date(newMessage.getDate().getTime() - 1);
			// TODO: Improve the title guess: If it doesnt start with "Re:" we should rather guess from the thread title ...
			// Subtract 1ms so the parent appears before the reply
			String parentTitleGuess = newMessage.getTitle();
			
			final BoardReplyLink ghostParentRef = new BoardReplyLink(this, 
					threadID, parentID, parentTitleGuess, parentDateGuess, takeFreeMessageIndexWithoutCommit());
			ghostParentRef.initializeTransient(mFreetalk);
			ghostParentRef.storeWithoutCommit();
			
			return ghostParentRef;
		}
    }


    /**
     * Get all threads in the board. The view is specified to the OwnIdentity who has subscribed to this board.
     * The transient fields of the returned messages will be initialized already.
     * @param identity The identity viewing the board.
     * @return An iterator of the message which the identity will see (based on its trust levels).
     */
    public synchronized ObjectSet<BoardThreadLink> getThreads() {
    	final Query q = mDB.query();
    	q.constrain(BoardThreadLink.class);
    	q.descend("mBoard").constrain(SubscribedBoard.this).identity(); // TODO: Benchmark whether switching the order of those two constrains makes it faster.
    	q.descend("mLastReplyDate").orderDescending();
    	return new Persistent.InitializingObjectSet<BoardThreadLink>(mFreetalk, q);
    }

    public synchronized ObjectSet<BoardMessageLink> getAllMessages(final boolean sortByMessageIndexAscending) {
    	final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        if (sortByMessageIndexAscending) {
            q.descend("mIndex").orderAscending(); /* Needed for NNTP */
        }
        return new Persistent.InitializingObjectSet<BoardMessageLink>(mFreetalk, q);
    }
    
    private synchronized ObjectSet<UnwantedMessageLink> getAllUnwantedMessages() {
    	final Query query = mDB.query();
    	query.constrain(UnwantedMessageLink.class);
    	query.descend("mBoard").constrain(this).identity();
    	return new Persistent.InitializingObjectSet<UnwantedMessageLink>(mFreetalk, query);
    }
    
    private synchronized ObjectSet<UnwantedMessageLink> getAllExpiredUnwantedMessages(final Date now) {
    	final Query query = mDB.query();
    	query.constrain(UnwantedMessageLink.class);
    	query.descend("mBoard").constrain(this).identity();
    	query.descend("mNextRetryDate").constrain(now).greater().not();
    	return new Persistent.InitializingObjectSet<UnwantedMessageLink>(mFreetalk, query);
    }
    
    private synchronized ObjectSet<BoardMessageLink> getAllExpiredWantedMessages(final Date now) {
    	final Query query = mDB.query();
    	query.constrain(BoardMessageLink.class);
    	query.descend("mBoard").constrain(this).identity();
    	query.descend("mNextWantedCheckDate").constrain(now).greater().not();
    	query.descend("mNextWantedCheckDate").constrain(null).identity().not();
    	return new Persistent.InitializingObjectSet<BoardMessageLink>(mFreetalk, query);    	
    }
    
    protected synchronized void retryAllUnwantedMessages(final Date now) {
    	Logger.normal(this, "Checking the wanted-state of unwanted messages ...");
    	
    	int count = 0;
    	
    	for(final UnwantedMessageLink link : getAllExpiredUnwantedMessages(now)) {
    		++count;
    		synchronized(Persistent.transactionLock(mDB)) {
    			try {
		    		if(link.retry() == false)
		    			link.storeWithoutCommit();
		    		else {
		    			final Message message = link.getMessage();
		    			Logger.normal(this, "Message state changed from unwanted to wanted, adding: " + message);
		    			addMessage(message);
		    		}
		    		Persistent.checkedCommit(mDB, this);
    			} catch(Exception e) {
    				Persistent.checkedRollback(mDB, this, e);
    			}
    		}
    	}
    	
    	Logger.normal(this, "Finished checking the wanted-state of " + count + " unwanted messages.");
    	
    	if(Logger.shouldLog(Logger.LogLevel.DEBUG, this)) {
    		final int remaining = getAllUnwantedMessages().size();
    		if(logDEBUG) Logger.debug(this, "Remaining unwanted count: " + remaining);
    	}
    }
    
    protected synchronized void validateAllWantedMessages(Date now) {
    	Logger.normal(this, "Checking the wanted-state of wanted messages ...");
    	
    	int count = 0;
    	
    	for(final BoardMessageLink ref : getAllExpiredWantedMessages(now)) {
    		if(ref.getNextWantedCheckDate() == null) {
    			Logger.warning(this, "Db4o bug: constrain(null).identity().not() did not work.");
    			continue;
    		}
    		
    		Message message;
    		
    		try {
    			message = ref.getMessage();
    		} catch(NoSuchMessageException e) {
    			Logger.error(this, "Wanted-check scheduled even though BoardMessageLink has no message: " + ref);
    			continue;
    		}
    		
    		++count;
    		synchronized(Persistent.transactionLock(mDB)) {
    			try {
    				if(ref.validateIfStillWanted(now)) {
    					ref.storeWithoutCommit();
    				} else {
    					Logger.normal(this, "Message state changed from wanted to unwanted, deleting: " + message);
    					deleteMessageAndStoreOrUpdateUnwantedMessageLink(message);
    				}
    				Persistent.checkedCommit(mDB, this);
    			} catch (Exception e) {
    				Persistent.checkedRollback(mDB, this, e);
    			}
    		}

    	}
    	
    	Logger.normal(this, "Finished checking the wanted-state of " + count +" wanted messages");
    }
    
	public synchronized int getFirstMessageIndex() throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mIndex").orderAscending();
        final ObjectSet<BoardMessageLink> result = new Persistent.InitializingObjectSet<SubscribedBoard.BoardMessageLink>(mFreetalk, q);
        
        if(result.size() == 0)
        	throw new NoSuchMessageException();
        
        return result.next().getIndex();
    }

	public synchronized int getLastMessageIndex() throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mIndex").orderDescending();
        final ObjectSet<BoardMessageLink> result = new Persistent.InitializingObjectSet<SubscribedBoard.BoardMessageLink>(mFreetalk, q);
        
        if(result.size() == 0)
        	throw new NoSuchMessageException();
        
        return result.next().getIndex();
    }
    
	public synchronized int getUnreadMessageCount() {
        final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mWasRead").constrain(false);
        
        return q.execute().size();
    }

    /**
     * Gets a reference to the message with the given index number.
     * 
     * Index numbers are local to each subscribed board. Attention: If a subscription to a board is removed and re-created, different index numbers might
     * be assigned to each message. This can be detected by a changed ID of the subscribed board.
     * 
     * @param index The index number of the demanded message.
     * @return A reference to the demanded message.
     * @throws NoSuchMessageException If there is no such message index.
     */
    @SuppressWarnings("unchecked")
    public synchronized BoardMessageLink getMessageByIndex(int index) throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mIndex").constrain(index);
        final ObjectSet<BoardMessageLink> result = q.execute();
        
        switch(result.size()) {
	        case 1:
	        	final BoardMessageLink ref = result.next();
	        	ref.initializeTransient(mFreetalk);
	        	return ref;
	        case 0:
	            throw new NoSuchMessageException();
	        default:
	        	throw new DuplicateMessageException("index " + Integer.toString(index));
        }
    }

    public synchronized ObjectSet<BoardMessageLink> getMessagesByMinimumIndex(
            int minimumIndex,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        if (minimumIndex > 0) {
            q.descend("mIndex").constrain(minimumIndex).smaller().not();
        }
        if (sortByMessageIndexAscending) {
            q.descend("mIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mDate").orderAscending();
        }
        return new Persistent.InitializingObjectSet<BoardMessageLink>(mFreetalk, q);
    }

    public synchronized ObjectSet<BoardMessageLink> getMessagesByMinimumDate(
    		Date minimumDate,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mDate").constrain(minimumDate).smaller().not();

        if (sortByMessageIndexAscending) {
            q.descend("mIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mDate").orderAscending();
        }
        return new Persistent.InitializingObjectSet<BoardMessageLink>(mFreetalk, q);
    }

    /**
     * Get the number of messages in this board.
     */
    public synchronized int messageCount() {
    	final Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        return q.execute().size();
    }

    /**
     * Get the number of replies to the given thread.
     */
    public synchronized int threadReplyCount(String threadID) {
    	final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        return q.execute().size();
    }
    
    /**
     * Get the number of unread replies to the given thread.
     * TODO: This should rather be cached in the BoardThreadLink
     */
    public synchronized int threadUnreadReplyCount(String threadID) {
    	final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        q.descend("mWasRead").constrain(false);
        
        return q.execute().size();
    }

    /**
     * Get all replies to the given thread, sorted ascending by date if requested
     */
    public synchronized ObjectSet<BoardReplyLink> getAllThreadReplies(final String threadID, final boolean sortByDateAscending) {
    	final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        
        if (sortByDateAscending) {
            q.descend("mDate").orderAscending();
        }
        
		return new Persistent.InitializingObjectSet<BoardReplyLink>(mFreetalk, q);
    }

    // @IndexedClass // I can't think of any query which would need to get all UnwantedMessageLink objects.
    public static final class UnwantedMessageLink extends Persistent {
    	
    	// TODO: Instead of periodic retrying, implement event subscription in the WoT plugin... 
    	
    	public static transient final long MINIMAL_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (5 * 60 * 1000) : (10 * 60 * 1000);
    	
    	public static transient final long MAXIMAL_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (10 * 60 * 6000) : (24 * 60 * 60 * 1000);
    	
    	public static transient final int MAXIMAL_RETRY_DELAY_AT_RETRY_COUNT = (int)(Math.log(MAXIMAL_RETRY_DELAY / MINIMAL_RETRY_DELAY) / Math.log(2));
    	
    	@IndexedField
    	protected final SubscribedBoard mBoard;
    	
    	@IndexedField
    	protected final Message mMessage;
    	
    	protected final Identity mAuthor;
    	
    	protected Date mLastRetryDate;
    	
    	@IndexedField
    	protected Date mNextRetryDate;
    	
    	protected int mNumberOfRetries;
    	
 
    	private UnwantedMessageLink(SubscribedBoard myBoard, Message myMessage) {
    		if(myBoard == null) throw new NullPointerException();
    		if(myMessage == null) throw new NullPointerException();
    		
    		mBoard = myBoard;
    		mMessage = myMessage;
    		mAuthor = mMessage.getAuthor();
    		
    		mNumberOfRetries = 0;
    		mLastRetryDate = CurrentTimeUTC.get();
    		
    		// When someone distrusts a spammer, a large amount of messages will be removed at once probably
    		// Therefore, we randomize their next-retry date to ensure that they are not retried all at once
    		Random random = mBoard.mFreetalk.getPluginRespirator() != null ? mBoard.mFreetalk.getPluginRespirator().getNode().random : new Random();
			mNextRetryDate = new Date(mLastRetryDate.getTime() + MINIMAL_RETRY_DELAY + Math.abs(random.nextLong() % (3*MINIMAL_RETRY_DELAY)));
    	}
    	
    	@Override
    	public void databaseIntegrityTest() throws Exception {
    		checkedActivate(1); // Date/String are db4o primitive types so 1 is enough
    		
    		IfNull.thenThrow(mBoard, "mBoard");
    		IfNull.thenThrow(mMessage, "mMessage");
    		IfNull.thenThrow(mAuthor, "mAuthor");
        	IfNull.thenThrow(mLastRetryDate, "mLastRetryDate");
        	IfNull.thenThrow(mNextRetryDate, "mNextRetryDate");
        	
        	final Date minNextRetry = new Date(mLastRetryDate.getTime() + UnwantedMessageLink.MINIMAL_RETRY_DELAY);
        	final Date maxNextRetry = new Date(mLastRetryDate.getTime() + UnwantedMessageLink.MAXIMAL_RETRY_DELAY);
        	
        	if(mNextRetryDate.before(minNextRetry))
        		throw new IllegalStateException("Invalid next retry date, too early: " + mNextRetryDate);
        	else if(mNextRetryDate.after(maxNextRetry))
        		throw new IllegalStateException("Invalid next retry date, too far in the future: " + mNextRetryDate);
        	
        	if(mNumberOfRetries < 0)
        		throw new IllegalStateException("mNumberOfRetries == " + mNumberOfRetries);
        	
        	for(BoardMessageLink ref : getBoard().getMessageLinks(getMessage().getID())) {
        		try {
        			ref.getMessage();
        			throw new IllegalStateException("Both UnwantedMessageLink and non-ghost MessageReference exist for " + getMessage());
        		} catch(NoSuchMessageException e) { } 
        		
        	}
        	
        	try {
        		getBoard().getParentBoard().getDownloadedMessageLink(getMessage());
        	} catch(NoSuchMessageException e) {
        		throw new IllegalStateException("Parent board does not have my message: " + getMessage());
        	}
    	}
    	
    	protected SubscribedBoard getBoard() {
    		checkedActivate(1);
    		mBoard.initializeTransient(mFreetalk);
    		return mBoard;
    	}
    	
    	protected Message getMessage() {
			checkedActivate(1);
			mMessage.initializeTransient(mFreetalk);
			return mMessage;
		}
    	
    	protected Identity getAuthor() {
    		checkedActivate(1);
    		if(mAuthor instanceof Persistent) {
    			Persistent author = (Persistent)mAuthor;
    			author.initializeTransient(mFreetalk);
    		}
    		return mAuthor;
    	}

		public boolean retry() {
			try {
				final boolean result = getBoard().isMessageWanted(getMessage());
				countRetry();
				return result;
			} catch(Exception e) {
				// isMessageWanted typically fails if we are not connected to the web of trust plugin so we only count the retry if it did not throw
				Logger.error(this, "retry() failed", e);
				return false;
			}
		}

		public void countRetry() {
			checkedActivate(1);
    		++mNumberOfRetries;
    		mLastRetryDate = CurrentTimeUTC.get();
    		mNextRetryDate = computeNextCheckDate();
		}

		private Date computeNextCheckDate() {
			checkedActivate(1); // Date is a db4o primitive type so 1 is enough
			if(mNumberOfRetries >=  MAXIMAL_RETRY_DELAY_AT_RETRY_COUNT)
				return new Date(mLastRetryDate.getTime() + MAXIMAL_RETRY_DELAY);
			
			// The Math.min() is a double check
			return new Date(mLastRetryDate.getTime() + Math.min(MINIMAL_RETRY_DELAY * (1<<mNumberOfRetries), MAXIMAL_RETRY_DELAY));
    	}

		@Override protected void storeWithoutCommit() {
    		super.storeWithoutCommit(1);
    	}

		@Override protected void deleteWithoutCommit() {
    		super.deleteWithoutCommit(1);
    	}

		@Override public String toString() {
			checkedActivate(1); // Date is a db4o primitive type so 1 is enough
    		return super.toString() + " with mBoard: (" + getBoard() + "); mMessage: (" + getMessage() + "); mAuthor: (" + getAuthor() +  "); mNumberOfRetries: " + mNumberOfRetries + 
    			"; mLastRetry: " + mLastRetryDate + "; mNextRetry: " + mNextRetryDate;
    	}
    }

    // @IndexedClass // I can't think of any query which would need to get all BoardMessageLink objects.
    public static abstract class BoardMessageLink extends Persistent {
    	
    	@IndexedField
    	protected final SubscribedBoard mBoard;
    	
    	protected final String mAuthorID;
    	
    	@IndexedField
    	protected final String mThreadID;
    	
    	@IndexedField
    	protected final String mMessageID;
    	
    	protected Message mMessage;
    	
    	protected String mTitle;
    	
    	@IndexedField
    	protected Date mDate;
    	
    	@IndexedField
    	protected final int mIndex;

    	private boolean mWasRead = false;

    	
    	// TODO: Instead of periodic retrying, implement event subscription in the WoT plugin...
    	
    	public static transient final long MINIMAL_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (5 * 60 * 1000) : (5 * 60 * 1000);
    	
    	public static transient final long MAXIMAL_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (10 * 60 * 6000) : (24 * 60 * 60 * 1000);
    	
    	public static transient final int MAXIMAL_RETRY_DELAY_AT_RETRY_COUNT = (int)(Math.log(MAXIMAL_RETRY_DELAY / MINIMAL_RETRY_DELAY) / Math.log(2));
    	
    	
    	protected int mNumberOfWantedChecks;
    	
    	protected Date mLastWantedCheckDate;
    	
    	@IndexedField
    	protected Date mNextWantedCheckDate;
    	

    	private BoardMessageLink(SubscribedBoard myBoard, String myThreadID, String myMessageID, String myMessageTitleGuess,
    			Date myMessageDateGuess, int myMessageIndex) {
        	if(myBoard == null) throw new NullPointerException();
        	if(myThreadID == null) throw new NullPointerException();
        	if(myMessageID == null) throw new NullPointerException();
        	if(myMessageDateGuess == null) throw new NullPointerException();
        	if(myMessageIndex < 0) throw new IllegalArgumentException();

    		mBoard = myBoard;
			mAuthorID = MessageID.construct(myMessageID).getAuthorID().toString(); // TODO: Change this function to eat a MessageID, not String
    		mThreadID = myThreadID;
    		mMessageID = myMessageID;
    		mMessage = null;
    		mTitle = myMessageTitleGuess;
    		mDate = myMessageDateGuess;
    		mIndex = myMessageIndex;
    		
    		try {
				assert(mIndex > mBoard.getLastMessageIndex());
			} catch (NoSuchMessageException e) {
			}
			
			mNumberOfWantedChecks = 0;
			mLastWantedCheckDate = null;
			mNextWantedCheckDate = null;
    	}

		public boolean validateIfStillWanted(Date now) {
			try {
				boolean result = getBoard().isMessageWanted(getMessage());
				countWantedCheck(now);
				// If the result is false we should not schedule a next wanted check - however we rely on the caller to unschedule it while
				// deleting the message - this is less error prune.
				return result;
			} catch(Exception e) {
				// isMessageWanted typically fails if we are not connected to the web of trust plugin so we only count the retry if it did not throw
				Logger.error(this, "validateIfStillWanted() failed", e);
				return true; // Do not delete existing messages just because we lost the connection to WoT
			}
		}
		
		private void countWantedCheck(Date now) {
			checkedActivate(1); // Date is a db4o primitive type so 1 is enough
			++mNumberOfWantedChecks;
			mLastWantedCheckDate = now;
			
			if(mNumberOfWantedChecks >= MAXIMAL_RETRY_DELAY_AT_RETRY_COUNT)
				mNextWantedCheckDate = new Date(mLastWantedCheckDate.getTime() + MAXIMAL_RETRY_DELAY);
			else {
				// The Math.min() is a double check
				mNextWantedCheckDate = new Date(mLastWantedCheckDate.getTime() + Math.min(MINIMAL_RETRY_DELAY * (1<<mNumberOfWantedChecks), MAXIMAL_RETRY_DELAY));
			}
		}
		
		protected Date getNextWantedCheckDate() {
			checkedActivate(1);
			return mNextWantedCheckDate;
		}
		

		private BoardMessageLink(SubscribedBoard myBoard, String myThreadID, Message myMessage, int myMessageIndex) {
			this(myBoard, myThreadID, myMessage.getID(), myMessage.getTitle(), myMessage.getDate(), myMessageIndex);

			// Done implicitely by .getID() above...
			// if(myMessage == null) throw new NullPointerException();
			
    		mMessage = myMessage; // We cannot use setMessage because initializeTransient was not called yet.
    		
    		mLastWantedCheckDate = CurrentTimeUTC.get();
    		
			// When a user creates a fresh Freetalk database, a huge bunch of messages will arrive in a relatively small time span
			// Therefore, we randomize the first wanted-check date to ensure that they will not be checked all at once
			Random random = mBoard.mFreetalk.getPluginRespirator() != null ? mBoard.mFreetalk.getPluginRespirator().getNode().random : new Random();
			mNextWantedCheckDate = new Date(mLastWantedCheckDate.getTime() + MINIMAL_RETRY_DELAY + Math.abs(random.nextLong() % (3*MINIMAL_RETRY_DELAY)));
    	}

		@Override public void databaseIntegrityTest() throws Exception {
			checkedActivate(1); // Date/String are db4o primitive types so 1 is enough
			
			IfNull.thenThrow(mBoard, "mBoard");
			IfNull.thenThrow(mAuthorID, "mAuthorID");
			IfNull.thenThrow(mThreadID, "mThreadID");
	    	IfNull.thenThrow(mMessageID, "mMessageID");
	    	IfNull.thenThrow(mTitle, "mTitle");
	    	IfNull.thenThrow(mDate, "mDate");
	    	
	    	if(mMessage != null) {
	    		final Message message = getMessage(); // Calls initializeTransient
	    		
	    		if(message instanceof OwnMessage)
	    			throw new IllegalStateException("mMessage == " + message);
	    		
	    		if(!getBoard().contains(message))
	    			throw new IllegalStateException("mBoard == " + getBoard() + " does not contain mMessage == " + message);
	    		
	           	try {
	        		getBoard().getParentBoard().getDownloadedMessageLink(message);
	        	} catch(NoSuchMessageException e) {
	        		throw new IllegalStateException("Parent board does not have my message: " + message);
	        	}
	    		
	    		IfNotEquals.thenThrow(mAuthorID, message.getAuthor().getID(), "mAuthorID");
	    		IfNotEquals.thenThrow(mMessageID, message.getID(), "mMessageID");
	    		
		    	try {
		    		try {
		    			IfNotEquals.thenThrow(mThreadID, message.getThreadID(), "mThreadID");
		    		} catch(IllegalStateException e) {
		    			// Replies can fork threads off existing messages, the thread ID of that message won't match then.
		    			if(!(this instanceof BoardThreadLink))
		    				throw e;
		    			else
		    				IfNotEquals.thenThrow(mThreadID, message.getID());
		    				
		    		}
		    	} catch(NoSuchMessageException e) {
		    		IfNotEquals.thenThrow(mThreadID, message.getID(), "mThreadID");
		    	}
	    		
	    		IfNotEquals.thenThrow(mTitle, message.getTitle(), "mTitle");
	    		IfNotEquals.thenThrow(mDate, message.getDate(), "mDate");
	    		
	        	IfNull.thenThrow(mLastWantedCheckDate, "mLastWantedCheckDate");
	        	IfNull.thenThrow(mNextWantedCheckDate, "mNextWantedCheckDate");
	        	
	        	final Date minNextRetry = new Date(mLastWantedCheckDate.getTime() + MINIMAL_RETRY_DELAY);
	        	final Date maxNextRetry = new Date(mLastWantedCheckDate.getTime() + MAXIMAL_RETRY_DELAY);
	        	
	        	if(mNextWantedCheckDate.before(minNextRetry))
	        		throw new IllegalStateException("Invalid next wanted-check date, too early: " + mNextWantedCheckDate);
	        	else if(mNextWantedCheckDate.after(maxNextRetry))
	        		throw new IllegalStateException("Invalid next wanted-check date, too far in the future: " + mNextWantedCheckDate);
	        	
	        	if(mNumberOfWantedChecks < 0)
	        		throw new IllegalStateException("mNumberOfWantedChecks == " + mNumberOfWantedChecks);
	    	} else {
	    		if(!Message.isTitleValid(mTitle))
	    			throw new IllegalStateException("Title guess is invalid: " + mTitle);
	    		
	    		IfNotEquals.thenThrow(mNumberOfWantedChecks, 0, "mNumberOfWantedChecks");
	    		
	    		if(mLastWantedCheckDate != null)
	    			throw new IllegalStateException("mLastWantedCheckDate==" + mLastWantedCheckDate);
	    		
	    		if(mNextWantedCheckDate != null)
	    			throw new IllegalStateException("mNextWantedCheckDate==" + mNextWantedCheckDate);
	    	}
	    	
	    	if(mIndex < 1)
	    		throw new IllegalStateException("mIndex == " + mIndex);
		}
		
		protected final SubscribedBoard getBoard() {
			checkedActivate(1);
			mBoard.initializeTransient(mFreetalk);
			return mBoard;
		}
		
        public final String getAuthorID() {
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
        	return mAuthorID;
        }
		
        public final String getThreadID() {
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
        	return mThreadID;
        }
        
        public final String getMessageID() {
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
        	return mMessageID;
        }
    	
        /**
         * Get the message to which this reference points.
         * @throws MessageNotFetchedException If the message belonging to this reference was not fetched yet.
         */
        public final Message getMessage() throws MessageNotFetchedException {
        	checkedActivate(1);
        	if(mMessage == null)
        		throw new MessageNotFetchedException(mMessageID);
        	
        	mMessage.initializeTransient(mFreetalk);
            return mMessage;
        }
        
		protected void setMessage(Message myMessage) {
			if(myMessage == null)
				throw new NullPointerException();
			
			checkedActivate(1); // Date/String are db4o primitive types so 1 is enough
			
			if(!mMessageID.equals(myMessage.getID()))
				throw new IllegalArgumentException("mMessageID==" + mMessageID + " but new message ID == " + myMessage.getID());
			
			if(mMessage != null) {
				if(mMessage != myMessage)
					throw new RuntimeException("setMessage() called but message is already set to different one: mMessage==" + mMessage + "; new message: " + myMessage);
				
				Logger.warning(this, "setMessage() called but message is already set.", new RuntimeException());
				return;
			}
			
			mMessage = myMessage;
			mMessage.initializeTransient(mFreetalk);
			mTitle = mMessage.getTitle();
			mDate = mMessage.getDate();
			
			markAsUnread();
			
			mLastWantedCheckDate = CurrentTimeUTC.get();
			
			// When a user creates a fresh Freetalk database, a huge bunch of messages will arrive in a relatively small time span
			// Therefore, we randomize the first wanted-check date to ensure that they will not be checked all at once
			Random random = mFreetalk.getPluginRespirator() != null ? mFreetalk.getPluginRespirator().getNode().random : new Random();
			mNextWantedCheckDate = new Date(mLastWantedCheckDate.getTime() + MINIMAL_RETRY_DELAY + Math.abs(random.nextLong() % (3*MINIMAL_RETRY_DELAY)));
		}
		
		protected void removeMessage() {
			checkedActivate(1);
			mMessage = null;
			
			mLastWantedCheckDate = null;
			mNextWantedCheckDate = null;
			mNumberOfWantedChecks = 0;
		}
		
		public final String getMessageTitle() {
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
			return mTitle;
		}
		
		protected final void setMessageTitle(String title) {
			if(Message.isTitleValid(title))
				throw new IllegalArgumentException("Title is not valid: " + title);
			
			checkedActivate(1);
			mTitle = title;
		}
        
        public final Date getMessageDate() {
			checkedActivate(1); // Date is a db4o primitive type so 1 is enough
        	return mDate;
        }
        
    	protected void setMessageDate(Date date) {
			checkedActivate(1); // Date is a db4o primitive type so 1 is enough
			
    		if(date.after(mDate))
    			throw new RuntimeException("Increasing the date guess does not make sense");
    		
    		mDate = date;
    	}
        
        /** Get an unique index number of this message in the board where which the query for the message was executed.
         * This index number is needed for NNTP and for synchronization with client-applications: They can check whether they have all messages by querying
         * for the highest available index number. */
        public final int getIndex() {
			checkedActivate(1); // int is a db4o primitive type so 1 is enough
        	return mIndex;
        }
        
		public final boolean wasRead() {
			checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
			return mWasRead;
		}
		
		protected final void markAsRead() {
			checkedActivate(1);
			mWasRead = true;
		}
		
		protected final void markAsUnread() {
			checkedActivate(1);
			mWasRead = false;
		}
        
        /**
         * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
         */
        @Override protected final void storeWithoutCommit() {
        	try {
        		checkedActivate(1);
        		throwIfNotStored(mBoard);
        		if(mMessage != null) throwIfNotStored(mMessage);

        		checkedStore();
        	}
        	catch(RuntimeException e) {
        		checkedRollbackAndThrow(e);
        	}
        }
        
        /**
         * Does not provide synchronization, you have to lock this Board before calling this function.
         */
        public final void storeAndCommit() {
        	synchronized(Persistent.transactionLock(mDB)) {
        		try {
	        		storeWithoutCommit();
	        		checkedCommit(this);
        		}
        		catch(RuntimeException e) {
        			checkedRollbackAndThrow(e);
        		}
        	}
        }
        
    	protected final void deleteWithoutCommit(ExtObjectContainer db) {
    		deleteWithoutCommit(1);
		}
    	
    	@Override
    	public String toString() {
    		Message message;
    		try {
    			message = getMessage();
    		} catch(MessageNotFetchedException e) {
    			message = null;
    		}
    		
    		return super.toString() + " with mBoard: (" + getBoard() + "); mAuthorID: " + getAuthorID() + "; mThreadID: " + getThreadID() + "; mMessageID: " + getMessageID() + 
    			"; subscriber: (" + getBoard().getSubscriber() + "); mMessage: (" + message + "); mTitle: " + getMessageTitle(); 
    	}
    }
    
    /**
     * Helper class to associate messages with boards in the database
     */
    // @Indexed // I can't think of any query which would need to get all BoardReplyLink objects.
    public static class BoardReplyLink extends BoardMessageLink { /* TODO: This is only public for configuring db4o. Find a better way */
    	
		/**
    	 * For constructing reply-links for messages which have been downloaded already.
    	 */
    	protected BoardReplyLink(SubscribedBoard myBoard, Message myMessage, int myMessageIndex) {
    		super(myBoard, myMessage.getThreadIDSafe(), myMessage, myMessageIndex);
    	}

        /**
         * For construction reply-links for messages which have not been downloaded - this is done when a other message states that
         * the given message is its parent message.
         * 
         * @param myBoard The board in which the message is supposed to exist.
         * @param myThreadID The ID of the thread in which the message is supposed to exist
         * @param myMessageID The ID of the hypothetical message.
         * @param myTitleGuess A guess for the title of the hypothetical message.
         * @param myDateGuess A guess for the date of the hypothetical message
         * @param myIndex The index which will be assigned to this reply link.
         */
		protected BoardReplyLink(SubscribedBoard myBoard, String myThreadID, String myMessageID, 
				String myTitleGuess, Date myDateGuess, int myIndex) {
    		super(myBoard, myThreadID, myMessageID, myTitleGuess, myDateGuess, myIndex);
    	}

    	@Override
    	public void databaseIntegrityTest() throws Exception {
    		super.databaseIntegrityTest();
    		
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
    		
    		try {
	    		if(getMessage().isThread())
	    			throw new IllegalStateException("mMessage is thread: " + getMessage());
    		} catch(NoSuchMessageException e) {}
    		
    		if(mMessage == null) {
    			boolean isValidGhost = false;
    			
    			for(BoardReplyLink reply : getBoard().getAllThreadReplies(mThreadID, false)) {
    				try {
						// A message is a valid ghost if a reply to it exists which has a Message object stored 
						// which makes getMessage NOT throw...
    					if(reply.getMessage().getParentID().equals(mMessageID)) {
	    					isValidGhost = true;
	    					break;
    					}
    				} catch(NoSuchMessageException e) {}
    			}
    			
    			if(!isValidGhost)
    				throw new IllegalStateException("BoardReplyLink has no message and no replies with a message");
    		}
    	}
    	
    }

    // @Indexed // I can't think of any query which would need to get all BoardThreadLink objects.
    public final static class BoardThreadLink  extends BoardMessageLink {
        
    	private Date mLastReplyDate;
    	
    	private boolean mWasThreadRead = false;


    	protected BoardThreadLink(SubscribedBoard myBoard, Message myThread, int myMessageIndex) {
    		super(myBoard, myThread.getID(), myThread, myMessageIndex);
    		
    		// Done implicitely by .getID() above
    		// if(myThread == null) throw new NullPointerException();
    		
    		mLastReplyDate = myThread.getDate();
    	}

		/**
    	 * @param myLastReplyDate The date of the last reply to this thread. This parameter must be specified at creation to prevent threads 
    	 * 		from being hidden if the user of this constructor forgot to call updateLastReplyDate() - thread display is sorted descending 
    	 * 		by reply date!
    	 */
    	protected BoardThreadLink(SubscribedBoard myBoard, String myThreadID, String myMessageTitle,
    			Date myLastReplyDate, int myMessageIndex) {
    		super(myBoard, myThreadID, myThreadID, myMessageTitle, myLastReplyDate, myMessageIndex);
    		
    		// TODO: We might validate the thread id here. Should be safe not to do so because it is taken from class Message which validates it.
    		
    		mLastReplyDate = myLastReplyDate;
    	}
    	
    	@Override
    	public void databaseIntegrityTest() throws Exception {
    		super.databaseIntegrityTest();
    		
    		checkedActivate(1);
    		
    		IfNotEquals.thenThrow(mMessageID, mThreadID, "mMessageID");
    		
    		// We do not check for getMessage().isThread() because non-thread messages can become threads if someone replies to them as thread.
    		
     		boolean hasActuallyFetchedReplies = false;
    		boolean threadWasRead = wasRead();
    		
    		for(final BoardReplyLink reply : getBoard().getAllThreadReplies(getThreadID(), true)) {
    			IfNotEquals.thenThrow(reply.getThreadID(), mThreadID, "reply.getThreadID()");
    			
				if(!reply.wasRead())
					threadWasRead = false;
				
				try {
					reply.getMessage();
    				hasActuallyFetchedReplies = true;
				} catch(NoSuchMessageException e) { }
    		}
    		
    		if(wasThreadRead() != threadWasRead)
    			throw new IllegalStateException("wasThreadRead()==" + wasThreadRead() + " is wrong");
    		
    		if(mMessage == null && !hasActuallyFetchedReplies)
    			throw new IllegalStateException("BoardThreadLink has no message and no replies");
    	}
    	
    	private void recomputeThreadReadState() {
			boolean wasThreadRead = wasRead();

			if(wasThreadRead) {
				for(BoardReplyLink reply : getBoard().getAllThreadReplies(getThreadID(), false)) {
					if(!reply.wasRead()) {
						wasThreadRead = false;
						break;
					}
				}
			}

			if(wasThreadRead)
				markThreadAsRead();
			else
				markThreadAsUnread();
    	}
    	
    	protected void onMessageAdded(Message newMessage) {
    		// TODO: Optimization: Normally, it would be okay to just mark the thread as unread. However, in some cases onMessageAdded is called
    		// for certain messages twice, maybe due to double Board.addMessage(), therefore we recompute until I have the time to fix this.
    		// markThreadAsUnread();
    		recomputeThreadReadState();
    		
    		final Date newDate = newMessage.getDate();
    		
			checkedActivate(1); // Date is a db4o primitive type so 1 is enough
			
    		if(mMessage == null) {
    			// The thread has not been downloaded, we adjust its date to be the date of the first reply
    			if(newDate.before(mDate))
    				setMessageDate(newDate);
    		}
    		
			if(newDate.after(mLastReplyDate))
				mLastReplyDate = newDate;
			
			// TODO: If the thread message was not downloaded, set the title guess to the most-seen title of all replies...
		}
    	
    	protected void onMessageRemoved(Message removedMessage) {
    		// TODO: This assumes that getAllThreadReplies() obtains the sorted order using an index. This is not the case right now. If we do not
    		// optimize getAllThreadReplies() we should just iterate over the unsorted replies list and do maximum search.

    		// TODO: Put this in a function "computeLastReplyDate"....
    		
			checkedActivate(1); // String/Date are db4o primitive types so 1 is enough

    		final ObjectSet<BoardReplyLink> replies = getBoard().getAllThreadReplies(mThreadID, true);

    		if(!removedMessage.getDate().before(mLastReplyDate)) {
    			final int repliesCount = replies.size();
    			if(repliesCount>0)
    				mLastReplyDate = replies.get(repliesCount-1).getMessageDate();
    			else
    				mLastReplyDate = mDate;
    		}

    		// If the last unread message in the thread was removed, the thread IS read now...
    		if(wasRead() && !wasThreadRead()) {
    			boolean wasThreadRead = true;

    			for(BoardReplyLink reply : replies) {
    				if(!reply.wasRead()) {
    					wasThreadRead = false;
    					break;
    				}
    			}

    			if(wasThreadRead)
    				markThreadAsRead();
    		}

    		
			// TODO: If the thread message was not downloaded, set the title guess to the most-seen title of all replies...
    	}
    	
    	
    	public void removeThreadMessage() {
    		super.removeMessage();
    		
    		// TODO: I cannot remember why I made the code delete the date. It should be kept instead of using guesses...
    		// If re-enabling the deletion, the following code should also be modified to delete the title and guess it instead...
    		
//    		mDate = null;
//    		
//    		// TODO: This assumes that getAllThreadReplies() obtains the sorted order using an index. This is not the case right now. If we do not
//    		// optimize getAllThreadReplies() we should just iterate over the unsorted replies list and do minimum search.
//    		for(BoardReplyLink reply : mBoard.getAllThreadReplies(mThreadID, true)) {
//    			mLastReplyDate = reply.getMessageDate();
//    			return;
//    		}
		}
		
		public Date getLastReplyDate() {
			checkedActivate(1); // Date is a db4o primitive type so 1 is enough
			return mLastReplyDate;
		}

		@Override public void setMessage(Message myThread) {
			if(myThread.getID().equals(getThreadID()) == false)
				throw new IllegalArgumentException();
			
			super.setMessage(myThread);

			onMessageAdded(myThread); // This also marks the whole thread as unread.
		}
		
		/**
		 * Gets the "thread was read flag". This is false if the thread contains a single unread message.
		 */
		public boolean wasThreadRead() {
			checkedActivate(1);
			return mWasThreadRead;
		}
		
		private void markThreadAsRead() {
			checkedActivate(1);
			mWasThreadRead = true;
		}
		
		private void markThreadAsUnread() {
			checkedActivate(1);
			mWasThreadRead = false;
		}
		
		private void changeThreadAndRepliesReadStateAndCommit(boolean newReadState) {
			checkedActivate(1);
			
			synchronized(mBoard) {
			synchronized(Persistent.transactionLock(mDB)) {
				try {
					// Mark this object as unread
					if(newReadState) {
						markAsRead();
						markThreadAsRead();
					} else {
						markAsUnread();
						markThreadAsUnread();
					}
					
					mWasThreadRead = newReadState;
					storeWithoutCommit();

					// Mark its replies as unread
					for(BoardReplyLink reference : getBoard().getAllThreadReplies(mThreadID, false)) {
						if(reference.wasRead() != newReadState) {
							// TODO: Encapsulate in BoardMessageLink
							if(newReadState)
								reference.markAsRead();
							else 
								reference.markAsUnread();
							reference.storeWithoutCommit();
						}
					}

					checkedCommit(this);
				}
				catch(RuntimeException e) {
					checkedRollbackAndThrow(e);
				}
			}
			}
		}
		
		public void markThreadAndRepliesAsUnreadAndCommit() {
			changeThreadAndRepliesReadStateAndCommit(false);
		}
		
		public void markThreadAndRepliesAsReadAndCommit() {
			changeThreadAndRepliesReadStateAndCommit(true);
		}
    }
    
    @Override
    public String toString() {
    	return super.toString() + "; mSubscriber: (" + getSubscriber() + "); mParentBoard: (" + getParentBoard() + ")"; 
    }

}
