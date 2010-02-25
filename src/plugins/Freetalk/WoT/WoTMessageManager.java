/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.FetchFailedMarker;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.MessageURI;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.exceptions.NoSuchFetchFailedMarkerException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
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

public class WoTMessageManager extends MessageManager {
	
	/** One for all requests for WoTMessage*, for fairness. */
	final RequestClient mRequestClient;

	public WoTMessageManager(ExtObjectContainer myDB, IdentityManager myIdentityManager, Freetalk myFreetalk, PluginRespirator myPluginRespirator) {
		super(myDB, myIdentityManager, myFreetalk, myPluginRespirator);
		
		mRequestClient = new RequestClient() {

			public boolean persistent() {
				return false;
			}

			public void removeFrom(ObjectContainer container) {
				throw new UnsupportedOperationException();
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
	protected synchronized void clearExpiredFetchFailedMarkers() {
		super.clearExpiredFetchFailedMarkers();
	}

	public WoTOwnMessage postMessage(MessageURI myParentThreadURI, Message myParentMessage, Set<Board> myBoards, Board myReplyToBoard, 
			FTOwnIdentity myAuthor, String myTitle, Date myDate, String myText, List<Attachment> myAttachments) throws Exception {
		WoTOwnMessage m;
		
		if(myParentThreadURI != null && !(myParentThreadURI instanceof WoTMessageURI))
			throw new IllegalArgumentException("Parent thread URI is no WoTMessageURI: " + myParentThreadURI);

		Date date = myDate!=null ? myDate : CurrentTimeUTC.get();
		m = WoTOwnMessage.construct((WoTMessageURI)myParentThreadURI, myParentMessage, myBoards, myReplyToBoard, myAuthor, myTitle, date, myText, myAttachments);
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
		synchronized(db.lock()) {
			try {
				WoTOwnMessageList list = (WoTOwnMessageList)getOwnMessageList(MessageList.getIDFromURI(uri));
				list.cancelInsert();
				
				if(collision)
					list.incrementInsertIndex();
				
				db.commit(); Logger.debug(this, "COMMITED.");
			}
			catch(RuntimeException e) {
				Persistent.checkedRollback(db, this, e);
			}
		}
	}
	
	public synchronized void onMessageListFetchFailed(FTIdentity author, FreenetURI uri, FetchFailedMarker.Reason reason) {
		WoTMessageList ghostList = new WoTMessageList(author, uri);
		ghostList.initializeTransient(mFreetalk);
		MessageList.MessageListFetchFailedMarker marker;
		
			try {
				getMessageList(ghostList.getID());
				Logger.error(this, "Download failed of a MessageList which we already have: " + ghostList.getURI());
				return;
			}
			catch(NoSuchMessageListException e1) {
				try {
					marker = getMessageListFetchFailedMarker(ghostList.getID());
				} catch(NoSuchFetchFailedMarkerException e) {
					marker = null;
				}
			}
			
			synchronized(db.lock()) {
				try {
					Date date = CurrentTimeUTC.get();
					Date dateOfNextRetry;

					ghostList.storeWithoutCommit();
					
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
				
					marker.storeWithoutCommit();
					
					Logger.normal(this, "Marked MessageList as download failed with reason " + reason + " (next retry is at " + dateOfNextRetry
							+ ", number of retries: " + marker.getNumberOfRetries() + "): "
							+  ghostList);
					
					db.commit();
					Logger.debug(this, "COMMITED.");
				}
				catch(Exception ex) {
					Persistent.checkedRollback(db, this, ex);
				}
			}
	}
	
	public synchronized void onOwnMessageInserted(String id, FreenetURI realURI) throws NoSuchMessageException {
		WoTOwnMessage message = (WoTOwnMessage) getOwnMessage(id);
		synchronized(message) {
		synchronized(db.lock()) {
			try {
				message.markAsInserted(realURI);
				addMessageToMessageList(message);
				db.commit(); Logger.debug(this, "COMMITED.");
			}
			catch(RuntimeException e) {
				Persistent.rollbackAndThrow(db, this, e);
			}
		}
		}
	}
	
	/**
	 * You have to synchronize on this MessageManager and on db.lock() when using this function.
	 */
	private void addMessageToMessageList(WoTOwnMessage message) {
		Query query = db.query();
		query.constrain(WoTOwnMessageList.class);
		query.descend("mAuthor").constrain(message.getAuthor()).identity();
		query.descend("iWasInserted").constrain(false);
		query.descend("iAmBeingInserted").constrain(false);
		
		for(WoTOwnMessageList list : generalGetOwnMessageListIterable(query)) {
			try {
				// FIXME: list.addMessage is synchronized and the caller of this function synchronizes on db.lock() - wrong order! This could cause deadlocks.
				list.addMessage(message);
				Logger.debug(this, "Added own message " + message + " to list " + list);
				return;
			}
			catch(RuntimeException e) {
				/* The list is full. */
				Logger.debug(this, "Not adding message " + message.getID() + " to message list " + list.getID(), e);
			}
		}
		
		WoTOwnIdentity author = (WoTOwnIdentity)message.getAuthor();
		WoTOwnMessageList list = new WoTOwnMessageList(author, getFreeOwnMessageListIndex(author));
		list.initializeTransient(mFreetalk);
		// FIXME: list.addMessage is synchronized and the caller of this function synchronizes on db.lock() - wrong order! This could cause deadlocks.
		list.addMessage(message);
		list.storeWithoutCommit();
		Logger.debug(this, "Found no list with free space, created the new list " + list.getID() + " for own message " + message.getID());
	}

	@SuppressWarnings("unchecked")
	public synchronized Iterable<WoTOwnMessage> getNotInsertedOwnMessages() {
		return new Iterable<WoTOwnMessage>() {
			public Iterator<WoTOwnMessage> iterator() {
				return new Iterator<WoTOwnMessage>() {
					private Iterator<WoTOwnMessage> iter;

					{
						Query query = db.query();
						query.constrain(WoTOwnMessage.class);
						query.descend("mRealURI").constrain(null).identity();
						iter = query.execute().iterator();
					}

					public boolean hasNext() {
						return iter.hasNext();
					}

					public WoTOwnMessage next() {
						WoTOwnMessage next = iter.next();
						next.initializeTransient(mFreetalk);
						return next;
					}

					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	/**
	 * For a database Query of result type <code>ObjectSet\<WoTOwnMessageList\></code>, this function provides an <code>Iterable</code>. The
	 * iterator of the ObjectSet cannot be used instead because it will not call initializeTransient() on the objects. The iterator which is
	 * returned by this function takes care of that.
	 * Please synchronize on the <code>WoTMessageManager</code> when using this function, it is not synchronized itself.
	 */
	protected Iterable<WoTOwnMessageList> generalGetOwnMessageListIterable(final Query query) {
		return new Iterable<WoTOwnMessageList>(){
			@SuppressWarnings("unchecked")
			public Iterator<WoTOwnMessageList> iterator() {
				return new Iterator<WoTOwnMessageList>() {
					private Iterator<WoTOwnMessageList> iter = query.execute().iterator();
					
					public boolean hasNext() {
						return iter.hasNext();
					}

					public WoTOwnMessageList next() {
						WoTOwnMessageList next = iter.next();
						next.initializeTransient(mFreetalk);
						return next;
					}

					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	/**
	 * Returns <code>OwnMessageList</code> objects which are marked as not inserted. It will also return those which are marked as currently
	 * being inserted, they are not filtered out because in the current implementation the WoTMessageListInserter will cancel all inserts
	 * before using this function.
	 */
	public synchronized Iterable<WoTOwnMessageList> getNotInsertedOwnMessageLists() {
		Query query = db.query();
		query.constrain(WoTOwnMessageList.class);
		query.descend("iWasInserted").constrain(false);
		return generalGetOwnMessageListIterable(query);
	}
	
	public synchronized Iterable<WoTOwnMessageList> getBeingInsertedOwnMessageLists() {
		Query query = db.query();
		query.constrain(WoTOwnMessageList.class);
		query.descend("iWasInserted").constrain(false);
		query.descend("iAmBeingInserted").constrain(true);
		return generalGetOwnMessageListIterable(query);
	}

	@SuppressWarnings("unchecked")
	public synchronized int getUnavailableNewMessageListIndex(FTIdentity identity) {
		Query query = db.query();
		query.constrain(WoTMessageList.class);
		query.constrain(WoTOwnMessageList.class).not();
		query.descend("mAuthor").constrain(identity).identity();
		query.descend("mIndex").orderDescending(); // FIXME: This is inefficient!
		ObjectSet<WoTMessageList> result = query.execute();
		
		if(result.size() == 0)
			return 0;
		
		return result.next().getIndex() + 1;
	}

	@SuppressWarnings("unchecked")
	public synchronized int getUnavailableOldMessageListIndex(FTIdentity identity) {
		Query query = db.query();
		query.constrain(WoTMessageList.class);
		query.constrain(WoTOwnMessageList.class).not();
		query.descend("mAuthor").constrain(identity).identity();
		query.descend("mIndex").orderDescending(); // FIXME: This is inefficient!
		ObjectSet<WoTMessageList> result = query.execute();
		
		if(result.size() == 0)
			return 0;
		
		int latestAvailableIndex = result.next().getIndex();
		int freeIndex = latestAvailableIndex - 1;
		for(; result.hasNext() && result.next().getIndex() == freeIndex; ) {
			--freeIndex;
		}
		
		/* FIXME: To avoid always checking ALL messagelists for a missing one, store somewhere in the FTIdentity what the latest index is up to
		 * which all messagelists are available! */
		
		return freeIndex >= 0 ? freeIndex : latestAvailableIndex+1;
	}

	/**
	 * Get the next free index for an OwnMessageList. You have to synchronize on this MessageManager while creating an OwnMessageList, this
	 * function does not provide synchronization.
	 */
	@SuppressWarnings("unchecked")
	public int getFreeOwnMessageListIndex(WoTOwnIdentity messageAuthor)  {
		Query q = db.query();
		/* We query for MessageList and not OwnMessageList because the user might have deleted his own messages or lost his database */
		q.constrain(MessageList.class);
		q.descend("mAuthor").constrain(messageAuthor).identity();
		q.descend("mIndex").orderDescending(); // FIXME: This is inefficient!
		ObjectSet<MessageList> result = q.execute();
		
		return result.size() > 0 ? result.next().getIndex()+1 : 0;
	}

}
