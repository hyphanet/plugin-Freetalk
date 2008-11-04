/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Iterator;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.UpdatableSortedLinkedListKilledException;

/**
 * @author xor
 *
 */
public class FMSBoard {

	private transient final ObjectContainer db;

	private transient final FMSBoard self = this;

	private transient final FMSMessageManager mMessageManager;

	private final String mName;

	/**
	 * Get a list of fields which the database should create an index on.
	 */
	public static String[] getIndexedFields() {
		return new String[] {"mName"};
	}
	
	public FMSBoard(ObjectContainer myDB, FMSMessageManager newMessageManager, String newName) {
		if(newName==null || newName.length() == 0)
			throw new IllegalArgumentException("Empty board name.");

		assert(myDB != null);
		assert(newMessageManager != null);

		db = myDB;
		mMessageManager = newMessageManager;
		// FIXME: Validate name and description.
		mName = newName;
		
		db.store(this);
		db.commit();
	}

	/**
	 * @return The name.
	 */
	public String getName() {
		return mName;
	}

	/**
	 * Called by the <code>FMSMessageManager</code> to add a just received message to the board.
	 * The job for this function is to find the right place in the thread-tree for the new message and to move around older messages
	 * if a parent message of them is received.
	 */
	public synchronized void addMessage(FMSMessage newMessage) throws UpdatableSortedLinkedListKilledException {
		synchronized(mMessageManager) {
			db.store(newMessage);
			db.commit();

			if(!newMessage.isThread())
			{
				FreenetURI parentURI = newMessage.getParentURI();
				FMSMessage parentMessage = mMessageManager.get(parentURI); /* TODO: This allows crossposting. Figure out whether we need to handle it specially */
				FMSMessage parentThread = findParentThread(newMessage);
	
				if(parentThread != null)
					newMessage.setThread(parentThread);
	
				if(parentMessage != null) {
					newMessage.setParent(parentMessage);
				} else { /* The message is an orphan */
					if(parentThread != null) {
						newMessage.setParent(parentThread);	/* We found its parent thread so just stick it in there for now */
					}
					else {
						 /* The message is an absolute orphan */
	
						/* 
						 * FIXME: The MessageManager should try to download the parent message if it's poster has enough trust.
						 * If it is programmed to do that, it will check its Hashtable whether the parent message already exists.
						 * We also do that here, therefore, when implementing parent message downloading, please do the Hashtable checking only once. 
						 */
					}
				} 
			}
	
			linkOrphansToNewParent(newMessage);
		}
	}

	private synchronized void linkOrphansToNewParent(FMSMessage newMessage) throws UpdatableSortedLinkedListKilledException {
		if(newMessage.isThread()) {
			Iterator<FMSMessage> absoluteOrphans = absoluteOrphanIterator(newMessage.getURI());
			while(absoluteOrphans.hasNext()){	/* Search in the absolute orphans for messages which belong to this thread  */
				FMSMessage orphan = absoluteOrphans.next();
				orphan.setParent(newMessage);
			}
		}
		else {
			FMSMessage parentThread = newMessage.getThread();
			if(parentThread != null) {	/* Search in its parent thread for its children */
				Iterator<FMSMessage> iter = parentThread.childrenIterator(this);
				while(iter.hasNext()) {
					FMSMessage parentThreadChild = iter.next();
					
					if(parentThreadChild.getParentURI().equals(newMessage.getURI())) { /* We found its parent, yeah! */
						parentThreadChild.setParent(newMessage); /* It's a child of the newMessage, not of the parentThread */
					}
				}
			}
			else { /* The new message is an absolute orphan, find its children amongst the other absolute orphans */
				Iterator<FMSMessage> absoluteOrphans = absoluteOrphanIterator(newMessage.getURI());
				while(absoluteOrphans.hasNext()){	/* Search in the orphans for messages which belong to this message  */
					FMSMessage orphan = absoluteOrphans.next();
					/*
					 * The following if() could be joined into the db4o query in absoluteOrphanIterator(). I did not do it because we could
					 * cache the list of absolute orphans locally. 
					 */
					if(orphan.getParentURI().equals(newMessage.getURI()))
						orphan.setParent(newMessage);
				}
			}
		}
	}
	
	protected synchronized FMSMessage findParentThread(FMSMessage m) {
		Query q = db.query();
		q.constrain(FMSMessage.class);
		/* FIXME: I assume that db4o is configured to keep an URI index per board. We still have to ensure in FMS.java that it is configured to do so.
		 * If my second assumption - that the descend() statements are evaluated in the specified order - is true, then it might be faste because the
		 * URI index is smaller per board than the global URI index. */
		q.descend("mBoards").constrain(mName); 
		q.descend("mURI").constrain(m.getParentThreadURI());
		ObjectSet<FMSMessage> parents = q.execute();
		
		assert(parents.size() <= 1);
		
		return (parents.size() != 0 ? parents.next()  : null);
	}
	

	/**
	 * Get all threads in the board. The view is specified to the FMSOwnIdentity displaying it, therefore you have to pass one as parameter.
	 * @param identity The identity viewing the board.
	 * @return An iterator of the message which the identity will see (based on its trust levels).
	 */
	public synchronized Iterator<FMSMessage> threadIterator(final FMSOwnIdentity identity) {
		return new Iterator<FMSMessage>() {
			private final FMSOwnIdentity mIdentity = identity;
			private final Iterator<FMSMessage> iter;
			private FMSMessage next;
			 
			{
				/* FIXME: If db4o supports precompiled queries, this one should be stored precompiled.
				 * Reason: We sort the threads by date.
				 * Maybe we can just keep the Query-object and call q.execute() as many times as we like to?
				 * Or somehow tell db4o to keep a per-board thread index which is sorted by Date? - This would be the best solution */
				Query q = db.query();
				q.constrain(FMSMessage.class);
				q.descend("mBoards").constrain(mName); /* FIXME: mBoards is an array. Does constrain() check whether it contains the element mName? */
				q.descend("mThread").constrain(null);
				q.descend("mDate").orderDescending();

				iter = q.execute().iterator();
				next = iter.hasNext() ? iter.next() : null;
			}

			public boolean hasNext() {
				for(; next != null; next = iter.hasNext() ? iter.next() : null)
				{
					if(mIdentity.wantsMessagesFrom(identity))
						return true;
				}
				return false;
			}

			public FMSMessage next() {
				FMSMessage result = hasNext() ? next : null;
				next = iter.hasNext() ? iter.next() : null;
				return result;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
			
		};
	}
	
	/**
	 * Get an iterator over messages for which the parent thread with the given URI was not known. 
	 */
	public synchronized Iterator<FMSMessage> absoluteOrphanIterator(final FreenetURI thread) {
		return new Iterator<FMSMessage>() {
			private final ObjectSet<FMSMessage> mMessages;
			private final Iterator<FMSMessage> iter;

			{
				/* FIXME: This query should be accelerated. The amount of absolute orphans is very small usually, so we should configure db4o
				 * to keep a separate list of those. */
				Query q = db.query();
				q.constrain(FMSMessage.class);
				q.descend("mBoards").constrain(mName); /* FIXME: mBoards is an array. Does constrain() check whether it contains the element mName? */
				q.descend("mThreadURI").constrain(thread);
				q.descend("mThread").constrain(null);
				mMessages = q.execute();
				iter = mMessages.iterator();
			}

			public boolean hasNext() {
				return mMessages.hasNext();
			}

			public FMSMessage next() {
				return mMessages.next();
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
