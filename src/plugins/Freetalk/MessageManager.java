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
import plugins.Freetalk.IdentityManager.NewOwnIdentityCallback;
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
import plugins.Freetalk.tasks.NewBoardTask;
import plugins.Freetalk.tasks.PersistentTaskManager;
import plugins.Freetalk.tasks.SubscribeToAllBoardsTask;

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
public abstract class MessageManager implements PrioRunnable, NewOwnIdentityCallback, IdentityDeletedCallback {

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
	
	public static final int MAXIMAL_MESSAGE_FETCH_RETRY_DELAY_AT_RETRY_COUNT = (int)(Math.log(MAXIMAL_MESSAGE_FETCH_RETRY_DELAY / MINIMAL_MESSAGE_FETCH_RETRY_DELAY) / Math.log(2));
		
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
	
	public static final int MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY_AT_RETRY_COUNT = (int)(Math.log(MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY / MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY) / Math.log(2));
	
	private final TrivialTicker mTicker;
	private final Random mRandom;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(MessageManager.class);
	}
	

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
		
		mIdentityManager.registerNewOwnIdentityCallback(this);
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

	@Override public int getPriority() {
		return NativeThread.MIN_PRIORITY;
	}

	@Override public void run() {
		if(logDEBUG) Logger.debug(this, "Main loop running...");
		
		try {
			// Must be called periodically because they are not called on demand.
			clearExpiredFetchFailedMarkers();
			recheckUnwantedMessages();
			recheckWantedMessages();
		}  finally {
			long sleepTime = THREAD_PERIOD/2 + mRandom.nextInt(THREAD_PERIOD);
			if(logDEBUG) Logger.debug(this, "Sleeping for " + sleepTime/(60*1000) + " minutes.");
			mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), sleepTime, false, true);
		}
		
		if(logDEBUG) Logger.debug(this, "Main loop finished.");
	}
	
	private final Runnable mNewMessageProcessor = new PrioRunnable() {
		@Override public int getPriority() {
			return NativeThread.NORM_PRIORITY;
		}

		@Override public void run() {
			if(logDEBUG) Logger.debug(MessageManager.this, "Processing new messages...");
			
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
		if(logDEBUG) Logger.debug(this, "Scheduling new message processing to be run in " + PROCESS_NEW_MESSAGES_DELAY / (60*1000) + " minutes...");
		if(mTicker != null)
			mTicker.queueTimedJob(mNewMessageProcessor, "Freetalk " + this.getClass().getSimpleName(), PROCESS_NEW_MESSAGES_DELAY, false, true);
		else { // For unit tests
			mNewMessageProcessor.run();
		}
	}
	
	
	public void start() {
		if(logDEBUG) Logger.debug(this, "Starting...");
		
		createDefaultBoards();
		
		long startupDelay = STARTUP_DELAY/2 + mRandom.nextInt(STARTUP_DELAY); 
		if(logDEBUG) Logger.debug(this, "Main loop will run in " + startupDelay/(60*1000) + " minutes.");
		mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), startupDelay, false, true);
		
		// It might happen that Freetalk is shutdown after a message has been downloaded and before addMessagesToBoards was called:
		// Then the message will still be stored but not visible in the boards because storing a message and adding it to boards are separate transactions.
		// Therefore, we must call addMessagesToBoards (and synchronizeSubscribedBoards) during startup.
		scheduleNewMessageProcessing();
		
		if(logDEBUG) Logger.debug(this, "Started.");
	}

	public void terminate() {
		if(logDEBUG) Logger.debug(this, "Stopping ..."); 
		mTicker.shutdown();
		if(logDEBUG) Logger.debug(this, "Stopped.");
	}
	
	/**
	 * This is the primary function for posting messages.
	 * TODO: Optimization: This probably does not require any synchronization when calling since the storeWithoutComit of Message throws if the referenced objects do not exist anymore.
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

	/**
	 * TODO: Optimization: This probably does not require any synchronization when calling since the storeWithoutComit of Message throws if the referenced objects do not exist anymore. 
	 */
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
		q.descend("mWasInserted").constrain(false);
		ObjectSet<OwnMessageList> notInsertedLists = new Persistent.InitializingObjectSet<OwnMessageList>(mFreetalk, q);
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
	
	public void deleteUnsentMessage(String messageID) throws NoSuchMessageException {
		synchronized(mFreetalk.getMessageInserter()) {
		synchronized(this) {
			final OwnMessage message = getOwnMessage(messageID);
			
			if(message.wasInserted())
				throw new UnsupportedOperationException("The message was inserted already");
			
			mFreetalk.getMessageInserter().abortMessageInsert(message.getID());
			
			deleteMessage(message);
		}
		}
	}

	private synchronized void deleteMessage(Message message) {
		if(!(message instanceof OwnMessage)) { // OwnMessages cannot be rated / added to boards.
		
		for(MessageRating rating : getAllMessageRatings(message)) {
			// We must not undo the effect because we do not want message deletion due to distrust of the author result in the distrust to be undone.
			deleteMessageRatingWithoutRevertingEffect(rating); // This call does a full transaction.
		}
		
		for(Board board : message.getBoards()) {
			synchronized(board) {
			synchronized(message) { // TODO: Check whether we actually need to lock messages. I don't think so.
			synchronized(Persistent.transactionLock(db)) {
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
				synchronized(Persistent.transactionLock(db)) {
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
		
		}

		synchronized(message) { // TODO: Check whether we actually need to lock messages. I don't think so.
		synchronized(Persistent.transactionLock(db)) {	
			try {
				if(message instanceof OwnMessage) {
					final OwnMessage ownMessage = (OwnMessage)message;
					
					for(OwnMessageList.OwnMessageReference ref : getAllOwnReferencesToMessage(ownMessage.getID())) {
						((OwnMessageList)ref.getMessageList()).removeMessageWithoutCommit(ownMessage);
					}
					
					// clearParent / clearThread is not neccessary for OwnMessages.
				} else {
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
			
			if(board.getDownloadedMessageCount() != 0) {
				Logger.normal(this, "Not deleting board because getDownloadedMessageCount()!=0: " + board);
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
			
			synchronized(Persistent.transactionLock(db)) {
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
	 * Called by the IdentityManager after a new Identity has been stored to the database and before committing the transaction.
	 * The IdentityManager and PersistentTaskManager are locked when this function is called.
	 * 
	 * Creates a SubscribeToAllBoardsTask for the identity if auto-subscription to boards is enabled.
	 */
	@Override public void onNewOwnIdentityAdded(OwnIdentity identity) {
		if(identity.wantsAutoSubscribeToNewBoards()) {
			// We cannot subscribe to the boards here because we lack the lock to the MessageManager
			mFreetalk.getTaskManager().storeTaskWithoutCommit(new SubscribeToAllBoardsTask(identity));
			mFreetalk.getTaskManager().processTasksSoon();
		}
	}
	
	/**
	 * Called by the {@link IdentityManager} before an identity is deleted from the database.
	 * 
	 * Deletes any messages and message lists referencing to it and commits the transaction.
	 */
	@Override public synchronized void beforeIdentityDeletion(Identity identity) {
		if(logDEBUG) Logger.debug(this, "Deleting all objects of identity " + identity);
		// We use multiple transactions here: We cannot acquire the Persistent.transactionLock(db) before deleteMessageRatting, board.deleteWithoutCommit and
		// deleteMessage. Each of them synchronize on message ratings / boards, therefore we must acquire the db.lock after synchronizing on each object.
		// TODO: Check whether this can result in bad side effects. IMHO it cannot.
		// If it can, add some mechanism similar to addMessagesToBoards()/synchronizeSubscribedBoards which handles half-deleted identities.
		
		if(identity instanceof OwnIdentity) {
			final OwnIdentity ownId = (OwnIdentity)identity;
			for(final MessageRating messageRating : getAllMessageRatingsBy(ownId)) {
				// We must not undo the effect because we do not want message deletion due to distrust of the author result in the distrust to be undone.
				deleteMessageRatingWithoutRevertingEffect(messageRating); // This does a full transaction and commits it.
			}
						
			for(SubscribedBoard board : subscribedBoardIteratorSortedByName((OwnIdentity)identity)) { // TODO: Optimization: Use a non-sorting function.
				unsubscribeFromBoard(ownId, board); // This does a full transaction and commits it.
			}
		}

		for(Message message : getMessagesBy(identity)) {
			deleteMessage(message); // This does a full transaction and commits it.
		}

		synchronized(Persistent.transactionLock(db)) {
			try {
				for(MessageList messageList : getMessageListsBy(identity)) {
					messageList.deleteWithoutCommit();
					// We do not call onMessageListDeleted for the IdentityStatistics since the statistics object will be deleted within
					// this transaction anyway.
				}

				if(identity instanceof OwnIdentity) {
					final OwnIdentity ownId = (OwnIdentity)identity;
					
					// TODO: Make sure that deleteMessage also works well for OwnMessages and use it.
					
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

				if(logDEBUG) Logger.debug(this, "beforeIdentityDeletion finished for " + identity);
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
		synchronized(Persistent.transactionLock(db)) {
			try {
				list.throwIfNotStored();
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
		synchronized(Persistent.transactionLock(db)) {
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
		
		synchronized(Persistent.transactionLock(db)) {
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
				synchronized(Persistent.transactionLock(db)) {
					try {
						if(logDEBUG) Logger.debug(this, "Adding message to board: " + message);
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
				synchronized(Persistent.transactionLock(db)) {
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
			synchronized(Persistent.transactionLock(db)) {
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
		
		// It's not possible to keep the synchronization order of message lists to synchronize BEFORE synchronizing on Persistent.transactionLock(db) some places so we 
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
				if(logDEBUG) Logger.debug(this, "Downloaded a MessageList which we already have: " + list);
				return;
			}

		} catch(NoSuchMessageListException e) {
			ghostList = null;
		}

		synchronized(Persistent.transactionLock(db)) {
				try {
					if(marker != null) {
						// TODO: This is usually an error, but it is no error if the fetched list is the only list which there was a ghost list for (i.e. edition 0)
						// Re-think about the conditions when this is an error and log an error then, not only a warning.
						Logger.warning(this, "MessageList was fetched even though a FetchFailedMarker existed for it! Deleting the marker: " + marker);
						marker.deleteWithoutCommit();
						marker = null;
					}
						
					if(ghostList != null) { // We do not nest it with the above if() for readability / robustness.
						Logger.warning(this, "MessageList was fetched even though a ghost list existed for it! Deleting the ghost list: " + ghostList);
						ghostList.deleteWithoutCommit();
						ghostList = null;
						// We don't call onMessageListDeleted on the IdentityStatistics since we will call onMessageListFetched for the 
						// list with the same ID in this transaction anyway. 
					}
					
					// TODO: Optimization: This is debug code which was added on 2011-02-13 for tracking down 0004739, it can be removed after some months if this does not happen.
					// and/or if the bug tracker entry is marked as fixed
					try {
						getMessageList(list.getID());
						throw new RuntimeException("Duplicate list would have been created, ghostList.deleteWithoutCommit() did not work!");
					} catch(NoSuchMessageListException e) {}
					
					list.storeWithoutCommit();
					
					// Mark existing messages as fetched... Can happen if a message is listed in multiple lists.
					for(MessageReference ref : list) {
						try {
							get(ref.getMessageID());
							ref.setMessageWasDownloadedFlag();
							ref.storeWithoutCommit();
						} catch(NoSuchMessageException e) {}
					}
					
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
			if(logDEBUG) Logger.debug(this, "Trying to mark a message as 'download failed' which we actually have: " + messageReference.getURI());
		}
		catch(NoSuchMessageException e) {
			synchronized(Persistent.transactionLock(db)) {
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
			// TODO: Optimization: Once we are sure that parsing failures do not happen randomly, set retry to a very high delay or limit to 3 retries or so....
			case ParsingFailed:
				// We need this check to prevent overflow causing negative Dates :)
				if(numberOfRetries >= MAXIMAL_MESSAGE_FETCH_RETRY_DELAY_AT_RETRY_COUNT)
					return new Date(now.getTime() + MAXIMAL_MESSAGE_FETCH_RETRY_DELAY);
				
				// Math.min() is just a double check
				return new Date(now.getTime() + Math.min(MINIMAL_MESSAGE_FETCH_RETRY_DELAY * (1<<numberOfRetries), MAXIMAL_MESSAGE_FETCH_RETRY_DELAY));
			default:
				return new Date(now.getTime()  + MINIMAL_MESSAGE_FETCH_RETRY_DELAY);
		}
	}
	
	protected Date calculateDateOfNextMessageListFetchRetry(FetchFailedMarker.Reason reason, Date now, int numberOfRetries) {
		switch(reason) {
			case DataNotFound:
			// TODO: Optimization: Once we are sure that parsing failures do not happen randomly, set retry to a very high delay or limit to 3 retries or so....
			case ParsingFailed:
				// We need this check to prevent overflow causing negative Dates :)
				if(numberOfRetries >= MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY_AT_RETRY_COUNT)
					return new Date(now.getTime() + MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY);
				
				// Math.min() is just a double check
				return new Date(now.getTime()  + Math.min(MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY * (1<<numberOfRetries), MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY));
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
		final ObjectSet<MessageFetchFailedMarker> markers = new Persistent.InitializingObjectSet<MessageList.MessageFetchFailedMarker>(mFreetalk, q);
		
		switch(markers.size()) {
			case 1:
				return markers.next();
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
			synchronized(Persistent.transactionLock(db)) {
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
					
					if(logDEBUG) Logger.debug(this, "Cleared marker " + marker);
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
		
		final Date now = CurrentTimeUTC.get();
		
		for(SubscribedBoard board : subscribedBoardIterator()) {
			board.retryAllUnwantedMessages(now);
		}
		
		Logger.normal(this, "Finished rechecking unwanted message");
	}
	
	/**
	 * Only for being used by the MessageManager itself and by unit tests.
	 * 
	 * Checks whether there are any messages in subscribed boards which the subscriber did want to read and now does not want to read anymore.
	 */
	protected synchronized void recheckWantedMessages() {
		Logger.normal(this, "Rechecking wanted messages...");
		
		final Date now = CurrentTimeUTC.get();
		
		for(SubscribedBoard board : subscribedBoardIterator()) {
			board.validateAllWantedMessages(now);
		}
		
		Logger.normal(this, "Finished rechecking wanted message");
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
	
	private ObjectSet<OwnMessageList.OwnMessageReference> getAllOwnReferencesToMessage(final String id) {
		final Query query = db.query();
		query.constrain(OwnMessageList.OwnMessageReference.class);
		query.descend("mMessageID").constrain(id);
		return new Persistent.InitializingObjectSet<OwnMessageList.OwnMessageReference>(mFreetalk, query);
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
	public Board getOrCreateBoard(String name) throws InvalidParameterException {
		return getOrCreateBoard(name, null);
	}

	private synchronized Board getOrCreateBoard(String name, String description) throws InvalidParameterException {
		name = name.toLowerCase();
		
		Board board;

		try {		
			board = getBoardByName(name);
		}
		catch(NoSuchBoardException e) {
			PersistentTaskManager tm = mFreetalk.getTaskManager();
			synchronized(tm) {
			synchronized(Persistent.transactionLock(db)) {
			try {
				board = new Board(name, description, false);
				board.initializeTransient(mFreetalk);
				board.storeWithoutCommit();
				if(logDEBUG) Logger.debug(this, "Created board " + name);
				board.checkedCommit(this);
				
				tm.storeTaskWithoutCommit(new NewBoardTask(board));
			}
			catch(RuntimeException ex) {
				Persistent.checkedRollbackAndThrow(db, this, ex);
				throw ex; // Satisfy the compiler
			}
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
					synchronized(Persistent.transactionLock(db)) {
						try {
							SubscribedBoard subscribedBoard = new SubscribedBoard(board, subscriber);
							subscribedBoard.initializeTransient(mFreetalk);
							subscribedBoard.storeWithoutCommit();
							subscribedBoard.synchronizeWithoutCommit();
							
							if(board.hasSubscriptions() == false) {
								if(logDEBUG) Logger.debug(this, "First subscription received for board " + board + ", setting it's HasSubscriptions flag.");
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
			synchronized(Persistent.transactionLock(db)) {
				try {
					subscribedBoard.deleteWithoutCommit();
					
					if(subscribedBoardIterator(subscribedBoard.getName()).isEmpty()) {
						try {
							Board board = getBoardByName(subscribedBoard.getName());
							if(logDEBUG) Logger.debug(this, "Last subscription to board " + board + " removed, clearing it's HasSubscriptions flag.");
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
	 * Gets the amount of all downloadable messages. This are the messages which where referenced in any non-own message list.
	 * Messages which are posted to multiple boards are counted multiple times, once for each board.
	 */
	public final synchronized int countNonOwnMessageListMessageReferences() {
		final Query query = db.query();
		query.constrain(MessageList.MessageReference.class);
		query.constrain(OwnMessageList.OwnMessageReference.class).not();
		return query.execute().size();
	}

	public final synchronized int countNonOwnMessageLists() {
		final Query query = db.query();
		query.constrain(MessageList.class);
		query.constrain(OwnMessageList.class).not();
		return query.execute().size();
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

	/**
	 * Deletes the given rating. Does not undo the effect of the rating (trust value changes, etc).
	 * For being used in automatic rating deletion - this usually happens when an identity is deleted due to distrust - we do not want the distrust
	 * which caused the identity deletion to be undone.
	 */
	public abstract void deleteMessageRatingWithoutRevertingEffect(final MessageRating rating);

	/**
	 * Deletes the given rating and reverts its effect (trust value change, etc.)
	 * For being used in the UI - it can fail to revert the effect and throw an exception (due to being disconnected to the web of trust plugin,
	 * due to the trust value of the affected identity having been changed by the user, etc.).
	 */
	public abstract void deleteMessageRatingAndRevertEffect(final MessageRating rating);

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
	
	public final ObjectSet<IdentityStatistics> getAllIdentityStatistics() {
		final Query query = db.query();
		query.constrain(IdentityStatistics.class);
		return new Persistent.InitializingObjectSet<IdentityStatistics>(mFreetalk, query);
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
	
	/**
	 * A list of boards which is automatically created in new Freetalk databases.
	 * The goal of this list is:
	 * - to be useful from the perspective of a user: Names an categories shall be chosen in a way that they make sense to a user, even if a better naming for developers exists
	 * - to prevent the board list from becoming a mess: It has been shown on Frost/FMS that it is really difficult to get users to migrate from existing boards to new boards
	 * 		which serve the same purpose but have a different name. We try to provide proper categories for the most purposes which have been observed on Frost/FMS.
	 * - to encourage categorization: This is part of the above, but I want to stress it again nevertheless: FMS/Frost has also shown that users tend to not use any categories
	 * 		( = the parts between the '.') when creating boards. Categories are good for filtering, usability, blah blah. We should encourage their usage.
	 * 
	 * I have tried to take all boards which can be seen on Frost and FMS into consideration for designing this clean list
	 */
	public static final String[][] DEFAULT_BOARDS = {
		new String[] { "deu.diskussion", "Offene Diskussion über alle Themen, für die es kein spezielles Forum gibt" },
		
		new String[] { "deu.downloads", "Downloads aller Art, für die es kein spezielles Forum gibt. Bitte respektieren Sie die Gesetze!" },
		new String[] { "deu.downloads.anfragen", "Anfragen für Downloads aller Art, für die es kein spezielles Forum gibt. Bitte respektieren Sie die Gesetze!" },
		
		new String[] { "deu.freenet", "Allgemeine Diskussion rund um Freenet" },
		new String[] { "deu.freenet.freetalk", "Diskussion über Freetalk - das ist dieses Forumsystem"},
		new String[] { "deu.freenet.jsite", "Diskussion über JSite - das Werkzeug zum Hochladen von Freesites" },
		new String[] { "deu.freenet.sone", "Diskussion über Sone - dem sozialen Netzwerk für Freenet" },
		new String[] { "deu.freenet.weboftrust", "Diskussion über Web Of Trust - dem Kern der meisten Freenet-Community-Plugins" },
		new String[] { "deu.freenet.seiten", "Diskussion über Freesites - die Websites in Freenet. Hier kann man seine Freesite der Öffentlichkeit und insbesondere Index-Verwaltern bekannt machen." },
		
		new String[] { "deu.nachrichten.international", "Internationale Nachrichten"},
		new String[] { "deu.gesellschaft.politik.international", "Diskussion über internationale Politik"},
		
		new String[] { "eng.boards", "Discussion about the board list and announcement of new boards" },
		
		new String[] { "eng.computers", "General discussion about computer-related topics"},
		new String[] { "eng.computers.help.hardware", "Questions about computer hardware"},
		new String[] { "eng.computers.help.software", "Questions about computer software"},
		new String[] { "eng.computers.programming", "General discussion about software programming"},
		
		new String[] { "eng.discussion", "General discussion about all topics for which there is no special board" },
		
		new String[] { "eng.freenet", "General discussion about Freenet" },
		new String[] { "eng.freenet.fms", "Discussion about FMS - the standalone (non-plugin) forum system for Freenet"},
		new String[] { "eng.freenet.freemail", "Discussion about Freemail - the E-Mail implementation for Freenet"},
		new String[] { "eng.freenet.freetalk", "Discussion about Freetalk - which is this forum system"},
		new String[] { "eng.freenet.fuqid", "Discussion about Fuqid - a file-transfer management tool for Freenet"},
		new String[] { "eng.freenet.jsite", "Discussion about JSite - the tool for uploading Freesites"},
		new String[] { "eng.freenet.sone", "Discussion about Sone - the social messagging tool for Freenet"},
		new String[] { "eng.freenet.thaw", "Discussion about Thaw - a file-transfer management tool for Freenet"},
		new String[] { "eng.freenet.translation", "Discussion about translating Freenet or the existing translations"},
		new String[] { "eng.freenet.weboftrust", "Discussion about Web Of Trust - the core of most community plugins for Freenet"},
		new String[] { "eng.freenet.sites", "Discussion about Freesites - the websites of Freenet. You can announce Freesites to the public and especially index-maintainers here."},
		
		new String[] { "eng.internet", "Discussion about the 'normal' (non-Freenet) Internet" },
		new String[] { "eng.internet.sites", "A board about interesting links of all kinds on the 'normal' (non-Freenet) internet" },
		
		new String[] { "eng.news.international", "International news"},
		
		new String[] { "eng.market", "Trading of various goods happens here. Please obey the law."},
		
		new String[] { "eng.media.tv", "Discussion about television" },
		
		new String[] { "eng.science", "Discussion about science"},
		new String[] { "eng.science.mathematics", "Discussion about mathematics"},
		new String[] { "eng.society.censorship", "Discussion and revelations of censorship"},
		new String[] { "eng.society.politics.international", "Discussion of international politics"},
		new String[] { "eng.society.privacy", "Discussion about privacy"},
		new String[] { "eng.society.religion", "Discussion about religion"},
		
		new String[] { "eng.trustvalues", "Discussion about the trust values which the Web Of Trust community has assigned to identities"},
		
		new String[] { "fra.freenet", "Discussions générales à propos de Freenet"},
		
		new String[] { "mul.downloads", "All kinds of downloads for which there is no special board. Please obey the law!"},
		new String[] { "mul.downloads.books", "Downloads of written books. Please obey the law!"},
		new String[] { "mul.downloads.books.audio", "Downloads of audible books. Please obey the law!"},
		new String[] { "mul.downloads.books.comics", "Downloads of comic books. Please obey the law!"},
		new String[] { "mul.downloads.games", "Downloads of games. Please obey the law!"},
		new String[] { "mul.downloads.movies", "Downloads of movies. Please obey the law!"},
		new String[] { "mul.downloads.music", "Downloads of music. Please obey the law!"},
		new String[] { "mul.downloads.music.videos", "Downloads of music with video. Please obey the law!"},
		new String[] { "mul.downloads.pictures", "Downloads of pictures. Please obey the law!"},
		new String[] { "mul.downloads.videos", "Video-downloads which do not fit in any category. Please obey the law!"},
		new String[] { "mul.downloads.videos.series", "Downloads of series of videos. Please obey the law!"},
		new String[] { "mul.downloads.requests", "You can ask for download links here. To ensure a large audience, all categories of downloads are allowed here. Please obey the law!"},
		new String[] { "mul.downloads.requests.reinserts", "If a download does not succeed anymore, you can request someone to upload it again here. Please obey the law!"},

		new String[] { "mul.random", "All content is allowed in this 'playground' board. Please try to not give negative ratings for its messages whenever possible by your law and ethics." },

		new String[] { "mul.test", "Board for sending test messages to. Readers shall try to ensure that each message gets a reply." },
	};
	
    public synchronized void createDefaultBoards() {
    	Logger.normal(this, "Creating the default boards...");
    
    	for(String[] boardInfo : DEFAULT_BOARDS) {
    		try {
    			try {
    				final Board existingBoard = getBoardByName(boardInfo[0]);
    				synchronized(Persistent.transactionLock(db)) {
    					try {
    						if(existingBoard.setDescription(boardInfo[1])) {
    							if(logDEBUG) Logger.debug(this, "Updated description for " + existingBoard);
    							existingBoard.storeWithoutCommit();
    							Persistent.checkedCommit(db, this);
    						}
    					} catch(RuntimeException e) {
    						Persistent.checkedRollback(db, this, e);
    					}
    				}
    			} catch(NoSuchBoardException e) {
    				getOrCreateBoard(boardInfo[0], boardInfo[1]);
    			}
    		} catch(Exception e) {
    			Logger.error(this, "Creating a board failed", e);
    		}
    	}
    	
    	Logger.normal(this, "Finished creating the default boards.");
    }

}
