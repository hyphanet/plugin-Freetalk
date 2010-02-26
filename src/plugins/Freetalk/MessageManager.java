/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.MessageList.MessageFetchFailedMarker;
import plugins.Freetalk.MessageList.MessageListFetchFailedMarker;
import plugins.Freetalk.MessageList.MessageReference;
import plugins.Freetalk.exceptions.DuplicateBoardException;
import plugins.Freetalk.exceptions.DuplicateFetchFailedMarkerException;
import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.DuplicateMessageListException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchFetchFailedMarkerException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;

/**
 * The MessageManager is the core connection between the UI and the backend of the plugin:
 * It is the entry point for posting messages, obtaining messages, obtaining boards, etc.
 * 
 * 
 * @author xor (xor@freenetproject.org)
 */
public abstract class MessageManager implements Runnable {

	protected final IdentityManager mIdentityManager;
	
	protected final Freetalk mFreetalk;
	
	protected final ExtObjectContainer db;
	
	protected final PluginRespirator mPluginRespirator;
	

	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	
	private static final int STARTUP_DELAY = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) : (3 * 60 * 1000);
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) : (15 * 60 * 1000);
	
	// FIXME: Adjust these before release:
	
	public static final long MINIMAL_MESSAGE_FETCH_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) :  (1 * 24 * 60 * 60 * 1000); // TODO: Make configurable.
	public static final long MAXIMAL_MESSAGE_FETCH_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) : (7 * 24 * 60 *60 * 1000); // TODO: Make configurable
	public static final long MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) : (1 * 24 * 60 * 60 * 1000); // TODO: Make configurable.
	public static final long MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) : (7 * 24 * 60 * 60 * 1000); 
	
	private volatile boolean isRunning = false;
	private volatile boolean shutdownFinished = false;
	private Thread mThread;

	public MessageManager(ExtObjectContainer myDB, IdentityManager myIdentityManager, Freetalk myFreetalk, PluginRespirator myPluginRespirator) {
		assert(myDB != null);
		assert(myIdentityManager != null);
		assert(myFreetalk != null);
		assert(myPluginRespirator != null);		
		
		db = myDB;
		mIdentityManager = myIdentityManager;
		mFreetalk = myFreetalk;
		mPluginRespirator = myPluginRespirator;
		
		deleteBrokenObjects();
		
		// It might happen that Freetalk is shutdown after a message has been downloaded and before addMessagesToBoards was called:
		// Then the message will still be stored but not visible in the boards because storing a message and adding it to boards are separate transactions.
		// Therefore, we must call addMessagesToBoards (and synchronizeSubscribedBoards) during startup.
		addMessagesToBoards();
		synchronizeSubscribedBoards();
	}
	
	/**
	 * For being used in JUnit tests to run without a node.
	 */
	protected MessageManager(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
		db = mFreetalk.getDatabase();
		mIdentityManager = mFreetalk.getIdentityManager();
		mPluginRespirator = null;
	}
	
	/**
	 * Called during startup to delete objects from the database which lack required information, such as messages with mAuthor == null.
	 * This is only a workaround until we find the reason of their existence.
	 */
	@SuppressWarnings("unchecked")
	private synchronized void deleteBrokenObjects() {
		Logger.debug(this, "Looking for broken Message objects...");
		Query q = db.query();
		q.constrain(Message.class);
		q.descend("mAuthor").constrain(null).identity();
	
		for(Message message : (ObjectSet<Message>) q.execute()) {
			try {
				synchronized(message) {
				synchronized(db.lock()) {
					message.initializeTransient(mFreetalk);
					Logger.error(this, "Deleting Message with mAuthor == null: " + message);
					message.deleteWithoutCommit();
					message.checkedCommit(this);
				}
				}
			} catch(Exception e) {
				Persistent.checkedRollback(db, this, e);
			}
		}
		
		Logger.debug(this, "Finished looking for broken Message objects.");
		
		Logger.debug(this, "Looking for broken MessageList objects...");
		q = db.query();
		q.constrain(MessageList.class);
		q.descend("mAuthor").constrain(null).identity();
		
		for(MessageList list : (ObjectSet<MessageList>) q.execute()) {
			try {
				list.initializeTransient(mFreetalk);
				Logger.error(this, "Deleting MessageList with mAuthor == null: " + list);
				list.deleteWithoutCommit();
				list.checkedCommit(this);
			} catch(Exception e) {
				Persistent.checkedRollback(db, this, e);
			}
		}
		Logger.debug(this, "Finished looking for broken MessageList objects.");
	}
	
	public void run() {
		Logger.debug(this, "Message manager started.");
		mThread = Thread.currentThread();
		isRunning = true;
		
		Random random = mPluginRespirator.getNode().fastWeakRandom;
		
		try {
			Logger.debug(this, "Waiting for the node to start up...");
			Thread.sleep(STARTUP_DELAY/2 + random.nextInt(STARTUP_DELAY));
		}
		catch (InterruptedException e)
		{
			mThread.interrupt();
		}
		
		try {
			while(isRunning) {
				Logger.debug(this, "Message manager loop running...");
				
				// Must be called periodically because it is not called on demand.
				clearExpiredFetchFailedMarkers();
				
				// Is called on demand, normally does not fail, but we call it to make sure.
				addMessagesToBoards();
				
				// Is called on demand and CAN fail because SubscribedBoard.addeMessage() tries to query the Score of the author from WoT and this can
				// fail due to connectivity issues (and currently most likely due to bugs in PluginTalker and especially BlockingPluginTalker!)
				synchronizeSubscribedBoards();
				
				Logger.debug(this, "Message manager loop finished.");

				try {
					Thread.sleep(THREAD_PERIOD/2 + random.nextInt(THREAD_PERIOD));  // TODO: Maybe use a Ticker implementation instead?
				}
				catch (InterruptedException e)
				{
					mThread.interrupt();
					Logger.debug(this, "Message manager loop interrupted!");
				}
			}
		}
		
		finally {
			synchronized (this) {
				shutdownFinished = true;
				Logger.debug(this, "Message manager thread exiting.");
				notify();
			}
		}
	}
	
	public void start() {
		mPluginRespirator.getNode().executor.execute(this, "Freetalk " + this.getClass().getName());
		Logger.debug(this, "Started.");
	}

	public void terminate() {
		Logger.debug(this, "Stopping ..."); 
		isRunning = false;
		mThread.interrupt();
		synchronized(this) {
			while(!shutdownFinished) {
				try {
					wait();
				}
				catch (InterruptedException e) {
					Thread.interrupted();
				}
			}
		}
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
	public abstract OwnMessage postMessage(MessageURI myParentThreadURI, Message myParentMessage, Set<Board> myBoards, Board myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, Date myDate, String myText, List<Attachment> myAttachments) throws InvalidParameterException, Exception;

	public OwnMessage postMessage(MessageURI myParentThreadURI, Message myParentMessage, Set<String> myBoards, String myReplyToBoard,
			FTOwnIdentity myAuthor, String myTitle, Date myDate, String myText, List<Attachment> myAttachments) throws Exception {

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
		q.descend("mRealURI").constrain(null).identity();
		int unsentCount = q.execute().size();
		
		q = db.query();
		q.constrain(OwnMessageList.class);
		q.descend("iWasInserted").constrain(false);
		ObjectSet<OwnMessageList> notInsertedLists = q.execute();
		for(OwnMessageList list : notInsertedLists)
			unsentCount += list.getMessageCount();
		
		return unsentCount;
	}
	
	/**
	 * Called by the {@link IdentityManager} before an identity is deleted from the database.
	 * 
	 * Deletes any messages and message lists referencing to it and commits the transaction.
	 */
	public synchronized void onIdentityDeletion(FTIdentity identity) {

		// We use multiple transactions here: We cannot acquire the db.lock() before board.deleteMessage because deleteMessage() synchronizes on board
		// and therefore we must acquire the db.lock after synchronizing on each board.
		// So the FIXME is: Add some mechanism similar to addMessagesToBoards()/synchronizeSubscribedBoards which handles half-deleted identities.
				
				for(Message message : getMessagesBy(identity)) {
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
								db.commit(); Logger.debug(this, "COMMITED.");
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
							}
							
							message.deleteWithoutCommit();
							
							db.commit(); Logger.debug(this, "COMMITED.");
						}
						catch(RuntimeException e) {
							Persistent.checkedRollbackAndThrow(db, this, e);
						}
					}
					}
				}

		synchronized(db.lock()) {
			try {
				for(MessageList messageList : getMessageListsBy(identity)) {
					messageList.deleteWithoutCommit();
				}

				if(identity instanceof FTOwnIdentity) {
					for(OwnMessage message : getOwnMessagesBy((FTOwnIdentity)identity)) {
						message.deleteWithoutCommit();
					}

					for(OwnMessageList messageList : getOwnMessageListsBy((FTOwnIdentity)identity)) {
						messageList.deleteWithoutCommit();
					}
					
					// FIXME: We do not lock the boards. Ensure that the UI cannot re-use the board by calling storeAndCommit somewhere even though the board
					// has been deleted. This can be ensured by having isStored() checks in all storeAndCommit() functions which use boards.
					
					for(SubscribedBoard board : subscribedBoardIteratorSortedByName((FTOwnIdentity)identity)) // TODO: Optimization: Use a non-sorting function.
						board.deleteWithoutCommit();
				}
				Logger.debug(this, "Messages and message lists deleted for " + identity);
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
				OwnMessageList list = getOwnMessageList(MessageList.getIDFromURI(uri));
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
		try {
			get(message.getID());
			Logger.debug(this, "Downloaded a message which we already have: " + message.getURI());
		}
		catch(NoSuchMessageException e) {
			
			synchronized(db.lock()) {
				try {
					message.initializeTransient(mFreetalk);
					message.storeWithoutCommit();

					for(MessageReference ref : getAllReferencesToMessage(message.getID())) {
						try {
							getMessageFetchFailedMarker(ref).deleteWithoutCommit();
							Logger.normal(this, "Deleted a FetchFailedMarker for the message.");
						} catch(NoSuchFetchFailedMarkerException e1) { }
						
						ref.setMessageWasDownloadedFlag();
					}

					message.checkedCommit(this);
				}
				catch(Exception ex) {
					Persistent.checkedRollback(db, this, e);
				}
			}
			
			// TODO: Instead of calling it immediately, schedule it to be executed in a few seconds. So if we receive a bunch of messages at once, they'll
			// be bulk-added.
			if(addMessagesToBoards())
				synchronizeSubscribedBoards();
		}
	}
	
	/**
	 * 
	 * @return True if there was at least one message which was linked in. False if no new messages were discovered.
	 */
	@SuppressWarnings("unchecked")
	private synchronized boolean addMessagesToBoards() {
		Logger.normal(this, "Adding messages to boards...");
		
		Query q = db.query();
		q.constrain(Message.class);
		q.descend("mWasLinkedIn").constrain(false);
		q.constrain(OwnMessage.class).not();
		ObjectSet<Message> invisibleMessages = q.execute();
		
		boolean addedMessages = false;
		
		for(Message message : invisibleMessages) {
			message.initializeTransient(mFreetalk);
			
			boolean allSuccessful = true;
			
			for(Board board : message.getBoards()) {
				synchronized(board) {
				synchronized(message) {
				synchronized(db.lock()) {
					try {
						Logger.debug(this, "Adding message to board: " + message);
						board.addMessage(message);
						board.checkedCommit(this);
						addedMessages = true;
					}
					catch(Exception e) {
						allSuccessful = false;
						Persistent.checkedRollback(db, this, e);			
					}
				}
				}
				}
			}
			
			if(allSuccessful) {
				synchronized(message) {
				synchronized(db.lock()) {
					message.setLinkedIn(true);
					message.storeAndCommit();
				}
				}
			}
		}
		
		Logger.normal(this, "Finished adding messages to boards.");
		
		return addedMessages;
	}
	
	private synchronized void synchronizeSubscribedBoards() {
		Logger.normal(this, "Synchronizing subscribed boards...");
		
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
					Persistent.checkedRollback(db, this, e);
				}
			}
			}
			// }
			
			Thread.yield();
		}
		
		Logger.normal(this, "Finished synchronizing subscribed boards.");
	}
	
	public synchronized void onMessageListReceived(MessageList list) {
		list.initializeTransient(mFreetalk);
		
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
						}
					}
					
					list.storeWithoutCommit();
					list.checkedCommit(this);
				}
				catch(RuntimeException ex) {
					Persistent.checkedRollback(db, this, ex);
				}
		}
	}
	
	/**
	 * Abstract because we need to store an object of a child class of MessageList which is chosen dependent on which implementation of the
	 * messaging system we are using.
	 */
	public abstract void onMessageListFetchFailed(FTIdentity author, FreenetURI uri, FetchFailedMarker.Reason reason);
	
	public synchronized void onMessageFetchFailed(MessageReference messageReference, FetchFailedMarker.Reason reason) {
		try {
			get(messageReference.getMessageID());
			Logger.debug(this, "Trying to mark a message as 'downlod failed' which we actually have: " + messageReference.getURI());
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
					
					failedMarker.storeWithoutCommit();
				
					ref.setMessageWasDownloadedFlag();
					
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
	
	@SuppressWarnings("unchecked")
	private ObjectSet<FetchFailedMarker> getFetchFailedMarkers(final Date now) {
		final Query query = db.query();
		query.constrain(FetchFailedMarker.class);
		query.descend("mDateOfNextRetry").constrain(now).greater().not();
		return new Persistent.InitializingObjectSet<FetchFailedMarker>(mFreetalk, query.execute());
	}
	
	private MessageFetchFailedMarker getMessageFetchFailedMarker(final MessageReference ref) throws NoSuchFetchFailedMarkerException {
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
		
		for(FetchFailedMarker marker : getFetchFailedMarkers(now)) {
			synchronized(db.lock()) {
				try {
					if(marker instanceof MessageFetchFailedMarker) {
						MessageFetchFailedMarker m = (MessageFetchFailedMarker)marker;
						MessageReference ref = m.getMessageReference();
						ref.clearMessageWasDownloadedFlag();
					} else if(marker instanceof MessageListFetchFailedMarker) {
						MessageListFetchFailedMarker m = (MessageListFetchFailedMarker)marker;
						try {
							getMessageList(m.getMessageListID()).deleteWithoutCommit();
							m.storeWithoutCommit(); // MessageList.deleteWithoutCommit deletes it.
						}
						catch(NoSuchMessageListException e) {
							// The marker was already processed.
						}
					} else
						Logger.error(this, "Unknown FetchFailedMarker type: " + marker);
					
					++amount;
					
					Logger.debug(this, "Cleared marker " + marker);
					marker.checkedCommit(this);
				}
				catch(RuntimeException e) {
					Persistent.checkedRollback(db, this, e);
				}
			}
		}
		
		Logger.normal(this, "Finished clearing " + amount + " expired FetchFailedMarkers.");
		
		// FIXME: Remove before release

		Query q = db.query();
		q.constrain(MessageListFetchFailedMarker.class);
		q.descend("mDateOfNextRetry").constrain(now).greater();
		@SuppressWarnings("unchecked")
		ObjectSet<MessageListFetchFailedMarker> markers = q.execute();
		
		for(MessageListFetchFailedMarker marker : markers) {
			try {
				getMessageList(marker.getMessageListID());
			} catch(NoSuchMessageListException e) {
				Logger.error(this, "Invalid MessageListFetchFailedMarker: Date of next retry is in future but there is no ghost message list for it: " + marker);
			}
		}
		
		Logger.normal(this, "Number of non-expired MessageListFetchFailedMarker: " + markers.size());
		
		q = db.query();
		q.constrain(MessageFetchFailedMarker.class);
		q.descend("mDateOfNextRetry").constrain(now).greater();
		
		Logger.normal(this, "Number of non-expired MessageFetchFailedMarker: " + q.execute().size());
	}
	
	/**
	 * Get a list of all MessageReference objects to the given message ID. References to OwnMessage are not returned.
	 * Used to mark the references to a message which was downloaded as downloaded.
	 */
	@SuppressWarnings("unchecked")
	private ObjectSet<MessageList.MessageReference> getAllReferencesToMessage(final String id) {
		final Query query = db.query();
		query.constrain(MessageList.MessageReference.class);
		query.constrain(OwnMessageList.OwnMessageReference.class).not();
		query.descend("mMessageID").constrain(id);
		return new Persistent.InitializingObjectSet<MessageList.MessageReference>(mFreetalk, query.execute());
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
	@SuppressWarnings("unchecked")
	public ObjectSet<Board> boardIteratorSortedByName() {
		final Query query = db.query();
		query.constrain(Board.class);
		query.constrain(SubscribedBoard.class).not();
		query.descend("mName").orderAscending();
		return new Persistent.InitializingObjectSet<Board>(mFreetalk, query.execute());
	}
	
	/**
	 * Get all boards which are being subscribed to by at least one {@link FTOwnIdentity}, i.e. the boards from which we should download messages.
	 */
	@SuppressWarnings("unchecked")
	public synchronized ObjectSet<Board> boardWithSubscriptionsIterator() {
		final Query query = db.query();
		query.constrain(Board.class);
		query.descend("mHasSubscriptions").constrain(true);
		return new Persistent.InitializingObjectSet<Board>(mFreetalk, query.execute());
	}
	
	@SuppressWarnings("unchecked")
	public synchronized ObjectSet<SubscribedBoard> subscribedBoardIterator() {
		final Query query = db.query();
		query.constrain(SubscribedBoard.class);
		return new Persistent.InitializingObjectSet<SubscribedBoard>(mFreetalk, query.execute());
	}
	/**
	 * Get an iterator of boards which were first seen after the given Date, sorted ascending by the date they were first seen at.
	 */
	@SuppressWarnings("unchecked")
	public synchronized ObjectSet<SubscribedBoard> subscribedBoardIteratorSortedByDate(final FTOwnIdentity subscriber, final Date seenAfter) {
		final Query query = db.query();
		query.constrain(SubscribedBoard.class);
		query.descend("mFirstSeenDate").constrain(seenAfter).greater();
		query.descend("mFirstSeenDate").orderAscending();
		return new Persistent.InitializingObjectSet<SubscribedBoard>(mFreetalk, query.execute());
	}
	
	/**
	 * Get a list of all subscribed boards of the given identity. The list is sorted ascending by name.
	 * 
	 * You have to synchronize on this MessageManager before calling this function and while processing the returned list.
	 * 
	 * The transient fields of the returned objects will be initialized already. 
	 */
	@SuppressWarnings("unchecked")
	public ObjectSet<SubscribedBoard> subscribedBoardIteratorSortedByName(final FTOwnIdentity subscriber) {
		final Query query = db.query();
		query.constrain(SubscribedBoard.class);
		query.descend("mSubscriber").constrain(subscriber).identity();
		query.descend("mName").orderAscending();
		return new Persistent.InitializingObjectSet<SubscribedBoard>(mFreetalk, query.execute());
	}
	
	@SuppressWarnings("unchecked")
	private ObjectSet<SubscribedBoard> subscribedBoardIterator(String boardName) {
		boardName = boardName.toLowerCase();
		
    	final Query q = db.query();
    	q.constrain(SubscribedBoard.class);
    	q.descend("mName").constrain(boardName);
    	return new Persistent.InitializingObjectSet<SubscribedBoard>(mFreetalk, q.execute());
    }
	
    @SuppressWarnings("unchecked")
	public synchronized SubscribedBoard getSubscription(final FTOwnIdentity subscriber, String boardName) throws NoSuchBoardException {
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
	public SubscribedBoard subscribeToBoard(FTOwnIdentity subscriber, String boardName) throws InvalidParameterException, NoSuchIdentityException, NoSuchBoardException {
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
	
	/**
	 * You do NOT need to synchronize on the IdentityManager when calling this function. 
	 */
	public void unsubscribeFromBoard(FTOwnIdentity subscriber, String boardName) throws NoSuchBoardException, NoSuchIdentityException {
		synchronized(mIdentityManager) {
		subscriber = mIdentityManager.getOwnIdentity(subscriber.getID()); // Ensure that the identity still exists so the caller does not have to synchronize.
			
		synchronized(this) {		
		SubscribedBoard subscribedBoard = getSubscription(subscriber, boardName);
		
		synchronized(subscribedBoard) {
		synchronized(db.lock()) {
			try {
				subscribedBoard.deleteWithoutCommit();
				
				if(subscribedBoardIterator(subscribedBoard.getName()).iterator().hasNext() == false) {
					Board board = getBoardByName(subscribedBoard.getName());
					Logger.debug(this, "Last subscription to board " + board + " removed, clearing it's HasSubscriptions flag.");
					board.setHasSubscriptions(false);
					board.storeWithoutCommit();
				}
				
				subscribedBoard.checkedCommit(this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
		}
		}
		}
	}
	
	/**
	 * Get the next index of which a message from the selected identity is not stored.
	 */
//	public int getUnavailableMessageIndex(FTIdentity messageAuthor) {
//		Query q = db.query();
//		q.constrain(Message.class);
//		q.constrain(OwnMessage.class).not(); /* We also download our own message. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
//		q.descend("mAuthor").constrain(messageAuthor);
//		q.descend("mIndex").orderDescending(); /* FIXME: Write a native db4o query which just looks for the maximum! */
//		ObjectSet<Message> result = q.execute();
//		
//		return result.size() > 0 ? result.next().getIndex()+1 : 0;
//	}
	
	@SuppressWarnings("unchecked")
	public synchronized ObjectSet<OwnMessage> notInsertedMessageIterator() {
		final Query query = db.query();
		query.constrain(OwnMessage.class);
		query.descend("mRealURI").constrain(null).identity();
		return new Persistent.InitializingObjectSet<OwnMessage>(mFreetalk, query.execute());

	}
	
	/**
	 * Get a list of not downloaded messages. This function only returns messages which are posted to a board which an OwnIdentity wants to
	 * receive messages from. However, it might also return messages which are from an author which nobody wants to receive messages from.
	 * Filtering out unwanted authors is done at MessageList-level: MessageLists are only downloaded from identities which we want to read
	 * messages from.
	 */
	@SuppressWarnings("unchecked")
	public synchronized ObjectSet<MessageList.MessageReference> notDownloadedMessageIterator() {
		// TODO: This query is very slow!
		final Query query = db.query();
		query.constrain(MessageList.MessageReference.class);
		query.constrain(OwnMessageList.OwnMessageReference.class).not();
		query.descend("mBoard").descend("mHasSubscriptions").constrain(true);
		query.descend("iWasDownloaded").constrain(false);
		/* FIXME: Order the message references randomly with some trick. */
		return new Persistent.InitializingObjectSet<MessageList.MessageReference>(mFreetalk, query.execute());		
	}


	/**
	 * Get a list of all message lists from the given identity.
	 * If the identity is an {@link FTOwnIdentity}, it's own message lists are only returned if they have been downloaded as normal message lists.
	 * Technically, this means that no objects of class {@link OwnMessageList} are returned.
	 * 
	 * The purpose of this behavior is to ensure that own messages are only displayed to the user if they have been successfully inserted.
	 * 
	 * @param author An identity or own identity.
	 * @return All message lists of the given identity except those of class OwnMessageList.
	 */
	@SuppressWarnings("unchecked")
	protected synchronized ObjectSet<MessageList> getMessageListsBy(final FTIdentity author) {
		final Query query = db.query();
		query.constrain(MessageList.class);
		query.constrain(OwnMessageList.class).not();
		query.descend("mAuthor").constrain(author).identity();
		return new Persistent.InitializingObjectSet<MessageList>(mFreetalk, query.execute());
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
	@SuppressWarnings("unchecked")
	protected synchronized ObjectSet<OwnMessageList> getOwnMessageListsBy(final FTOwnIdentity author) {
		final Query query = db.query();
		query.constrain(OwnMessageList.class);
		query.descend("mAuthor").constrain(author).identity();
		return new Persistent.InitializingObjectSet<OwnMessageList>(mFreetalk, query.execute());
	}
	
	
	/**
	 * Get a list of all messages from the given identity.
	 * If the identity is an {@link FTOwnIdentity}, it's own messages are only returned if they have been downloaded as normal messages.
	 * Technically, this means that no objects of class {@link OwnMessage} are returned.
	 * 
	 * The purpose of this behavior is to ensure that own messages are only displayed to the user if they have been successfully inserted.
	 * 
	 * Does not lock the MessageManager, you have to do this while calling the function and parsing the returned list.
	 * 
	 * @param author An identity or own identity.
	 * @return All messages of the given identity except those of class OwnMessage.
	 */
	@SuppressWarnings("unchecked")
	public ObjectSet<Message> getMessagesBy(final FTIdentity author) {
		final Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mAuthor").constrain(author).identity();
		return new Persistent.InitializingObjectSet<Message>(mFreetalk, query.execute());
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
	@SuppressWarnings("unchecked")
	public synchronized ObjectSet<OwnMessage> getOwnMessagesBy(final FTOwnIdentity author) {
		final Query query = db.query();
		query.constrain(OwnMessage.class);
		query.descend("mAuthor").constrain(author).identity();
		return new Persistent.InitializingObjectSet<OwnMessage>(mFreetalk, query.execute());
	}

	public IdentityManager getIdentityManager() {
		return mIdentityManager;
	}

}
