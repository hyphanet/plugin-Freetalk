package plugins.Freetalk;

import java.util.Date;

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

/**
 * A SubscribedBoard is a {@link Board} which only stores messages which the subscriber (a {@link OwnIdentity}) wants to read,
 * according to the implementation of {@link OwnIdentity.wantsMessagesFrom}.
 */
@IndexedClass
public final class SubscribedBoard extends Board {

	private final OwnIdentity mSubscriber;
	
	private Board mParentBoard;
	
	/**
	 * The description which the subscriber has specified for this Board. Null if he has not specified any.
	 */
	private String mDescription = null;
	
	/**
	 * Index of the latest message which this board has pulled from it's parent board. 
	 */
	private int	mHighestSynchronizedParentMessageIndex = 0;

	
	public SubscribedBoard(Board myParentBoard, OwnIdentity mySubscriber) throws InvalidParameterException {
		super(myParentBoard.getName());
		
		// .getName() does this for us.
		// if(myParentBoard == null) throw new NullPointerException();
		
		if(mySubscriber == null) throw new NullPointerException();
		
		mParentBoard = myParentBoard;
		mSubscriber = mySubscriber;
	}
    
    protected void storeWithoutCommit() {
    	throwIfNotStored(mSubscriber);
    	throwIfNotStored(mParentBoard);
    	super.storeWithoutCommit();
    }
	
	protected void deleteWithoutCommit() {
		// TODO: When deleting a subscribed board, check whether the objects of class Message are being used by a subscribed board of another own identity.
		// If not, delete the messages.
		try {
			checkedActivate(3); // TODO: Figure out a suitable depth.
			
			for(UnwantedMessageLink link : getAllUnwantedMessages()) {
				link.deleteWithoutCommit();
			}
			
			for(MessageReference ref : getAllMessages(false)) {
				ref.deleteWithoutCommit();
			}

			checkedDelete();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}

	}
	
	public OwnIdentity getSubscriber() {
    	if(mSubscriber instanceof Persistent) {
    		final Persistent subscriber = (Persistent)mSubscriber;
    		subscriber.initializeTransient(mFreetalk);
    	}
    	return mSubscriber;
	}
	
	public Board getParentBoard() {
		mParentBoard.initializeTransient(mFreetalk);
		return mParentBoard;
	}

    public synchronized String getDescription() {
        return mDescription != null ? mDescription : super.getDescription(getSubscriber());
    }
    
    /**
     * Gets the reference to the latest message. Does not return ghost thread references - therefore, the returned MessageReference will always
     * point to a valid Message object.
     * 
     * TODO: Make this function return class Message and not class MessageReference because it won't return MessageReference objects whose Message
     * is not downloaded yet anyway.
     * 
     * @throws NoSuchMessageException If the board is empty.
     */
	public synchronized MessageReference getLatestMessage() throws NoSuchMessageException {
    	// TODO: We can probably cache the latest message date in this SubscribedBoard object.
    	
        final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this);
        q.descend("mDate").orderDescending();
      
