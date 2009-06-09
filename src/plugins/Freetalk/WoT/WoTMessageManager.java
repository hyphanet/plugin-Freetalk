/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Board;
import plugins.Freetalk.CurrentTimeUTC;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.Executor;
import freenet.support.Logger;

public class WoTMessageManager extends MessageManager {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 5 * 60 * 1000;

	private volatile boolean isRunning = false;
	private volatile boolean shutdownFinished = false;
	private Thread mThread;

	/** One for all requests for WoTMessage*, for fairness. */
	public RequestClient requestClient;


	public WoTMessageManager(ExtObjectContainer myDB, Executor myExecutor, WoTIdentityManager myIdentityManager) {
		super(myDB, myExecutor, myIdentityManager);
		mIdentityManager = myIdentityManager;
		isRunning = true;
		mExecutor.execute(this, "FT Message Manager");
		requestClient = new RequestClient() {

			public boolean persistent() {
				return false;
			}

			public void removeFrom(ObjectContainer container) {
				throw new UnsupportedOperationException();
			}
			
		};
	}
	
	/**
	 * For being used in JUnit tests to run without a node.
	 */
	public WoTMessageManager(ExtObjectContainer myDB, WoTIdentityManager myIdentityManager) {
		super(myDB, myIdentityManager);
	}

	public synchronized WoTOwnMessage postMessage(Message myParentMessage, Set<Board> myBoards, Board myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, Date myDate, String myText, List<Attachment> myAttachments) throws Exception {
		WoTOwnMessage m;
		
		synchronized(WoTOwnMessage.class) {	/* TODO: Investigate whether this lock is necessary. */
			Message parentThread = null;
			try {
				if(myParentMessage != null) {
					if(myParentMessage.isThread())
						parentThread = myParentMessage;
					else
						parentThread = myParentMessage.getThread();
				}
			}
			catch(NoSuchMessageException e) {

			}
			
			Date date = myDate!=null ? myDate : CurrentTimeUTC.get();
			m = WoTOwnMessage.construct(parentThread, myParentMessage, myBoards, myReplyToBoard, myAuthor, myTitle, date, myText, myAttachments);
			m.initializeTransient(db, this);
			m.store();
			
			/* We do not add the message to the boards it is posted to because the user should only see the message if it has been downloaded
			 * successfully. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
		}
		
		return m;
	}
	
	public synchronized void onMessageListFetchFailed(FTIdentity author, FreenetURI uri, MessageList.MessageListFetchFailedReference.Reason reason) {
		if(reason == MessageList.MessageListFetchFailedReference.Reason.DataNotFound) {
			/* TODO: Handle DNF in some reasonable way. Mark the MessageLists as unavailable after a certain amount of retries maybe */
			return;
		} 
		
		WoTMessageList list = new WoTMessageList(author, uri);
		try {
			getMessageList(list.getID());
			Logger.debug(this, "Download failed of a MessageList which we already have: " + list.getURI());
		}
		catch(NoSuchMessageListException e) {
			try {
				list.initializeTransient(db, this);
				list.store();
				MessageList.MessageListFetchFailedReference ref = new MessageList.MessageListFetchFailedReference(list, reason);
				ref.initializeTransient(db);
				ref.store();
				Logger.debug(this, "Marked message list as download failed with reason " + reason + ": " +  uri);
			}
			catch(Exception ex) {
				Logger.error(this, "Error while marking a message list as 'download failed'", ex);
				synchronized(db.lock()) {
				db.delete(list);
				db.commit(); Logger.debug(this, "COMMITED.");
				}
			}
		}
	}
	
	public synchronized void onOwnMessageInserted(String id, FreenetURI realURI) throws NoSuchMessageException {
		synchronized(db.lock()) {
			try {
				WoTOwnMessage message = (WoTOwnMessage) getOwnMessage(id);
				if(message == null)
					throw new NoSuchMessageException(id);

				message.markAsInserted(realURI); /* Does not db.commit() */
				addMessageToMessageList(message); /* Does db.commit(); */
			}
			catch(RuntimeException e) {
				db.rollback(); Logger.error(this, "ROLLED BACK!", e);
				throw e;
			}
		}
	}
	
	private synchronized void addMessageToMessageList(WoTOwnMessage message) {
		Query query = db.query();
		query.constrain(WoTOwnMessageList.class);
		query.descend("mAuthor").constrain(message.getAuthor()).identity();
		query.descend("iWasInserted").constrain(false);
		query.descend("iAmBeingInserted").constrain(false);
		
		for(WoTOwnMessageList list : generalGetOwnMessageListIterable(query)) {
			try {
				list.addMessage(message);
				return;
			}
			catch(Exception e) {
				/* The list is full. */
				Logger.debug(this, "Not adding message " + message.getID() + " to message list " + list.getID(), e);
			}
		}
		
		WoTOwnIdentity author = (WoTOwnIdentity)message.getAuthor();
		WoTOwnMessageList list = new WoTOwnMessageList(author, getFreeOwnMessageListIndex(author));
		list.initializeTransient(db, this);
		list.addMessage(message);
		list.store();
		/* FIXME: try,commit/catch */
		Logger.debug(this, "Created the new list " + list.getID() + " for message " + message.getID());
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
						next.initializeTransient(db, self);
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
						next.initializeTransient(db, self);
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
		query.descend("mIndex").orderDescending(); /* FIXME: This is inefficient! Use a native query instead, we just need to find the maximum */
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
		query.descend("mIndex").orderDescending(); /* FIXME: This is inefficient! Use a native query instead */
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
	 * Get the next free index for an WoTOwnMessageList. You have to synchronize on WoTOwnMessageList.class while creating an WoTOwnMessageList, this
	 * function does not provide synchronization.
	 */
	@SuppressWarnings("unchecked")
	public int getFreeOwnMessageListIndex(WoTOwnIdentity messageAuthor)  {
		Query q = db.query();
		/* We query for WoTMessageList and not WoTOwnMessageList because the user might have deleted his own messages or lost his database */
		q.constrain(WoTMessageList.class);
		q.descend("mAuthor").constrain(messageAuthor).identity();
		q.descend("mIndex").orderDescending(); /* FIXME: Write a native db4o query which just looks for the maximum! */
		ObjectSet<WoTMessageList> result = q.execute();
		
		return result.size() > 0 ? result.next().getIndex()+1 : 0;
	}

	public void run() {
		Logger.debug(this, "Message manager started.");
		mThread = Thread.currentThread();
		
		try {
			Logger.debug(this, "Waiting for the node to start up...");
			Thread.sleep((long) (3*60*1000 * (0.5f + Math.random()))); /* Let the node start up */
		}
		catch (InterruptedException e)
		{
			mThread.interrupt();
		}
		
		try {
			while(isRunning) {
				Logger.debug(this, "Message manager loop running...");

				Logger.debug(this, "Message manager loop finished.");

				try {
					Thread.sleep((long) (THREAD_PERIOD * (0.5f + Math.random())));
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

	public void terminate() {
		Logger.debug(this, "Stopping the message manager..."); 
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
		Logger.debug(this, "Stopped the message manager.");
	}

}
