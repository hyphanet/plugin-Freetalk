/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import plugins.Freetalk.IdentityManager.IdentityDeletedCallback;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.MessageList.MessageFetchFailedMarker;
import plugins.Freetalk.MessageList.MessageListFetchFailedMarker;
import plugins.Freetalk.MessageList.MessageListID;
import plugins.Freetalk.MessageList.MessageReference;
import plugins.Freetalk.exceptions.DuplicateBoardException;
import plugins.Freetalk.exceptions.DuplicateElementException;
import plugins.Freetalk.exceptions.DuplicateFetchFailedMarkerException;
import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.DuplicateMessageListException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchFetchFailedMarkerException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import plugins.Freetalk.exceptions.NoSuchMessageRatingException;
import plugins.Freetalk.exceptions.NoSuchObjectException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.node.PrioRunnable;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;
import freenet.support.TrivialTicker;
import freenet.support.io.NativeThread;

/**
 * The MessageManager is the core connection between the UI and the backend of the plugin:
 * It is the entry point for posting messages, obtaining messages, obtaining boards, etc.
 * 
 * 
 * @author xor (xor@freenetproject.org)
 */
public abstract class MessageManager implements PrioRunnable, IdentityDeletedCallback {

	protected final IdentityManager mIdentityManager;
	
	protected final Freetalk mFreetalk;
	
	protected final ExtObjectContainer db;
	
	protected final PluginRespirator mPluginRespirator;
	