        // Do not use a constrain() because the case where the latest message has no message object should not happen very often.
        for(MessageReference ref : new Persistent.InitializingObjectSet<MessageReference>(mFreetalk, q)) {
        	try {
        		ref.getMessage(); // Check whether the message was downloaded
        		return ref;
        	}
        	catch(MessageNotFetchedException e)  {
        		// Continue to next MessageReference
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
    	for(Board.BoardMessageLink messageLink : getParentBoard().getMessagesAfterIndex(mHighestSynchronizedParentMessageIndex)) {
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
     * Stores an {@link UnwantedMessageLink} for the given message.
     * If there is one already, its retry count is incremented (which effectively increases the delay until the next retry will be done).
     */
    private final void storeOrUpdateUnwantedMessageLink(Message newMessage) {
		Logger.normal(this, "Ignoring message from " + newMessage.getAuthor().getNickname() + " because " +
				getSubscriber().getNickname() + " does not want his messages.");
		
		try {
			UnwantedMessageLink link = getUnwantedMessageLink(newMessage);
			link.countRetry();
			link.storeWithoutCommit();
		} catch(NoSuchMessageException e) {
			UnwantedMessageLink link = new UnwantedMessageLink(this, newMessage);
			link.initializeTransient(mFreetalk);
			link.storeWithoutCommit();
		}
    }
    
    /**
     * Checks whether there is an {@link UnwantedMessageLink} stored for the given message.
     * Deletes it if yes, if not, does nothing.
     */
    private final void maybeDeleteUnwantedMessageLink(Message newMessage) {
		try {
			UnwantedMessageLink link = getUnwantedMessageLink(newMessage);
			link.deleteWithoutCommit();
		} catch(NoSuchMessageException e) {}
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
    protected synchronized final void addMessage(Message newMessage) throws Exception {
    	Logger.debug(this, "addMessage " + newMessage);
    	
    	// Sanity checks
    	throwIfNotAllowedInThisBoard(newMessage);
    	
    	// Check whether the subscriber wants the message
    	if(isMessageWanted(newMessage)) {
    		// Maybe delete an obsolete UnwantedMessageLink if it exists
    		maybeDeleteUnwantedMessageLink(newMessage);
    	} else {
    		storeOrUpdateUnwantedMessageLink(newMessage);
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
    		BoardThreadLink parentThreadRef = findOrCreateParentThread(newMessage);
    		
    		// 2. Tell the parent thread that a new message was added. This updates the last reply date and the "was read"-flag of the thread.
    		parentThreadRef.onMessageAdded(newMessage);
    		parentThreadRef.storeWithoutCommit();
    		
    		// 3. If the parent message did not exist, create a ghost reply link for it.
    		// - The ghost link allows the UI to display a "Message is not downloaded yet" warning for the parent message
    		if(!parentID.equals(threadID)) {
    			findOrCreateParentMessage(newMessage);
    		}
    		
    		// 4. Store a BoardReplyLink for the new message
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
    	}

    	storeWithoutCommit();
    }

    
    /**
     * Called by the {@link MessageManager} before a {@link Message} object is deleted from the database.
     * This usually happens when an {@link Identity} is being deleted.
     * 
     * Does not delete the Message object itself, this is to be done by the callee.
     * 
     * TODO: Write a sophisticated unit test
     * 
     * @param message The message which is about to be deleted. It must still be stored within the database so that queries on it work.
     * @throws NoSuchMessageException If the message does not exist in this Board.
     */
    protected synchronized void deleteMessage(Message message) throws NoSuchMessageException {
    	boolean unwantedLinkDeleted = false;
    	
    	final String messageID = message.getID();
    	
    	// Maybe delete UnwantedMessageLink
    	
    	try {
    		UnwantedMessageLink link = getUnwantedMessageLink(message);
    		link.deleteWithoutCommit();
    		unwantedLinkDeleted = true;
    	} catch(NoSuchMessageException e) { }

    	
    	// Maybe delete BoardThreadLink
    	
    	try {
    		// Check whether the message was listed as a thread.
    		BoardThreadLink threadLink = getThreadLink(messageID);

    		// If it was listed as a thread and had no replies, we can delete it's ThreadLink.
	    	if(threadReplyCount(messageID) == 0) {
	    		threadLink.deleteWithoutCommit();
	    	} else {
	    		// We do not delete the ThreadLink if it has replies already: We want the replies to stay visible and therefore the ThreadLink has to be kept,
	    		// so we mark it as a ghost thread.
	    		threadLink.removeThreadMessage();
	    	}
	    	
	    	if(unwantedLinkDeleted) {
	    		Logger.error(this, "Message was linked in even though it was marked as unwanted: " + message);
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
					continue; // Keep the ghost links intact
				}
					
			} catch(NoSuchMessageException e) {
				continue; // Keep the ghost links intact
			}
				
			
			// Now we are in the right thread (thread ID matches) and the current replyLink has the message object we are looking for

			if(unwantedLinkDeleted) {
				Logger.error(this, "Message was linked in even though it was marked as unwanted: " + message);
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
								if(!otherReply.equals(replyLink) && otherReply.getMessage().getParentID().equals(parentLink.getMessageID())) {
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
							// (Messages cannot be their own parents so we don't have to check for otherReply.equals(replyLink))
							deleteReplyLink = false;
							break;
						}
					} catch(NoSuchMessageException e) {} // TODO: This might be an error...
				}
				
				if(deleteReplyLink)
					replyLink.deleteWithoutCommit();
				else
					replyLink.removeMessage();
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
				
				if(threadLink != null)
					threadLink.onMessageRemoved(message);
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
    			assert(message.equals(link.mMessage));
    			return link;
    		default:
    			throw new DuplicateMessageException(message.getID());
    	}
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
				assert(threadID.equals(replyRef.mThreadID)); // The query works
				assert(messageID.equals(replyRef.mMessageID)); // The query works
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
				assert(threadID.equals(threadRef.mThreadID)); // The query works
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
    private synchronized BoardThreadLink findOrCreateParentThread(final Message newMessage) {
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

    			BoardThreadLink parentThreadRef = new BoardThreadLink(this, parentThread, takeFreeMessageIndexWithoutCommit());
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

    public synchronized ObjectSet<MessageReference> getAllMessages(final boolean sortByMessageIndexAscending) {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (sortByMessageIndexAscending) {
            q.descend("mIndex").orderAscending(); /* Needed for NNTP */
        }
        return new Persistent.InitializingObjectSet<MessageReference>(mFreetalk, q);
    }
    
    private synchronized ObjectSet<UnwantedMessageLink> getAllUnwantedMessages() {
    	final Query query = mDB.query();
    	query.constrain(UnwantedMessageLink.class);
    	query.descend("mBoard").constrain(this).identity();
    	return new Persistent.InitializingObjectSet<UnwantedMessageLink>(mFreetalk, query);
    }
    
    private synchronized ObjectSet<UnwantedMessageLink> getAllExpiredUnwantedMessages(Date now) {
    	final Query query = mDB.query();
    	query.constrain(UnwantedMessageLink.class);
    	query.descend("mBoard").constrain(this).identity();
    	query.descend("mNextRetryDate").constrain(now).greater().not();
    	return new Persistent.InitializingObjectSet<UnwantedMessageLink>(mFreetalk, query);
    }
    
    protected synchronized void retryAllUnwantedMessages(Date now) {
    	for(UnwantedMessageLink link : getAllExpiredUnwantedMessages(now)) {
    		if(link.retry() == false)
    			link.storeWithoutCommit();
    		else {
    			try {
    				final Message message = link.getMessage();
    				Logger.debug(this, "Message state changed from unwanted to wanted, adding: " + message);
    				addMessage(message);
    			} catch(Exception e) {
    				Logger.error(this, "Adding message failed", e);
    			}
    		}
    	}
    	
    	if(Logger.shouldLog(Logger.LogLevel.DEBUG, this)) {
    		final int remaining = getAllUnwantedMessages().size();
    		if(remaining > 0)
    			Logger.debug(this, "Remaining unwanted count: " + remaining);
    	}
    }
    
    @SuppressWarnings("unchecked")
	public synchronized int getFirstMessageIndex() throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mIndex").orderAscending();
        ObjectSet<MessageReference> result = q.execute();
        
        if(result.size() == 0)
        	throw new NoSuchMessageException();
        
        return result.next().getIndex();
    }

    @SuppressWarnings("unchecked")
	public synchronized int getLastMessageIndex() throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mIndex").orderDescending();
        ObjectSet<MessageReference> result = q.execute();
        
        if(result.size() == 0)
        	throw new NoSuchMessageException();
        
        return result.next().getIndex();
    }
    
	public synchronized int getUnreadMessageCount() {
        final Query q = mDB.query();
        q.constrain(MessageReference.class);
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
    public synchronized MessageReference getMessageByIndex(int index) throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mIndex").constrain(index);
        final ObjectSet<MessageReference> result = q.execute();
        
        switch(result.size()) {
	        case 1:
	        	final MessageReference ref = result.next();
	        	ref.initializeTransient(mFreetalk);
	        	return ref;
	        case 0:
	            throw new NoSuchMessageException();
	        default:
	        	throw new DuplicateMessageException("index " + Integer.toString(index));
        }
    }

    public synchronized ObjectSet<MessageReference> getMessagesByMinimumIndex(
            int minimumIndex,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = mDB.query();
        q.constrain(MessageReference.class);
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
        return new Persistent.InitializingObjectSet<MessageReference>(mFreetalk, q);
    }

    public synchronized ObjectSet<MessageReference> getMessagesByMinimumDate(
    		Date minimumDate,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mDate").constrain(minimumDate).smaller().not();

        if (sortByMessageIndexAscending) {
            q.descend("mIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mDate").orderAscending();
        }
        return new Persistent.InitializingObjectSet<MessageReference>(mFreetalk, q);
    }

    /**
     * Get the number of messages in this board.
     */
    public synchronized int messageCount() {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
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
    	
    	public static final long MINIMAL_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (5 * 60 * 1000) : (5 * 60 * 1000);
    	
    	public static final long MAXIMAL_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (10 * 60 * 6000) : (24 * 60 * 60 * 1000);
    	
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
    		mLastRetryDate = CurrentTimeUTC.get();
    		mNumberOfRetries = 1;
    		mNextRetryDate = computeNextCheckDate();
    	}
    	
    	protected SubscribedBoard getBoard() {
    		checkedActivate(2);
    		mBoard.initializeTransient(mFreetalk);
    		return mBoard;
    	}
    	
    	protected Message getMessage() {
			checkedActivate(2);
			mMessage.initializeTransient(mFreetalk);
			return mMessage;
		}
    	
    	protected Identity getAuthor() {
    		checkedActivate(2);
    		if(mAuthor instanceof Persistent) {
    			Persistent author = (Persistent)mAuthor;
    			author.initializeTransient(mFreetalk);
    		}
    		return mAuthor;
    	}

		public boolean retry() {
			try {
				boolean result = getBoard().getSubscriber().wantsMessagesFrom(getAuthor());
				countRetry(); // wantsMessagesFrom typically fails if we are not connected to the web of trust plugin so we only count the retry if it did not throw
				return result;
			} catch(Exception e) {
				Logger.error(this, "Retry failed");
				return false;
			}
		}

		public void countRetry() {
    		++mNumberOfRetries;
    		mNextRetryDate = computeNextCheckDate();
		}

		private Date computeNextCheckDate() {
			return new Date(mLastRetryDate.getTime() + Math.min(MINIMAL_RETRY_DELAY * (1<<mNumberOfRetries), MAXIMAL_RETRY_DELAY));
    	}
    	
    	protected void storeWithoutCommit() {
    		super.storeWithoutCommit(2);
    	}
    	
    	protected void deleteWithoutCommit() {
    		super.deleteWithoutCommit(2);
    	}
    	
    	public String toString() {
    		return "author: " + mAuthor.getNickname() + "; messageID: " + mMessage.getID() + "; numberOfRetries: " + mNumberOfRetries + 
    			"; lastRetry: " + mLastRetryDate + "; nextRetry: " + mNextRetryDate;
    	}
    }

    // @IndexedClass // I can't think of any query which would need to get all MessageReference objects.
    public static abstract class MessageReference extends Persistent {
    	
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


    	private MessageReference(SubscribedBoard myBoard, String myThreadID, String myMessageID, String myMessageTitleGuess,
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
    	}

		private MessageReference(SubscribedBoard myBoard, String myThreadID, Message myMessage, int myMessageIndex) {
			this(myBoard, myThreadID, myMessage.getID(), myMessage.getTitle(), myMessage.getDate(), myMessageIndex);

			// Done implicitely by .getID() above...
			// if(myMessage == null) throw new NullPointerException();
			
    		mMessage = myMessage;
    	}
		
        public final String getAuthorID() {
        	return mAuthorID;
        }
		
        public final String getThreadID() {
        	return mThreadID;
        }
        
        public final String getMessageID() {
        	return mMessageID;
        }
    	
        /**
         * Get the message to which this reference points.
         * @throws MessageNotFetchedException If the message belonging to this reference was not fetched yet.
         */
        public final Message getMessage() throws MessageNotFetchedException {
        	checkedActivate(2);
        	if(mMessage == null)
        		throw new MessageNotFetchedException(mMessageID);
        	
        	mMessage.initializeTransient(mFreetalk);
            return mMessage;
        }
        
		protected void setMessage(Message myMessage) {
			if(myMessage == null)
				throw new NullPointerException();
			
			mMessage = myMessage;
			mTitle = mMessage.getTitle();
			mDate = mMessage.getDate();
			
			markAsUnread();
		}
		
		protected void removeMessage() {
			mMessage = null;
		}
		
		public final String getMessageTitle() {
			return mTitle;
		}
		
		protected final void setMessageTitle(String title) {
			if(Message.isTitleValid(title))
				throw new IllegalArgumentException("Title is not valid: " + title);
			
			mTitle = title;
		}
        
        public final Date getMessageDate() {
        	return mDate;
        }
        
    	protected void setMessageDate(Date date) {
    		if(date.after(mDate))
    			throw new RuntimeException("Increasing the date guess does not make sense");
    		
    		mDate = date;
    	}
        
        /** Get an unique index number of this message in the board where which the query for the message was executed.
         * This index number is needed for NNTP and for synchronization with client-applications: They can check whether they have all messages by querying
         * for the highest available index number. */
        public final int getIndex() {
        	return mIndex;
        }
        
		public final boolean wasRead() {
			return mWasRead;
		}
		
		protected final void markAsRead() {
			mWasRead = true;
		}
		
		protected final void markAsUnread() { 
			mWasRead = false;
		}
        
        /**
         * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
         */
        protected final void storeWithoutCommit(ExtObjectContainer db) {
        	try {
        		checkedActivate(3); // TODO: Figure out a suitable depth.
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
        	synchronized(mDB.lock()) {
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
    		deleteWithoutCommit(3); // TODO: Figure out a suitable depth.
		}
    }
    
    /**
     * Helper class to associate messages with boards in the database
     */
    // @Indexed // I can't think of any query which would need to get all BoardReplyLink objects.
    public static class BoardReplyLink extends MessageReference { /* TODO: This is only public for configuring db4o. Find a better way */
    	
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

    }

    // @Indexed // I can't think of any query which would need to get all BoardThreadLink objects.
    public final static class BoardThreadLink  extends MessageReference {
        
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
    	
    	protected void onMessageAdded(Message newMessage) {
    		markThreadAsUnread();
    		
    		final Date newDate = newMessage.getDate();
    		
    		checkedActivate(2);
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

    		final ObjectSet<BoardReplyLink> replies = mBoard.getAllThreadReplies(mThreadID, true);

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
    		mMessage = null;
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
			return mLastReplyDate;
		}
		
		public void setMessage(Message myThread) {
			if(myThread.getID().equals(mThreadID) == false)
				throw new IllegalArgumentException();
			
			super.setMessage(myThread);

			onMessageAdded(myThread); // This also marks the whole thread as unread.
		}
		
		/**
		 * Gets the "thread was read flag". This is false if the thread contains a single unread message.
		 */
		public boolean wasThreadRead() {
			return mWasThreadRead;
		}
		
		private void markThreadAsRead() {
			mWasThreadRead = true;
		}
		
		private void markThreadAsUnread() {
			mWasThreadRead = false;
		}
		
		private void changeThreadAndRepliesReadStateAndCommit(boolean newReadState) {
			synchronized(mBoard) {
			synchronized(mDB.lock()) {
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
					for(BoardReplyLink reference : mBoard.getAllThreadReplies(mThreadID, false)) {
						if(reference.wasRead() != newReadState) {
							// TODO: Encapsulate in MessageReference
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

    protected synchronized void verifyDatabaseIntegrity() {
    	if(mSubscriber == null)
    		Logger.error(this, "SubscribedBoard has mSubscriber==null: " + this);
    	
    	if(mParentBoard == null)
    		Logger.error(this, "SubscribedBoard has mParentBoard==null: " + this);
    	
    	for(final BoardThreadLink thread : getThreads()) {
    		boolean hasActuallyFetchedReplies = false;
    		boolean threadWasRead = thread.wasRead();
    		
    		for(final BoardReplyLink reply : getAllThreadReplies(thread.getThreadID(), true)) {
				if(!reply.mThreadID.equals(thread.mThreadID))
					Logger.error(this, "BoardReplyLink has wrong thread ID: " + reply);
    			
    			try {
    				final Message replyMessage = reply.getMessage();
    				
    				hasActuallyFetchedReplies = true;
    				
    				if(!reply.mMessageID.equals(replyMessage.getID()))
    					Logger.error(this, "BoardReplyLink has message with wrong ID: " + reply);
    				
    				if(!reply.mAuthorID.equals(replyMessage.getAuthor().getID()))
    					Logger.error(this, "BoardReplyLink has message of wrong author: " + reply);
    				
    				if(!reply.mThreadID.equals(replyMessage.getThreadIDSafe()))
    					Logger.error(this, "BoardReplyLink has message of wrong thread: " + reply);
    				
    				if(!reply.mTitle.equals(replyMessage.getTitle()))
    					Logger.error(this, "BoardReplyLink has wrong title: " + reply);
    				
    				if(!reply.wasRead())
    					threadWasRead = false;
    				
    				// TODO: Verify the date.
    			} catch(MessageNotFetchedException e) {}
    		}
    		
    		if(thread.wasThreadRead() != threadWasRead)
    			Logger.error(this, "BoardThreadLink has wrong was-whole-thread-read status: " + thread);
    		
    		try {
    			final Message threadMessage = thread.getMessage();
    			
    			if(!thread.mThreadID.equals(threadMessage.getID()))
    				Logger.error(this, "BoardThreadLink has message of wrong ID: " + thread);
    			
    			if(!thread.mMessageID.equals(thread.mThreadID))
    				Logger.error(this, "BoardThreadLink has mismatch in thread and message ID: " + thread);
    			
    			if(!thread.mAuthorID.equals(threadMessage.getAuthor().getID()))
    				Logger.error(this, "BoardThreadLink has message of wrong author: " + thread);
    		} catch(NoSuchMessageException e) { 
    			if(!hasActuallyFetchedReplies)
    				Logger.error(this, "BoardThreadLink has no message and no replies: " + thread);
    		}
    	}
    	
    	for(final UnwantedMessageLink u : getAllUnwantedMessages()) {
        	if(u.mMessage == null)
        		Logger.error(this, "UnwantedMessageLink has mMessage==null: " + u);
        	
        	if(u.mAuthor == null)
        		Logger.error(this, "UnwantedMessageLink has mAuthor==null: " + u);
        	
        	final Date minNextRetry = new Date(u.mLastRetryDate.getTime() + UnwantedMessageLink.MINIMAL_RETRY_DELAY);
        	final Date maxNextRetry = new Date(u.mLastRetryDate.getTime() + UnwantedMessageLink.MAXIMAL_RETRY_DELAY);
        	
        	if(u.mNextRetryDate.before(minNextRetry))
        		Logger.error(this, "UnwantedMessageLink has invalid next retry date, too early: " + u);
        	else  if(u.mNextRetryDate.after(maxNextRetry))
        		Logger.error(this, "UnwantedMessageLink has invalid next retry date, too far in the future: " + u);
    	}
    }
}
