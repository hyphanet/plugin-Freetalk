/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Board;
import plugins.Freetalk.FetchFailedMarker;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.IdentityStatistics;
import plugins.Freetalk.Message;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageList.MessageListID;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.MessageRating;
import plugins.Freetalk.MessageURI;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.OwnMessageList;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.Persistent.InitializingObjectSet;
import plugins.Freetalk.exceptions.DuplicateElementException;
import plugins.Freetalk.exceptions.NoSuchFetchFailedMarkerException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import plugins.Freetalk.exceptions.NoSuchMessageRatingException;
import plugins.Freetalk.exceptions.NoSuchObjectException;
import plugins.Freetalk.tasks.PersistentTaskManager;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;

public final class WoTMessageManager extends MessageManager {
	
	/** One for all requests for WoTMessage*, for fairness. */
	final RequestClient mRequestClient;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(WoTMessageManager.class);
	}
	

	public WoTMessageManager(ExtObjectContainer myDB, IdentityManager myIdentityManager, Freetalk myFreetalk, PluginRespirator myPluginRespirator) {
		super(myDB, myIdentityManager, myFreetalk, myPluginRespirator);
		
		mRequestClient = new RequestClient() {

			@Override public boolean persistent() {
				return false;
			}

			@Override public void removeFrom(ObjectContainer container) {
				throw new UnsupportedOperationException();
			}

			@Override public boolean realTimeFlag() {
				return false; // We want throughput.
			}
			
		};;
	}

	/**
	 * For being used in JUnit tests to run without a node.
	 */
	public WoTMessageManager(Freetalk myFreetalk) {
		super(myFreetalk);
		mRequestClient = null;
	}
	
	/**
	 * Only for being used by the MessageManager itself and by unit tests.
	 */
	@Override protected synchronized void clearExpiredFetchFailedMarkers() {
		super.clearExpiredFetchFailedMarkers();
	}

	@Override public WoTOwnMessage postMessage(
			MessageURI myParentThreadURI, Message myParentMessage, Set<Board> myBoards,
			Board myReplyToBoard, OwnIdentity myAuthor, String myTitle, Date myDate, String myText,
			List<Attachment> myAttachments) throws Exception {
		
		WoTOwnMessage m;
		
		if(myParentThreadURI != null && !(myParentThreadURI instanceof WoTMessageURI))
			throw new IllegalArgumentException("Parent thread URI is no WoTMessageURI: " + myParentThreadURI);
		
		if(myParentMessage != null && !(myParentMessage instanceof WoTMessage))
			throw new IllegalArgumentException("Parent message is no WoTMessage");
		
		if(!(myAuthor instanceof WoTOwnIdentity))
			throw new IllegalArgumentException("Author is no WoTOwnIdentity");

		Date date = myDate!=null ? myDate : CurrentTimeUTC.get();
		m = WoTOwnMessage.construct(mFreetalk, (WoTMessageURI)myParentThreadURI, myParentMessage, myBoards, myReplyToBoard, myAuthor, myTitle, date, myText, myAttachments);
		m.initializeTransient(mFreetalk);
		synchronized(this) {
			m.storeAndCommit();
		}

		if(mFreetalk != null) {
			PersistentTaskManager taskManager = mFreetalk.getTaskManager();
			if(taskManager != null)
				taskManager.onOwnMessagePosted(m);
		}
		
		/* We do not add the message to the boards it is posted to because the user should only see the message if it has been downloaded
		 * successfully. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
		
		return m;
	}
	
	@Override
	public synchronized void onMessageListInsertFailed(FreenetURI uri,boolean collision) throws NoSuchMessageListException {
		synchronized(Persistent.transactionLock(db)) {
			try {
				WoTOwnMessageList list = (WoTOwnMessageList)getOwnMessageList(MessageListID.construct(uri).toString());
				list.cancelInsert();
				
				if(collision)
					list.incrementInsertIndex();
				
				Persistent.checkedCommit(db, this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollback(db, this, e);
			}
		}
	}

	@Override public synchronized void onMessageListFetchFailed(
			Identity author, FreenetURI uri, FetchFailedMarker.Reason reason) {
		
		WoTMessageList ghostList = new WoTMessageList(mFreetalk, author, uri);
		ghostList.initializeTransient(mFreetalk);
		MessageList.MessageListFetchFailedMarker marker;
		
			try {
				getMessageList(ghostList.getID());
				
				long oldestIndex = -1; 
				long newestIndex = -1;
				
				try {
					oldestIndex = getUnavailableOldMessageListIndex(author);
					newestIndex = getUnavailableNewMessageListIndex(author);
				} catch(Exception e) {
					Logger.error(this, "Getting indices failed", e);
				}
				
				// TODO: Optimization: Remove the above debug code once the following exception doesn't happen anymore
				
				Logger.error(this, "Download failed of a MessageList which we already have (oldest available index: " +
						oldestIndex + "; newest available index: " + newestIndex + "):" + ghostList.getURI());
				return;
			}
			catch(NoSuchMessageListException e1) {
				try {
					marker = getMessageListFetchFailedMarker(ghostList.getID());
				} catch(NoSuchFetchFailedMarkerException e) {
					marker = null;
				}
			}
			
			// It's not possible to keep the synchronization order of message lists to synchronize BEFORE synchronizing on Persistent.transactionLock(db) some places so we 
			// do not synchronize here.
			// And in this function we don't need to synchronize on it anyway because it is not known to anything which might modify it anyway.
			// In general, due to those issues the functions which modify message lists just use the message manager as synchronization object.
			//synchronized(ghostList) {
			synchronized(Persistent.transactionLock(db)) {
				try {
					Date date = CurrentTimeUTC.get();
					Date dateOfNextRetry;
					
					ghostList.storeWithoutCommit();
					
					final IdentityStatistics stats = getOrCreateIdentityStatistics(author);
					stats.onMessageListFetched(ghostList);
					stats.storeWithoutCommit();
					
					if(marker == null) {
						dateOfNextRetry = calculateDateOfNextMessageListFetchRetry(reason, date, 0);
						marker = new MessageList.MessageListFetchFailedMarker(ghostList, reason, date, dateOfNextRetry);
						marker.initializeTransient(mFreetalk);
					} else  {
						marker.setReason(reason);
						marker.incrementNumberOfRetries();
						dateOfNextRetry = calculateDateOfNextMessageListFetchRetry(reason, date, marker.getNumberOfRetries());
						marker.setDate(date);
						marker.setDateOfNextRetry(dateOfNextRetry);
					}
				
					// marker.setAllowRetryNow(false); // setDateOfNextRetry does this for us
					marker.storeWithoutCommit();
					
					Logger.normal(this, "Marked MessageList as download failed with reason " + reason + " (next retry is at " + dateOfNextRetry
							+ ", number of retries: " + marker.getNumberOfRetries() + "): "
							+  ghostList);
					
					Persistent.checkedCommit(db, this);
				}
				catch(Exception ex) {
					Persistent.checkedRollback(db, this, ex);
				}
			}
			//}
	}
	
	public synchronized void onOwnMessageInserted(String id, FreenetURI freenetURI) throws NoSuchMessageException {
		WoTOwnMessage message = (WoTOwnMessage) getOwnMessage(id);
		synchronized(message) {
		synchronized(Persistent.transactionLock(db)) {
			try {
				message.markAsInserted(freenetURI);
				addMessageToMessageList(message);
				Persistent.checkedCommit(db, this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
		}
	}
	
	/**
	 * You have to synchronize on this MessageManager and on Persistent.transactionLock(db) when using this function.
	 */
	private void addMessageToMessageList(WoTOwnMessage message) {
		Query query = db.query();
		query.constrain(WoTOwnMessageList.class);
		query.descend("mAuthor").constrain(message.getAuthor()).identity();
		query.descend("mWasInserted").constrain(false);
		query.descend("mIsBeingInserted").constrain(false);
		
		for(WoTOwnMessageList list : new Persistent.InitializingObjectSet<WoTOwnMessageList>(mFreetalk, query)) {
			try {
				synchronized(list) {
				list.addMessage(message);
				list.storeWithoutCommit();
				}
				if(logDEBUG) Logger.debug(this, "Added own message " + message + " to list " + list);
				return;
			}
			catch(RuntimeException e) {
				/* The list is full. */
				if(logDEBUG) Logger.debug(this, "Not adding message " + message.getID() + " to message list " + list.getID(), e);
			}
		}
		
		WoTOwnIdentity author = (WoTOwnIdentity)message.getAuthor();
		WoTOwnMessageList list = new WoTOwnMessageList(author, getFreeOwnMessageListIndex(author));
		list.initializeTransient(mFreetalk);
		
		// TODO: Optimization: This is debug code which was added on 2011-02-13 for preventing DuplicateMessageListException, it can be removed after some months if they do not happen.
		try {
			final MessageList existingList = mFreetalk.getMessageManager().getOwnMessageList(list.getID());
			throw new RuntimeException("getFreeOwnMessageListIndex reported non-free index, taken by: " + existingList);
		} catch(NoSuchMessageListException e) {}
		
		list.addMessage(message);
		list.storeWithoutCommit();
		if(logDEBUG) Logger.debug(this, "Found no list with free space, created the new list " + list.getID() + " for own message " + message.getID());
	}

	/**
	 * ATTENTION: Due to a db4o bug you must check whether the messages are really not inserted by using testFreenetURIisNull() on them
	 * TODO: Remove this workaround notice for the db4o bug as soon as we are sure that it does not happen anymore.
	 */
	public synchronized ObjectSet<WoTOwnMessage> getNotInsertedOwnMessages() {
		final Query query = db.query();
		query.constrain(WoTOwnMessage.class);
		query.descend("mFreenetURI").constrain(null).identity();
		return new Persistent.InitializingObjectSet<WoTOwnMessage>(mFreetalk, query);
	}

	/**
	 * Returns <code>OwnMessageList</code> objects which are marked as not inserted and not being inserted.
	 */
	public synchronized ObjectSet<WoTOwnMessageList> getNotInsertedOwnMessageLists() {
		Query query = db.query();
		query.constrain(WoTOwnMessageList.class);
		query.descend("mWasInserted").constrain(false);
		query.descend("mIsBeingInserted").constrain(false);
		return new Persistent.InitializingObjectSet<WoTOwnMessageList>(mFreetalk, query);
	}
	
	public synchronized ObjectSet<WoTOwnMessageList> getBeingInsertedOwnMessageLists() {
		Query query = db.query();
		query.constrain(WoTOwnMessageList.class);
		query.descend("mWasInserted").constrain(false);
		query.descend("mIsBeingInserted").constrain(true);
		return new Persistent.InitializingObjectSet<WoTOwnMessageList>(mFreetalk, query);
	}

	/**
	 * Get the highest index of a not fetched message list of the given identity.
	 * 
	 * Notice that this uses a cached number from the IdentityStatistics object and it is possible that there are message lists with
	 * higher index numbers which HAVE been fetched already. This function is for being used in scheduling message list fetches so this is
	 * not a problem.
	 */
	public synchronized long getUnavailableNewMessageListIndex(Identity identity) {
		try {
			return getIdentityStatistics(identity).getIndexOfLatestAvailableMessageList() + 1;
		} catch(NoSuchObjectException e) {
			return 0;
		}
	}
	
	/**
	 * Get a hint for the highest index of a not fetched message list of the given identity.
	 * In the current implementation, this just calls {@link getUnavailableNewMessageListIndex}
	 * 
	 * Notice that this uses a cached number from the IdentityStatistics object and it is possible that there are message lists with
	 * higher index numbers which HAVE been fetched already. This function is for being used in scheduling message list fetches so this is
	 * not a problem.
	 */
	public long getNewMessageListIndexEditionHint(Identity identity) {
		// TODO: Implement storage of edition hints in message lists.
		return getUnavailableNewMessageListIndex(identity);
	}


	/**
	 * Get a hint for the lowest index of a not fetched message list of the given identity.
	 * 
	 * Notice that this uses a cached number from the IdentityStatistics object and it is possible that there are message lists with
	 * lower index numbers which HAVE been fetched already. This function is for being used in scheduling message list fetches so this is
	 * not a problem.
	 */
	public synchronized long getUnavailableOldMessageListIndex(Identity identity) throws NoSuchMessageListException {
		long unavailableIndex; 
		
		try {
			unavailableIndex = getIdentityStatistics(identity).getIndexOfOldestAvailableMessageList() - 1;
		} catch(NoSuchObjectException e) {
			unavailableIndex = 0;
		}
		
		if(unavailableIndex < 0)
			throw new NoSuchMessageListException("");
		
		return unavailableIndex;
	}

	/**
	 * Get the next free index for an OwnMessageList. You have to synchronize on this MessageManager while creating an OwnMessageList, this
	 * function does not provide synchronization.
	 */
	public long getFreeOwnMessageListIndex(final WoTOwnIdentity messageAuthor)  {
		// We do not use IdentityStatistics since it does not guarantee to return the highest existing list, it just guarantees that
		// the index after the one which it has returned does not exist. We must of course not re-use own message list indices.
		final Query q = db.query();
		// We query for MessageList and not OwnMessageList because the user might have deleted his own messages or lost his database
		q.constrain(MessageList.class);
		q.descend("mAuthor").constrain(messageAuthor).identity();
		q.descend("mIndex").orderDescending(); // TODO: This is inefficient!
		final ObjectSet<MessageList> result = new Persistent.InitializingObjectSet<MessageList>(mFreetalk, q);
		
		// We must look for the latest message list which was not marked as fetch failed and return its index + 1
		// Further, if a list is marked as fetch failed but the reason is NOT data not found, we must return its index + 1; explanation:
		// There are 2 things which could go wrong here:
		// 1) We accidentally return the index of a list which was already inserted
		// 2) We accidentally consider an empty slot as filled already and return its index + 1
		// Case 1 is much worse: We do not ever want to insert a message list into an old slot which has fallen out of the network already
		// because that would result in nobody seeing the list.
		// Therefore, we bias towards case 2 which Freetalk should be able to handle well:
		// It is normal for content to fall out of the network, therefore the USK queue must be able to deal well with empty slots.
		
		for(final MessageList list : result) {
			FetchFailedMarker marker;
			
			if(!(list instanceof OwnMessageList)) {
				// If the list is no OwnMessageList, it might be a ghost list only for a data-not-found slot... we shall ignore
				// those lists in the computation, so we check whether there is a FetchFailedMarker
				try {
					marker = getMessageListFetchFailedMarker(list.getID());
				} catch(NoSuchFetchFailedMarkerException e) {
					marker = null;
				}
			} else {
				// The list is an OwnMessageList, we MUST NOT check for a FetchFailedMarker: We ignore DataNotFound non-own MessageLists when
				// returning a free slot index, so both an OwnMessageList and a MessageList with DNF FetchFailedMarker might exist at once
				// for the same index. If we DID take its FetchFailedMarker into consideration, the index would be returned by this function
				// even though an OwnMessageList exists for that index already.
				marker = null;
			}
			
			if(marker == null || marker.getReason() != FetchFailedMarker.Reason.DataNotFound)
				return list.getIndex() + 1;
		}
		
		// There are no message lists or all are marked as DNF => Slot 0 must be free, return it.
		return 0;
	}
	
	public WoTMessageRating rateMessage(final WoTOwnIdentity rater, final WoTMessage message, final byte value) {
		synchronized(mIdentityManager) {
		synchronized(this) {
			// We do not have to re-query the rater/message because MessageRating.storeWithout commit throws if they are not stored anymore
			
			try {
				getMessageRating(rater, message);
				throw new IllegalArgumentException("The message was already rated");
			} catch(NoSuchMessageRatingException e) {}
			
			final WoTMessageRating rating = new WoTMessageRating(rater, message, value);
			rating.initializeTransient(mFreetalk);
			rating.storeAndCommit();
			
			return rating;
		}
		}
	}
	
	/**
	 * This function is not synchronized to allow calls to it when only having locked a {@link Board} and not the whole MessageManager.
	 */
	@Override public WoTMessageRating getMessageRating(
			final OwnIdentity rater, final Message message) throws NoSuchMessageRatingException {
		
		if(!(rater instanceof WoTOwnIdentity))
			throw new IllegalArgumentException("No WoT identity: " + rater);
		
		if(!(message instanceof WoTMessage))
			throw new IllegalArgumentException("No WoT message: " + message);
		
		final Query query = db.query();
		query.constrain(WoTMessageRating.class);
		query.descend("mRater").constrain(rater).identity();
		query.descend("mMessage").constrain(message).identity();
		final InitializingObjectSet<WoTMessageRating> result = new Persistent.InitializingObjectSet<WoTMessageRating>(mFreetalk, query);
		
		switch(result.size()) {
			case 0: throw new NoSuchMessageRatingException();
			case 1: return result.next();
			default: throw new DuplicateElementException("Duplicate rating from " + rater + " of " + message);
		}
	}

	@Override public ObjectSet<WoTMessageRating> getAllMessageRatings(final Message message) {
		final Query query = db.query();
		query.constrain(WoTMessageRating.class);
		query.descend("mMessage").constrain(message).identity();
		return new Persistent.InitializingObjectSet<WoTMessageRating>(mFreetalk, query);
	}

	@Override public ObjectSet<? extends MessageRating> getAllMessageRatingsBy(OwnIdentity rater) {
		final Query query = db.query();
		query.constrain(WoTMessageRating.class);
		query.descend("mRater").constrain(rater).identity();
		return new Persistent.InitializingObjectSet<WoTMessageRating>(mFreetalk, query);
	}

	private void deleteMessageRating(final MessageRating rating, boolean undoTrustChange) {
		if(!(rating instanceof WoTMessageRating))
			throw new IllegalArgumentException("No WoT rating: " + rating);
		
		final WoTMessageRating realRating = (WoTMessageRating)rating;
		
		synchronized(this) {
			realRating.initializeTransient(mFreetalk);
			realRating.deleteAndCommit(undoTrustChange);
		}
	}

	@Override
	public void deleteMessageRatingWithoutRevertingEffect(MessageRating rating) {
		deleteMessageRating(rating, false);
	}

	@Override
	public void deleteMessageRatingAndRevertEffect(MessageRating rating) {
		deleteMessageRating(rating, true);
	}
	
}