	private static final int STARTUP_DELAY = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) : (3 * 60 * 1000);
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (15 * 60 * 1000) : (15 * 60 * 1000);
	
	private static final int PROCESS_NEW_MESSAGES_DELAY = 1 * 60 * 1000;
	
	/**
	 * When a {@link Message} fetch fails (DNF for example) the message is marked as fetch failed and the fetch will be retried after a growing amount of time.
	 * This is the minimal delay.
	 */
	public static final long MINIMAL_MESSAGE_FETCH_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (10 * 60 * 1000) :  (4 * 60 * 60 * 1000); // TODO: Make configurable.
	
	/**
	 * When a {@link Message} fetch fails (DNF for example) the message is marked as fetch failed and the fetch will be retried after a growing amount of time.
	 * This is the maximal delay.
	 */
	public static final long MAXIMAL_MESSAGE_FETCH_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (30 * 60 * 1000) : (7 * 24 * 60 *60 * 1000); // TODO: Make configurable
		
	/**
	 * When a {@link MessageList} fetch fails (DNF for example) the {@link MessageList} is marked as fetch failed and the fetch will be retried after a
	 * growing amount of time. This is the minimal delay.
	 * Notice that this only applies to "old" message lists - that is message lists with an edition number lower than the latest successfully fetched edition.
	 */
	public static final long MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (10 * 60 * 1000) : (4 * 60 * 60 * 1000); // TODO: Make configurable.
	
	/**
	 * When a {@link MessageList} fetch fails (DNF for example) the {@link MessageList}  is marked as fetch failed and the fetch will be retried after a
	 * growing amount of time. This is the maximal delay.
	 * Notice that this only applies to "old" message lists - that is message lists with an edition number lower than the latest successfully fetched edition.
	 */
	public static final long MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (30 * 60 * 1000) : (7 * 24 * 60 * 60 * 1000);  // TODO: Make configurable.
	
	private final TrivialTicker mTicker;
	private final Random mRandom;
	

	public MessageManager(ExtObjectContainer myDB, IdentityManager myIdentityManager, Freetalk myFreetalk, PluginRespirator myPluginRespirator) {
		assert(myDB != null);
		assert(myIdentityManager != null);
		assert(myFreetalk != null);
		assert(myPluginRespirator != null);		
		
		db = myDB;
		mIdentityManager = myIdentityManager;
		mFreetalk = myFreetalk;
		mPluginRespirator = myPluginRespirator;
		
		mTicker = new TrivialTicker(mFreetalk.getPluginRespirator().getNode().executor);
		mRandom = mPluginRespirator.getNode().fastWeakRandom;
		
		mIdentityManager.registerIdentityDeletedCallback(this, true);
	}
	
	/**
	 * For being used in JUnit tests to run without a node.
	 */
	protected MessageManager(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
		db = mFreetalk.getDatabase();
		mIdentityManager = mFreetalk.getIdentityManager();
		mPluginRespirator = null;
		mTicker = null;
		mRandom = null;
	}
	
	public int getPriority() {
		return NativeThread.MIN_PRIORITY;
	}
	
	public void run() {
		Logger.debug(this, "Main loop running...");
		
		try {
			// Must be called periodically because it is not called on demand.
			clearExpiredFetchFailedMarkers();

			recheckUnwantedMessages();
		}  finally {
			long sleepTime = THREAD_PERIOD/2 + mRandom.nextInt(THREAD_PERIOD);
			Logger.debug(this, "Sleeping for " + sleepTime/(60*1000) + " minutes.");
			mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), sleepTime, false, true);
		}
		
		Logger.debug(this, "Main loop finished.");
	}
	
	private final Runnable mNewMessageProcessor = new PrioRunnable() {
		public int getPriority() {
			return NativeThread.NORM_PRIORITY;
		}
		
		public void run() {
			Logger.debug(MessageManager.this, "Processing new messages...");
			
			boolean success1 = addMessagesToBoards(); // Normally does not fail
			
			// CAN fail because SubscribedBoard.addeMessage() tries to query the Score of the author from WoT and this can
			// fail due to connectivity issues (and currently most likely due to bugs in PluginTalker and especially BlockingPluginTalker!)
			boolean success2 = synchronizeSubscribedBoards();
			
			// If it didn't work we re-schedule it... but not in unit tests, they would infinite loop..
			if(mTicker != null && (!success1 || !success2))
				scheduleNewMessageProcessing();
		}
	};
	
	private void scheduleNewMessageProcessing() {
		Logger.debug(this, "Scheduling new message processing to be run in " + PROCESS_NEW_MESSAGES_DELAY / (60*1000) + " minutes...");
		if(mTicker != null)
			mTicker.queueTimedJob(mNewMessageProcessor, "Freetalk " + this.getClass().getSimpleName(), PROCESS_NEW_MESSAGES_DELAY, false, true);
		else { // For unit tests
			mNewMessageProcessor.run();
		}
	}
	
	
	public void start() {
		Logger.debug(this, "Starting...");
		
		long startupDelay = STARTUP_DELAY/2 + mRandom.nextInt(STARTUP_DELAY); 
		Logger.debug(this, "Main loop will run in " + startupDelay/(60*1000) + " minutes.");
		mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), startupDelay, false, true);
		
		// It might happen that Freetalk is shutdown after a message has been downloaded and before addMessagesToBoards was called:
		// Then the message will still be stored but not visible in the boards because storing a message and adding it to boards are separate transactions.
		// Therefore, we must call addMessagesToBoards (and synchronizeSubscribedBoards) during startup.
		scheduleNewMessageProcessing();
		
		Logger.debug(this, "Started.");
	}

	public void terminate() {
		Logger.debug(this, "Stopping ..."); 
		mTicker.shutdown();
		Logger.debug(this, "Stopped.");
	}
	
	/**
	 * This is the primary function for posting messages.
	 * 
	 * @param myParentMessage The message to which the new message is a reply. Null if the message should be a thread.
	 * @param myBoards The boards to which the new message should be posted. Has to contain at least one board.
	 * @param myReplyToBoard The board to which replies to this message should be sent. This is just a recommendation. Notice that it should be contained in myBoards. Can be null.
	 * @param myAuthor The author of the new message. Cannot be null.
	 * @param myTitle The subject of the new message. Cannot be null or empty.
	 * @param myDate The UTC time of the message. Null to use the current time.
	 * @param myText The body of the new message. Cannot be null.
	 * @param myAttachments The Attachments of the new Message. See <code>Message.Attachment</code>. Set to null if the message has none.
	 * @return The new message.
	 * @throws InvalidParameterException Invalid board names, invalid title, invalid body.
	 * @throws Exception 
	 */
	public abstract OwnMessage postMessage(MessageURI myParentThreadURI, Message myParentMessage, Set<Board> myBoards, Board myReplyToBoard, OwnIdentity myAuthor,
			String myTitle, Date myDate, String myText, List<Attachment> myAttachments) throws InvalidParameterException, Exception;

	public OwnMessage postMessage(MessageURI myParentThreadURI, Message myParentMessage, Set<String> myBoards, String myReplyToBoard,
			OwnIdentity myAuthor, String myTitle, Date myDate, String myText, List<Attachment> myAttachments) throws Exception {

		HashSet<Board> boardSet = new HashSet<Board>();
		for (Iterator<String> i = myBoards.iterator(); i.hasNext(); ) {
			String boardName = i.next();
			Board board = getOrCreateBoard(boardName);
			boardSet.add(board);
		}

		Board replyToBoard = null;
		if (myReplyToBoard != null) {
			replyToBoard = getOrCreateBoard(myReplyToBoard);
		}

		return postMessage(myParentThreadURI, myParentMessage, boardSet, replyToBoard, myAuthor, myTitle, myDate, myText, myAttachments);
	}
	
	@SuppressWarnings("unchecked")
	public synchronized int countUnsentMessages() {
		Query q = db.query();
		q.constrain(OwnMessage.class);
		q.descend("mFreenetURI").constrain(null).identity();
		int unsentCount = 0;
		
		for(OwnMessage m : new Persistent.InitializingObjectSet<OwnMessage>(mFreetalk, q)) {
			// TODO: Remove this workaround for the db4o bug as soon as we are sure that it does not happen anymore.
			if(!m.testFreenetURIisNull()) // Logs an error for us
				continue;
		
			++unsentCount;
		}
		
		q = db.query();
		q.constrain(OwnMessageList.class);
		q.descend("iWasInserted").constrain(false);
		ObjectSet<OwnMessageList> notInsertedLists = q.execute();
		for(OwnMessageList list : notInsertedLists)
			unsentCount += list.getMessageCount();
		
		return unsentCount;
	}
	
	public synchronized int countMessages() {
		final Query q = db.query();
		q.constrain(Message.class);
		// This should use indexes and be O(1) therefore, which I'm not sure about with using "q.constrain(OwnMessage.class).not();"
		return q.execute().size() - countOwnMessages();
	}
	
	public synchronized int countOwnMessages() {
		final Query q = db.query();
		q.constrain(OwnMessage.class);
		return q.execute().size();
	}

	private synchronized void deleteMessage(Message message) {
		for(MessageRating rating : getAllMessageRatings(message)) {
			// This call does a full transaction.
			deleteMessageRating(rating);
		}
		
		for(Board board : message.getBoards()) {
			synchronized(board) {
			synchronized(message) { // TODO: Check whether we actually need to lock messages. I don't think so.
			synchronized(db.lock()) {
			try {
				board.deleteMessage(message);
				message.setLinkedIn(false);
				message.storeAndCommit();
			} catch (NoSuchMessageException e) {
				// The message was not added to the board yet, this is normal
			} catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
			}
			}
			}
			
			for(SubscribedBoard subscribedBoard : subscribedBoardIterator(board.getName())) {
				synchronized(subscribedBoard) {
				synchronized(message) { // TODO: Check whether we actually need to lock messages. I don't think so.
				synchronized(db.lock()) {
				try {
					subscribedBoard.deleteMessage(message);
					subscribedBoard.checkedCommit(this);
				} catch (NoSuchMessageException e) {
					// The message was not added to the board yet, this is normal
				} catch(RuntimeException e) {
					Persistent.checkedRollbackAndThrow(db, this, e);
				}
				}
				}
				}
			}
			
		}

		synchronized(message) { // TODO: Check whether we actually need to lock messages. I don't think so.
		synchronized(db.lock()) {	
			try {
				// Clear the "message was downloaded" flags of the references to this message.
				// This is necessary because the following transaction (deletion of the message lists of the identity) might fail and we should
				///re-download the message if the identity is not deleted.
				for(MessageReference ref : getAllReferencesToMessage(message.getID())) {
					ref.clearMessageWasDownloadedFlag();
					ref.storeWithoutCommit();
				}
				
				for(Message reply : getAllRepliesToMessage(message)) {
					reply.clearParent();
				}
				
				for(Message threadReply : getAllThreadRepliesToMessage(message)) {
					threadReply.clearThread();
				}
				
				message.deleteWithoutCommit();
				
				message.checkedCommit(this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
		}
	}
	
	/**
	 * Deletes all boards which are empty.
	 * A board is considered empty if:
	 * - There are no subscriptions to it
	 * - There are no fetched messages or message lists for it
	 * - There are no own messages or own message lists in it
	 * @return The list of the deleted boards' names.
	 */
	public synchronized ArrayList<String> deleteEmptyBoards() {
		Logger.normal(this, "Attempting to delete empty boards...");
		
		// TODO: Optimization: This might speed things up... or slow them down.
		// addMessagesToBoards();
		
		final ArrayList<String> deletedBoards = new ArrayList<String>();
		final ObjectSet<OwnMessage> notInsertedOwnMessages = notInsertedMessageIterator();
		
		for(final Board board: boardIteratorSortedByName()) { // TODO: Optimization: Implement & use a non-sorting function.
			if(board.hasSubscriptions()) {
				Logger.normal(this, "Not deleting board because it has subscriptions: " + board);
				continue;
			}
			
			// TODO: This is debug code, remove it when we are sure that it does not happen.
			if(subscribedBoardIterator(board.getName()).size() != 0) {
				Logger.error(this, "Board.hasSubscriptions()==false but subscribed boards exist, not deleting: " + board);
				continue;
			}
			
			if(board.getMessageCount() != 0) {
				Logger.normal(this, "Not deleting board because getMessageCount()!=0: " + board);
				continue;
			}
			
			if(getOwnMessages(board).size() > 0) {
				// TODO: We should provide functionality for deleting them.
				// I did not implement this yet because the goal of this function is to provide the ability to delete large amounts of
				// boards which were created as spam, the user is not very likely to have posted in them. Yet we WILL need provide the
				// ability to delete boards which only contain own messages.
				Logger.warning(this, "Cannot delete board because there are own messages in it: " + board);
				continue;
			}

			{ // Check for not inserted own messages...
				boolean containsNotInsertedOwnMessage = false;
				
				for(final OwnMessage notInserted : notInsertedOwnMessages) {
					if(board.contains(notInserted)) {
						containsNotInsertedOwnMessage = true;
						break;
					}
				}
				
				if(containsNotInsertedOwnMessage) {
					Logger.warning(this, "Cannot delete board because there are own messages in it: " + board);
					continue;
				}
			}
			
			if(getDownloadableMessageCount(board) > 0) {
				Logger.normal(this, "Not deleting board because it is referenced in message lists: " + board);
				continue;
			}
			
			synchronized(db.lock()) {
				try {
					Logger.normal(this, "Deleting empty board " + board);
					board.deleteWithoutCommit();
					board.checkedCommit(this);
					deletedBoards.add(board.getName());
				} catch(RuntimeException e) {
					Persistent.checkedRollback(db, this, e);
				}
			}
		}
		
		return deletedBoards;
	}
	
	/**
	 * Called by the {@link IdentityManager} before an identity is deleted from the database.
	 * 
	 * Deletes any messages and message lists referencing to it and commits the transaction.
	 */
	public synchronized void beforeIdentityDeletion(Identity identity) {
		Logger.debug(this, "Deleting all objects of identity " + identity);
		// We use multiple transactions here: We cannot acquire the db.lock() before deleteMessageRatting, board.deleteWithoutCommit and
		// deleteMessage. Each of them synchronize on message ratings / boards, therefore we must acquire the db.lock after synchronizing on each object.
		// TODO: Check whether this can result in bad side effects. IMHO it cannot.
		// If it can, add some mechanism similar to addMessagesToBoards()/synchronizeSubscribedBoards which handles half-deleted identities.
		
		if(identity instanceof OwnIdentity) {
			final OwnIdentity ownId = (OwnIdentity)identity;
			for(final MessageRating messageRating : getAllMessageRatingsBy(ownId)) {
				deleteMessageRating(messageRating); // This does a full transaction and commits it.
			}
						
			for(SubscribedBoard board : subscribedBoardIteratorSortedByName((OwnIdentity)identity)) { // TODO: Optimization: Use a non-sorting function.
				unsubscribeFromBoard(ownId, board); // This does a full transaction and commits it.
			}
		}

		for(Message message : getMessagesBy(identity)) {
			deleteMessage(message); // This does a full transaction and commits it.
		}

		synchronized(db.lock()) {
			try {
				for(MessageList messageList : getMessageListsBy(identity)) {
					messageList.deleteWithoutCommit();
					// We do not call onMessageListDeleted for the IdentityStatistics since the statistics object will be deleted within
					// this transaction anyway.
				}

				if(identity instanceof OwnIdentity) {
					final OwnIdentity ownId = (OwnIdentity)identity;
					
					for(final OwnMessage message : getOwnMessagesBy(ownId)) {
						message.deleteWithoutCommit();
						
						// We don't need to delete it from the boards because own messages are not being added to boards
						// We don't need to set parent/thread pointers to this message to null because parent/thread pointers are never set to an OwnMessage 
					}

					for(final OwnMessageList messageList : getOwnMessageListsBy(ownId)) {
						messageList.deleteWithoutCommit();
					}
				}
				
				try {
					getIdentityStatistics(identity).deleteWithoutCommit();
				} catch(NoSuchObjectException e) {}

				Logger.debug(this, "beforeIdentityDeletion finished for " + identity);
				Persistent.checkedCommit(db, this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
	}
	
	/**
	 * Called by the {@link MessageListInserter} implementation when the insertion of an {@link OwnMessageList} is to be started.
	 * Has to be called before any data is pulled from the {@link OwnMessageList}: It locks the list so no further messages can be added.
	 * Further, you have to acquire the lock on this MessageManager before calling this function and while taking data from the {@link OwnMessageList} since
	 * the lock of the message list could be cleared and further messages could be added if you do not.
	 * 
	 * @param uri The URI of the {@link OwnMessageList}.
	 * @throws NoSuchMessageListException If there is no such {@link OwnMessageList}.
	 */
	public synchronized void onMessageListInsertStarted(OwnMessageList list) {
		synchronized(db.lock()) {
			try {
				list.beginOfInsert();
				Persistent.checkedCommit(db, this);
			}
			catch(RuntimeException e) {
				// This function MUST NOT succeed if the list was not marked as being inserted: Otherwise messages could be added to the list while it is
				// being inserted already, resulting in the messages being marked as successfully inserted but not being visible to anyone!
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
	}
	
	/**
	 * Called by the {@link MessageListInserter} implementation when the insertion of an {@link OwnMessageList} succeeded. Marks the list as inserted.
	 * 
	 * @param uri The URI of the {@link OwnMessageList}.
	 * @throws NoSuchMessageListException If there is no such {@link OwnMessageList}.
	 */
	public synchronized void onMessageListInsertSucceeded(FreenetURI uri) throws NoSuchMessageListException {
		synchronized(db.lock()) {
			try {
				OwnMessageList list = getOwnMessageList(MessageListID.construct(uri).toString());
				list.markAsInserted();
				list.checkedCommit(this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
	}
	
	/**
	 * Called by the {@link MessageListInserter} implementation when the insertion of an {@link OwnMessageList} fails.
	 * Clears the "being inserted"-flag of the given message list.
	 * 
	 * @param uri The URI of the {@link OwnMessageList}.
	 * @param collision Whether the index of the {@link OwnMessageList} was already taken. If true, the index of the message list is incremented.
	 * @throws NoSuchMessageListException If there is no such {@link OwnMessageList}.
	 */
	public abstract void onMessageListInsertFailed(FreenetURI uri, boolean collision) throws NoSuchMessageListException;
	
	public synchronized void onMessageReceived(Message message) {
		message.initializeTransient(mFreetalk);
		
		boolean wasDownloadedAlready;
		try {
			message = get(message.getID());
			wasDownloadedAlready = true;
			Logger.error(this, "Downloaded a message which we already have: " + message);
		}
		catch(NoSuchMessageException e) {
			wasDownloadedAlready = false;
		}
		
		synchronized(db.lock()) {
			try {
				if(!wasDownloadedAlready) {
					message.storeWithoutCommit();
				}
				
				// We also try to mark the message as downloaded if it was fetched already to ensure that its not being fetched over and over again.

				for(MessageReference ref : getAllReferencesToMessage(message.getID())) {
					try {
						getMessageFetchFailedMarker(ref).deleteWithoutCommit();
						Logger.normal(this, "Deleted a FetchFailedMarker for the message.");
					} catch(NoSuchFetchFailedMarkerException e1) { }
					
					ref.setMessageWasDownloadedFlag();
					ref.storeWithoutCommit();
				}
				
				if(wasDownloadedAlready) {
					Persistent.checkedCommit(db, this);
					return;
				}
				
				try {
					message.setThread(get(message.getThreadID())); // Calls storeWithoutCommit
				} catch(NoSuchMessageException e) {
					// The message has no thread ID or the parent thread was not downloaded yet
				}
				
				try {
					message.setParent(get(message.getParentID()));  // Calls storeWithoutCommit
				} catch(NoSuchMessageException e) {
					// The message has no parent ID or the parent message was not downloaded yet
				}
				
				for(Message reply : getAllRepliesToMessage(message.getID())) {
					reply.setParent(message); // Calls storeWithoutCommit
				}
				
				for(Message threadReply : getAllThreadRepliesToMessage(message.getID())) {
					threadReply.setThread(message); // Calls storeWithoutCommit
				}

				Persistent.checkedCommit(db, this);
			}
			catch(Exception ex) {
				Persistent.checkedRollback(db, this, ex);
			}
		}
		
		scheduleNewMessageProcessing();
	}
	
	/**
	 * @return True If adding new messages succeeded, false if not.
	 */
	private synchronized boolean addMessagesToBoards() {
		Logger.normal(this, "Adding messages to boards...");
		
		Query q = db.query();
		q.constrain(Message.class);
		q.descend("mWasLinkedIn").constrain(false);
		q.constrain(OwnMessage.class).not();
		ObjectSet<Message> invisibleMessages = new Persistent.InitializingObjectSet<Message>(mFreetalk, q);
		
		boolean allSuccessful = true;
		
		for(Message message : invisibleMessages) {
			boolean messageSuccessful = true;
			
			for(Board board : message.getBoards()) {
				synchronized(board) {
				synchronized(message) {
				synchronized(db.lock()) {
					try {
						Logger.debug(this, "Adding message to board: " + message);
						board.addMessage(message);
						board.checkedCommit(this);
					}
					catch(Exception e) {
						messageSuccessful = false;
						Persistent.checkedRollback(db, this, e);			
					}
				}
				}
				}
			}
			
			if(messageSuccessful) {
				synchronized(message) {
				synchronized(db.lock()) {
					message.setLinkedIn(true);
					message.storeAndCommit();
				}
				}
			} else {
				allSuccessful = false;
			}
		}
		
		Logger.normal(this, "Finished adding messages to boards.");
		
		return allSuccessful;
	}
	
	private synchronized boolean synchronizeSubscribedBoards() {
		Logger.normal(this, "Synchronizing subscribed boards...");

		boolean success = true;
		
		for(SubscribedBoard board : subscribedBoardIterator()) {
			// No need to lock the parent board because we do not modify it and we've locked the MessageManager which prevents writes to the parent board.
			// synchronized(board.getParentBoard()) {
			synchronized(board) {
			synchronized(db.lock()) {
				try {
					board.synchronizeWithoutCommit();
					board.checkedCommit(this);
				}
				catch(Exception e) {
					success = false;
					Persistent.checkedRollback(db, this, e);
				}
			}
			}
			// }
			
			Thread.yield();
		}
		
		Logger.normal(this, "Finished synchronizing subscribed boards.");
		return success;
	}
	
	public synchronized void onMessageListReceived(MessageList list) {
		list.initializeTransient(mFreetalk);
		
		// It's not possible to keep the synchronization order of message lists to synchronize BEFORE synchronizing on db.lock() some places so we 
		// do not synchronize here.
		// And in this function we don't need to synchronize on it anyway because it is not known to anything which might modify it anyway.
		// In general, due to those issues the functions which modify message lists just use the message manager as synchronization object. 
		//synchronized(list) {
		MessageListFetchFailedMarker marker;
		MessageList ghostList;

		try {
			marker = getMessageListFetchFailedMarker(list.getID());
		}
		catch(NoSuchFetchFailedMarkerException e) {
			marker = null;
		}
		
		try {
			ghostList = getMessageList(list.getID());
			
			if(marker == null) {
				Logger.debug(this, "Downloaded a MessageList which we already have: " + list);
				return;
			}

		} catch(NoSuchMessageListException e) {
			ghostList = null;
		}

		synchronized(db.lock()) {
				try {
					if(marker != null) {
						marker.deleteWithoutCommit();
						Logger.normal(this, "Deleted a FetchFailedMarker for the MessageList.");
						
						if(ghostList != null) {
							Logger.error(this, "MessageList was fetched even though a ghost list existed for it! Deleting the ghost list: " + ghostList);
							ghostList.deleteWithoutCommit();
							// We don't call onMessageListDeleted on the IdentityStatistics since we will call onMessageListFetched for the 
							// list with the same ID in this transaction anyway. 
						}
					}
					
					// Mark existing messages as fetched... Can happen if a message is list in multiple lists.
					for(MessageReference ref : list) {
						try {
							get(ref.getMessageID());
							ref.setMessageWasDownloadedFlag();
							ref.storeWithoutCommit();
						} catch(NoSuchMessageException e) {}
					}
					
					list.storeWithoutCommit();
					
					final IdentityStatistics stats = getOrCreateIdentityStatistics(list.getAuthor());
					stats.onMessageListFetched(list);
					stats.storeWithoutCommit();
					
					list.checkedCommit(this);
				}
				catch(RuntimeException ex) {
					Persistent.checkedRollback(db, this, ex);
				}
		}
		//}
	}
	
	/**
	 * Abstract because we need to store an object of a child class of MessageList which is chosen dependent on which implementation of the
	 * messaging system we are using.
	 */
	public abstract void onMessageListFetchFailed(Identity author, FreenetURI uri, FetchFailedMarker.Reason reason);
	
	public synchronized void onMessageFetchFailed(MessageReference messageReference, FetchFailedMarker.Reason reason) {
		try {
			get(messageReference.getMessageID());
			Logger.debug(this, "Trying to mark a message as 'download failed' which we actually have: " + messageReference.getURI());
		}
		catch(NoSuchMessageException e) {
			synchronized(db.lock()) {
			try {				
				Date date = CurrentTimeUTC.get();
				
				for(MessageReference ref : getAllReferencesToMessage(messageReference.getMessageID())) {
					MessageList.MessageFetchFailedMarker failedMarker;
					
					try {
						failedMarker = getMessageFetchFailedMarker(ref);
						failedMarker.setReason(reason);
						failedMarker.incrementNumberOfRetries();
						Date dateOfNextRetry = calculateDateOfNextMessageFetchRetry(failedMarker.getReason(), date, failedMarker.getNumberOfRetries());
						failedMarker.setDate(date);
						failedMarker.setDateOfNextRetry(dateOfNextRetry);
					} catch(NoSuchFetchFailedMarkerException e1) {
						Date dateOfNextRetry = calculateDateOfNextMessageFetchRetry(reason, date, 0);
						failedMarker = new MessageList.MessageFetchFailedMarker(ref, reason, date, dateOfNextRetry);
						failedMarker.initializeTransient(mFreetalk);
					}
					
					ref.setMessageWasDownloadedFlag();
					ref.storeWithoutCommit();
					// failedMarker.setAllowRetryNow(false); // setDateOfNextRetry does this for us
					failedMarker.storeWithoutCommit();
				
					
					Logger.normal(this, "Marked message as download failed with reason " + reason + " (next retry is at " + failedMarker.getDateOfNextRetry()
							+ ", number of retries: " + failedMarker.getNumberOfRetries() + "): "
							+  messageReference.getURI());
				}
				
				
				Persistent.checkedCommit(db, this);
			}
			catch(RuntimeException ex) {
				Persistent.checkedRollback(db, this, ex);
			}
			}
		}
	}
	
	protected Date calculateDateOfNextMessageFetchRetry(FetchFailedMarker.Reason reason, Date now, int numberOfRetries) {
		switch(reason) {
			case DataNotFound:
				return new Date(now.getTime() + Math.min(MINIMAL_MESSAGE_FETCH_RETRY_DELAY * (1<<numberOfRetries), MAXIMAL_MESSAGE_FETCH_RETRY_DELAY));
			case ParsingFailed:
				return new Date(Long.MAX_VALUE);
			default:
				return new Date(now.getTime()  + MINIMAL_MESSAGE_FETCH_RETRY_DELAY);
		}
	}
	
	protected Date calculateDateOfNextMessageListFetchRetry(FetchFailedMarker.Reason reason, Date now, int numberOfRetries) {
		switch(reason) {
			case DataNotFound:
				return new Date(now.getTime()  + Math.min(MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY * (1<<numberOfRetries), MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY));
			case ParsingFailed:
				return new Date(Long.MAX_VALUE);
			default:
				return new Date(now.getTime()  + MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY);
		}		
	}

	private ObjectSet<FetchFailedMarker> getExpiredFetchFailedMarkers(final Date now) {
		final Query query = db.query();
		query.constrain(FetchFailedMarker.class);
		query.descend("mDateOfNextRetry").constrain(now).greater().not();
		query.descend("mRetryAllowedNow").constrain(false);
		return new Persistent.InitializingObjectSet<FetchFailedMarker>(mFreetalk, query);
	}
	
	public MessageFetchFailedMarker getMessageFetchFailedMarker(final MessageReference ref) throws NoSuchFetchFailedMarkerException {
		final Query q = db.query();
		q.constrain(MessageFetchFailedMarker.class);
		q.descend("mMessageReference").constrain(ref).identity();
		@SuppressWarnings("unchecked")
		final ObjectSet<MessageFetchFailedMarker> markers = q.execute();
		
		switch(markers.size()) {
			case 1:
				final MessageFetchFailedMarker result = markers.next();
				result.initializeTransient(mFreetalk);
				return result;
			case 0:
				throw new NoSuchFetchFailedMarkerException(ref.toString());
			default:
				throw new DuplicateFetchFailedMarkerException(ref.toString());
		}
	}
	
	protected MessageListFetchFailedMarker getMessageListFetchFailedMarker(final String messageListID) throws NoSuchFetchFailedMarkerException {
		final Query q = db.query();
		q.constrain(MessageListFetchFailedMarker.class);
		q.descend("mMessageListID").constrain(messageListID);
		@SuppressWarnings("unchecked")
		final ObjectSet<MessageListFetchFailedMarker> markers = q.execute();
		
		switch(markers.size()) {
			case 1:
				final MessageListFetchFailedMarker result = markers.next();
				result.initializeTransient(mFreetalk);
				return result;
			case 0:
				throw new NoSuchFetchFailedMarkerException(messageListID);
			default:
				throw new DuplicateFetchFailedMarkerException(messageListID);
		}
	}
	
	/**
	 * Only for being used by the MessageManager itself and by unit tests.
	 */
	protected synchronized void clearExpiredFetchFailedMarkers() {
		Logger.normal(this, "Clearing expired FetchFailedMarkers...");
	
		Date now = CurrentTimeUTC.get();
		
		int amount = 0;
		
		for(FetchFailedMarker marker : getExpiredFetchFailedMarkers(now)) {
			synchronized(db.lock()) {
				try {
					if(marker instanceof MessageFetchFailedMarker) {
						MessageFetchFailedMarker m = (MessageFetchFailedMarker)marker;
						MessageReference ref = m.getMessageReference();
						ref.clearMessageWasDownloadedFlag();
						ref.storeWithoutCommit();
					} else if(marker instanceof MessageListFetchFailedMarker) {
						MessageListFetchFailedMarker m = (MessageListFetchFailedMarker)marker;
						try {
							MessageList list = getMessageList(m.getMessageListID());
							list.deleteWithoutCommit();
							
							final IdentityStatistics stats = getOrCreateIdentityStatistics(list.getAuthor());
							stats.onMessageListDeleted(list);
							stats.storeWithoutCommit();
							
							m.storeWithoutCommit(); // MessageList.deleteWithoutCommit deletes it.
						}
						catch(NoSuchMessageListException e) {
							// The marker was already processed.
						}
					} else
						Logger.error(this, "Unknown FetchFailedMarker type: " + marker);
					
					++amount;
					
					Logger.debug(this, "Cleared marker " + marker);
					marker.setAllowRetryNow(true);
					marker.checkedCommit(this);
				}
				catch(RuntimeException e) {
					Persistent.checkedRollback(db, this, e);
				}
			}
		}
		
		Logger.normal(this, "Finished clearing " + amount + " expired FetchFailedMarkers.");

		assert(validateMessageFetchFailedMarkers(now));
	}
	
	private boolean validateMessageFetchFailedMarkers(Date now) {
		boolean valid = true;
		
		Query q = db.query();
		q.constrain(MessageFetchFailedMarker.class);
		q.descend("mDateOfNextRetry").constrain(now).greater();
		ObjectSet<MessageFetchFailedMarker> messageMarkers = new Persistent.InitializingObjectSet<MessageFetchFailedMarker>(mFreetalk, q);
		
		for(MessageFetchFailedMarker marker : messageMarkers) {
			if(marker.isRetryAllowedNow()) {
				assert(false);
				Logger.error(this, "Invalid MessageFetchFailedMarker: Date of next retry is in future but isRetryAllowedNow==true: " + marker);
				valid = false;
			}
				
			if(!marker.getMessageReference().wasMessageDownloaded()) {
				assert(false);
				valid = false;
				Logger.error(this, "Invalid MessageFetchFailedMarker: Date of next retry is in future but message is marked as not fetched: " + marker);
			}
		}
		
		Logger.normal(this, "Number of non-expired MessageFetchFailedMarker: " + messageMarkers.size());
		

		q = db.query();
		q.constrain(MessageListFetchFailedMarker.class);
		q.descend("mDateOfNextRetry").constrain(now).greater();
		ObjectSet<MessageListFetchFailedMarker> listMarkers = new Persistent.InitializingObjectSet<MessageListFetchFailedMarker>(mFreetalk, q);
		
		for(MessageListFetchFailedMarker marker : listMarkers) {
			if(marker.isRetryAllowedNow()) {
				assert(false);
				Logger.error(this, "Invalid MessageListFetchFailedMarker: Date of next retry is in future but isRetryAllowedNow==true: " + marker);
				valid = false;
			}
			
			try {
				getMessageList(marker.getMessageListID());
			} catch(NoSuchMessageListException e) {
				assert(false);
				valid = false;
				Logger.error(this, "Invalid MessageListFetchFailedMarker: Date of next retry is in future but there is no ghost message list for it: " + marker);
			}
		}
		
		Logger.normal(this, "Number of non-expired MessageListFetchFailedMarker: " + listMarkers.size());
		
		return valid;
	}
	
	/**
	 * Only for being used by the MessageManager itself and by unit tests.
	 * 
	 * Checks whether there are any messages in subscribed boards which the subscriber did not want to read (because he does not like the author) and now
	 * wants to read ... they must be added to the boards then.
	 */
	protected synchronized void recheckUnwantedMessages() {
		Logger.normal(this, "Rechecking unwanted messages...");
		
		Date now = CurrentTimeUTC.get();
		
		for(SubscribedBoard board : subscribedBoardIterator()) {
			board.retryAllUnwantedMessages(now);
		}
		
		Logger.normal(this, "Finished rechecking unwanted message");
	}
	
	/**
	 * Get a list of all MessageReference objects to the given message ID. References to OwnMessage are not returned.
	 * Used to mark the references to a message which was downloaded as downloaded.
	 */
	private ObjectSet<MessageList.MessageReference> getAllReferencesToMessage(final String id) {
		final Query query = db.query();
		query.constrain(MessageList.MessageReference.class);
		query.constrain(OwnMessageList.OwnMessageReference.class).not();
		query.descend("mMessageID").constrain(id);
		return new Persistent.InitializingObjectSet<MessageList.MessageReference>(mFreetalk, query);
	}
	
	private ObjectSet<Message> getAllRepliesToMessage(String messageID) {
		final Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mParentID").constrain(messageID);
		return new Persistent.InitializingObjectSet<Message>(mFreetalk, query);
	}
	
	private ObjectSet<Message> getAllRepliesToMessage(Message message) {
		final Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mParent").constrain(message).identity();
		return new Persistent.InitializingObjectSet<Message>(mFreetalk, query);
	}
	
	private ObjectSet<Message> getAllThreadRepliesToMessage(String threadID) {
		final Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mThreadID").constrain(threadID);
		return new Persistent.InitializingObjectSet<Message>(mFreetalk, query);
	}
	
	private ObjectSet<Message> getAllThreadRepliesToMessage(Message message) {
		final Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mThread").constrain(message).identity();
		return new Persistent.InitializingObjectSet<Message>(mFreetalk, query);
	}

	/**
	 * Get a message by its URI. The transient fields of the returned message will be initialized already.
	 * This will NOT return OwnMessage objects. Your own messages will be returned by this function as soon as they have been downloaded.
	 * @throws NoSuchMessageException 
	 */
	public Message get(FreenetURI uri) throws NoSuchMessageException {
		/* return get(Message.getIDFromURI(uri)); */
		throw new UnsupportedOperationException("Getting a message by it's URI is inefficient compared to getting by ID. Please only repair this function if absolutely unavoidable.");
	}
	
	/**
	 * Get a message by its ID. The transient fields of the returned message will be initialized already.
	 * This will NOT return OwnMessage objects. Your own messages will be returned by this function as soon as they have been downloaded as
	 * if they were normal messages of someone else.
	 * @throws NoSuchMessageException 
	 */
	@SuppressWarnings("unchecked")
	public synchronized Message get(final String id) throws NoSuchMessageException {
		final Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mID").constrain(id);
		final ObjectSet<Message> result = query.execute();

		switch(result.size()) {
			case 1:
				final Message m = result.next();
				m.initializeTransient(mFreetalk);
				return m;
			case 0:
				throw new NoSuchMessageException(id);
			default:
				throw new DuplicateMessageException(id);
		}
	}
	
	/**
	 * Get a <code>MessageList</code> by its ID. The transient fields of the returned <code>MessageList</code>  will be initialized already.
	 * This will NOT return <code>OwnMessageList</code> objects. Your own message lists will be returned by this function as soon as they have
	 * been downloaded as if they were normal message  lists of someone else.
	 * @throws NoSuchMessageListException 
	 */
	@SuppressWarnings("unchecked")
	public synchronized MessageList getMessageList(final String id) throws NoSuchMessageListException {
		final Query query = db.query();
		query.constrain(MessageList.class);
		query.constrain(OwnMessageList.class).not();
		query.descend("mID").constrain(id);
		final ObjectSet<MessageList> result = query.execute();

		switch(result.size()) {
			case 1:
				final MessageList list = result.next();
				list.initializeTransient(mFreetalk);
				return list;
			case 0:
				throw new NoSuchMessageListException(id);
			default:
				throw new DuplicateMessageListException(id);
		}
	}
	
	@SuppressWarnings("unchecked")
	public synchronized OwnMessageList getOwnMessageList(final String id) throws NoSuchMessageListException {
		final Query query = db.query();
		query.constrain(OwnMessageList.class);
		query.descend("mID").constrain(id);
		final ObjectSet<OwnMessageList> result = query.execute();

		switch(result.size()) {
			case 1:
				final OwnMessageList list = result.next();
				list.initializeTransient(mFreetalk);
				return list;
			case 0:
				throw new NoSuchMessageListException(id);
			default:
				throw new DuplicateMessageListException(id);
		}
	}
	
	public OwnMessage getOwnMessage(final FreenetURI uri) throws NoSuchMessageException {
		/* return getOwnMessage(Message.getIDFromURI(uri)); */
		throw new UnsupportedOperationException("Getting a message by it's URI is inefficient compared to getting by ID. Please only repair this function if absolutely unavoidable.");
	}
	
	@SuppressWarnings("unchecked")
	public synchronized OwnMessage getOwnMessage(final String id) throws NoSuchMessageException {
		final Query query = db.query();
		query.constrain(OwnMessage.class);
		query.descend("mID").constrain(id);
		final ObjectSet<OwnMessage> result = query.execute();

		switch(result.size()) {
			case 1:
				final OwnMessage m = result.next();
				m.initializeTransient(mFreetalk);
				return m;
			case 0:
				throw new NoSuchMessageException(id);
			default:
				throw new DuplicateMessageException(id);
		}
	}

	/**
	 * Get a board by its name. The transient fields of the returned board will be initialized already.
	 * @throws NoSuchBoardException 
	 */
	@SuppressWarnings("unchecked")
	public synchronized Board getBoardByName(String name) throws NoSuchBoardException {
		name = name.toLowerCase();
		
		final Query query = db.query();
		query.constrain(Board.class);
		query.constrain(SubscribedBoard.class).not();
		query.descend("mName").constrain(name);
		final ObjectSet<Board> result = query.execute();

		switch(result.size()) {
			case 1:
				final Board b = result.next();
				b.initializeTransient(mFreetalk);
				return b;
			case 0:
				throw new NoSuchBoardException(name);
			default:
				throw new DuplicateBoardException(name);
		}
	}
	
	/**
	 * Gets the board with the given name. If it does not exist, it is created and stored, the transaction is commited.
	 * @param The name of the desired board
	 * @throws InvalidParameterException If the name is invalid.
	 */
	public synchronized Board getOrCreateBoard(String name) throws InvalidParameterException {
		name = name.toLowerCase();
		
		Board board;

		try {		
			board = getBoardByName(name);
		}
		catch(NoSuchBoardException e) {
			synchronized(db.lock()) {
			try {
				board = new Board(name);
				board.initializeTransient(mFreetalk);
				board.storeWithoutCommit();
				Logger.debug(this, "Created board " + name);
				board.checkedCommit(this);
			}
			catch(RuntimeException ex) {
				Persistent.checkedRollbackAndThrow(db, this, ex);
				throw ex; // Satisfy the compiler
			}
			}
		}
		
		return board;
	}

	/**
	 * Get an iterator of all boards. The list is sorted ascending by name.
	 * 
	 * You have to synchronize on this MessageManager before calling this function and while processing the returned list.
	 * The transient fields of the returned boards will be initialized already.
	 */
	public ObjectSet<Board> boardIteratorSortedByName() {
		final Query query = db.query();
		query.constrain(Board.class);
		query.constrain(SubscribedBoard.class).not();
		query.descend("mName").orderAscending();
		return new Persistent.InitializingObjectSet<Board>(mFreetalk, query);
	}
	
	/**
	 * Get all boards which are being subscribed to by at least one {@link OwnIdentity}, i.e. the boards from which we should download messages.
	 */
	public synchronized ObjectSet<Board> boardWithSubscriptionsIterator() {
		final Query query = db.query();
		query.constrain(Board.class);
		query.descend("mHasSubscriptions").constrain(true);
		return new Persistent.InitializingObjectSet<Board>(mFreetalk, query);
	}

	public synchronized ObjectSet<SubscribedBoard> subscribedBoardIterator() {
		final Query query = db.query();
		query.constrain(SubscribedBoard.class);
		return new Persistent.InitializingObjectSet<SubscribedBoard>(mFreetalk, query);
	}
	/**
	 * Get an iterator of boards which were first seen after the given Date, sorted ascending by the date they were first seen at.
	 */
	public synchronized ObjectSet<SubscribedBoard> subscribedBoardIteratorSortedByDate(final OwnIdentity subscriber, final Date seenAfter) {
		final Query query = db.query();
		query.constrain(SubscribedBoard.class);
		query.descend("mFirstSeenDate").constrain(seenAfter).greater();
		query.descend("mFirstSeenDate").orderAscending();
		return new Persistent.InitializingObjectSet<SubscribedBoard>(mFreetalk, query);
	}
	
	/**
	 * Get a list of all subscribed boards of the given identity. The list is sorted ascending by name.
	 * 
	 * You have to synchronize on this MessageManager before calling this function and while processing the returned list.
	 * 
	 * The transient fields of the returned objects will be initialized already. 
	 */
	public ObjectSet<SubscribedBoard> subscribedBoardIteratorSortedByName(final OwnIdentity subscriber) {
		final Query query = db.query();
		query.constrain(SubscribedBoard.class);
		query.descend("mSubscriber").constrain(subscriber).identity();
		query.descend("mName").orderAscending();
		return new Persistent.InitializingObjectSet<SubscribedBoard>(mFreetalk, query);
	}
	
	protected ObjectSet<SubscribedBoard> subscribedBoardIterator(String boardName) {
		boardName = boardName.toLowerCase();
		
    	final Query q = db.query();
    	q.constrain(SubscribedBoard.class);
    	q.descend("mName").constrain(boardName);
    	return new Persistent.InitializingObjectSet<SubscribedBoard>(mFreetalk, q);
    }
	
    @SuppressWarnings("unchecked")
	public synchronized SubscribedBoard getSubscription(final OwnIdentity subscriber, String boardName) throws NoSuchBoardException {
    	boardName = boardName.toLowerCase();
    	
    	final Query q = db.query();
    	q.constrain(SubscribedBoard.class);
    	q.descend("mName").constrain(boardName);
    	q.descend("mSubscriber").constrain(subscriber).identity();
    	final ObjectSet<SubscribedBoard> result = q.execute();
    	
    	switch(result.size()) {
    		case 1:
    			final SubscribedBoard board = result.next();
    			board.initializeTransient(mFreetalk);
    			return board;
    		case 0: throw new NoSuchBoardException(boardName);
    		default: throw new DuplicateBoardException(boardName);
    	}
    }
    
	/**
	 * You do NOT need to synchronize on the IdentityManager when calling this function.
	 */
	public SubscribedBoard subscribeToBoard(OwnIdentity subscriber, String boardName) throws InvalidParameterException, NoSuchIdentityException, NoSuchBoardException {
		boardName = boardName.toLowerCase();
		
		synchronized(mIdentityManager) {
			subscriber = mIdentityManager.getOwnIdentity(subscriber.getID()); // Ensure that the identity still exists so the caller does not have to synchronize.

			synchronized(this) {
				Board board = getBoardByName(boardName);

				try {
					return getSubscription(subscriber, boardName);
				}
				catch(NoSuchBoardException e) {
					synchronized(db.lock()) {
						try {
							SubscribedBoard subscribedBoard = new SubscribedBoard(board, subscriber);
							subscribedBoard.initializeTransient(mFreetalk);
							subscribedBoard.storeWithoutCommit();
							subscribedBoard.synchronizeWithoutCommit();
							
							if(board.hasSubscriptions() == false) {
								Logger.debug(this, "First subscription received for board " + board + ", setting it's HasSubscriptions flag.");
								board.setHasSubscriptions(true);
								board.storeWithoutCommit();
							}
							
							subscribedBoard.checkedCommit(this);

							return subscribedBoard;
						}
						catch(InvalidParameterException error) {
							Persistent.checkedRollbackAndThrow(db, this, new RuntimeException(error));
							throw error; // Satisfy the compiler
						}
						catch(Exception error) {
							Persistent.checkedRollbackAndThrow(db, this, new RuntimeException(error));
							throw new RuntimeException(error); // Satisfy the compiler
						}
					}
				}
			}
		}
	}
	
	protected synchronized void unsubscribeFromBoard(OwnIdentity subscriber, SubscribedBoard subscribedBoard) {
		synchronized(subscribedBoard) {
			synchronized(db.lock()) {
				try {
					subscribedBoard.deleteWithoutCommit();
					
					if(subscribedBoardIterator(subscribedBoard.getName()).isEmpty()) {
						try {
							Board board = getBoardByName(subscribedBoard.getName());
							Logger.debug(this, "Last subscription to board " + board + " removed, clearing it's HasSubscriptions flag.");
							board.setHasSubscriptions(false);
							board.storeWithoutCommit();
						} catch (NoSuchBoardException e) { 
							throw new RuntimeException(e); // Should not happen. 
						}
					}
					
					subscribedBoard.checkedCommit(this);
				}
				catch(RuntimeException e) {
					Persistent.checkedRollbackAndThrow(db, this, e);
				}
			}
		}
	}
	
	/**
	 * You do NOT need to synchronize on the IdentityManager when calling this function. 
	 */
	public void unsubscribeFromBoard(OwnIdentity subscriber, String boardName) throws NoSuchBoardException, NoSuchIdentityException {
		synchronized(mIdentityManager) {
		subscriber = mIdentityManager.getOwnIdentity(subscriber.getID()); // Ensure that the identity still exists so the caller does not have to synchronize.
			
		synchronized(this) {		
			SubscribedBoard subscribedBoard = getSubscription(subscriber, boardName);
			unsubscribeFromBoard(subscriber, subscribedBoard);
		}
		}
	}

	public synchronized ObjectSet<OwnMessage> notInsertedMessageIterator() {
		final Query query = db.query();
		query.constrain(OwnMessage.class);
		query.descend("mFreenetURI").constrain(null).identity();
		// TODO: Sort ascending by date if db4o is intelligent enough to evaluate the mFreenetURI constrain before sorting...
		return new Persistent.InitializingObjectSet<OwnMessage>(mFreetalk, query);

	}
	
	/**
	 * Get a list of not downloaded messages. This function only returns messages which are posted to a board which an OwnIdentity wants to
	 * receive messages from. However, it might also return messages which are from an author which nobody wants to receive messages from.
	 * Filtering out unwanted authors is done at MessageList-level: MessageLists are only downloaded from identities which we want to read
	 * messages from.
	 */
	public synchronized ObjectSet<MessageList.MessageReference> notDownloadedMessageIterator() {
		// TODO: This query is very slow!
		final Query query = db.query();
		query.constrain(MessageList.MessageReference.class);
		query.constrain(OwnMessageList.OwnMessageReference.class).not();
		query.descend("mWasDownloaded").constrain(false);
		query.descend("mDate").orderDescending();
		query.descend("mBoard").descend("mHasSubscriptions").constrain(true);
		
		// TODO: The date only contains day, month and year (the XML does not contain more). We have some randomization by sorting by date but we might
		// want even more maybe - are there any security issues with not downloading messages in perfectly random order? Probably not?

		return new Persistent.InitializingObjectSet<MessageList.MessageReference>(mFreetalk, query);		
	}
	
	public synchronized int getDownloadableMessageCount(final Board board) {
		final Query query = db.query();
		query.constrain(MessageList.MessageReference.class);
		query.constrain(OwnMessageList.OwnMessageReference.class).not();
		query.descend("mBoard").constrain(board).identity();
		return query.execute().size();
	}

	/**
	 * Gets all downloadable messages for the given board and sorts them descending by date.
	 * The date here is NOT the date specified by the author but the date when we got to know about the message.
	 */
	public ObjectSet<MessageList.MessageReference> getDownloadableMessagesSortedByDate(final Board board) {
		final Query query = db.query();
		query.constrain(MessageList.MessageReference.class);
		query.constrain(OwnMessageList.OwnMessageReference.class).not();
		query.descend("mBoard").constrain(board).identity();
		query.descend("mCreationDate").orderDescending();
		return new Persistent.InitializingObjectSet<MessageList.MessageReference>(mFreetalk, query);
	}


	/**
	 * Get a list of all message lists from the given identity.
	 * If the identity is an {@link OwnIdentity}, it's own message lists are only returned if they have been downloaded as normal message lists.
	 * Technically, this means that no objects of class {@link OwnMessageList} are returned.
	 * 
	 * The purpose of this behavior is to ensure that own messages are only displayed to the user if they have been successfully inserted.
	 * 
	 * @param author An identity or own identity.
	 * @return All message lists of the given identity except those of class OwnMessageList.
	 */
	protected synchronized ObjectSet<MessageList> getMessageListsBy(final Identity author) {
		final Query query = db.query();
		query.constrain(MessageList.class);
		query.constrain(OwnMessageList.class).not();
		query.descend("mAuthor").constrain(author).identity();
		return new Persistent.InitializingObjectSet<MessageList>(mFreetalk, query);
	}
	
	/**
	 * Get a list of locally stored own message lists of the given identity. 
	 * Locally stored means that only message lists of class {@link OwnMessageList} are returned.
	 * 
	 * This means that there is no guarantee that the returned message lists have actually been inserted to Freenet.
	 * - The message lists returned by this function can be considered as the outbox of the given identity.
	 * 
	 * If you want a list of message lists  which is actually downloadable from Freenet, see {@link getMessageListsBy}.
	 * 
	 * @param author The author of the message lists.
	 * @return All own message lists of the given own identity.
	 */
	protected synchronized ObjectSet<OwnMessageList> getOwnMessageListsBy(final OwnIdentity author) {
		final Query query = db.query();
		query.constrain(OwnMessageList.class);
		query.descend("mAuthor").constrain(author).identity();
		return new Persistent.InitializingObjectSet<OwnMessageList>(mFreetalk, query);
	}
	
	/**
	 * Get a list of all messages from the given identity.
	 * If the identity is an {@link OwnIdentity}, it's own messages are only returned if they have been downloaded as normal messages.
	 * Technically, this means that no objects of class {@link OwnMessage} are returned.
	 * 
	 * The purpose of this behavior is to ensure that own messages are only displayed to the user if they have been successfully inserted.
	 * 
	 * Does not lock the MessageManager, you have to do this while calling the function and parsing the returned list.
	 * 
	 * @param author An identity or own identity.
	 * @return All messages of the given identity except those of class OwnMessage.
	 */
	public ObjectSet<Message> getMessagesBy(final Identity author) {
		final Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mAuthor").constrain(author).identity();
		return new Persistent.InitializingObjectSet<Message>(mFreetalk, query);
	}
	
	/**
	 * Get a list of locally stored own messages of the given identity. 
	 * Locally stored means that only messages of class {@link OwnMessage} are returned.
	 * 
	 * This means that there is no guarantee that the returned messages have actually been inserted to Freenet.
	 * - The messages returned by this function can be considered as the outbox of the given identity.
	 * 
	 * If you want a list of messages which is actually downloadable from Freenet, see {@link getMessagesBy}.
	 * 
	 * @param author The author of the messages.
	 * @return All own messages of the given own identity.
	 */
	public synchronized ObjectSet<OwnMessage> getOwnMessagesBy(final OwnIdentity author) {
		final Query query = db.query();
		query.constrain(OwnMessage.class);
		query.descend("mAuthor").constrain(author).identity();
		return new Persistent.InitializingObjectSet<OwnMessage>(mFreetalk, query);
	}
	
	private ObjectSet<OwnMessageList.OwnMessageReference> getOwnMessages(final Board board) {
		final Query query = db.query();
		query.constrain(OwnMessageList.OwnMessageReference.class);
		query.descend("mBoard").constrain(board).identity();
		return new Persistent.InitializingObjectSet<OwnMessageList.OwnMessageReference>(mFreetalk, query);
	}

	public IdentityManager getIdentityManager() {
		return mIdentityManager;
	}
	
	
	public abstract MessageRating getMessageRating(OwnIdentity rater, Message message) throws NoSuchMessageRatingException;
	
	public abstract ObjectSet<? extends MessageRating> getAllMessageRatings(Message message);
	
	public abstract ObjectSet<? extends MessageRating> getAllMessageRatingsBy(OwnIdentity rater);

	public abstract void deleteMessageRating(final MessageRating rating);

	protected final synchronized IdentityStatistics getIdentityStatistics(final Identity identity) throws NoSuchObjectException {
		final Query query = db.query();
		query.constrain(IdentityStatistics.class);
		query.descend("mIdentity").constrain(identity).identity();
		final ObjectSet<IdentityStatistics> result = new Persistent.InitializingObjectSet<IdentityStatistics>(mFreetalk, query);
		
		switch(result.size()) {
			case 1: return result.next();
			case 0: throw new NoSuchObjectException();
			default: throw new DuplicateElementException("Duplicate IdentityStatistics for " + identity);
		}
	}
	
	/**
	 * Not synchronized because it is typically being used in a transaction anyway - we need to store the created object.
	 */
	protected final IdentityStatistics getOrCreateIdentityStatistics(final Identity identity)  {
		try {
			return getIdentityStatistics(identity);
		} catch(NoSuchObjectException e) {
			IdentityStatistics stats = new IdentityStatistics(identity);
			stats.initializeTransient(mFreetalk);
			return stats;
		}
	}

}
