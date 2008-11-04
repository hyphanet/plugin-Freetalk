/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.DoublyLinkedList;
import freenet.support.IndexableUpdatableSortedLinkedListItem;
import freenet.support.UpdatableSortedLinkedListItemImpl;
import freenet.support.UpdatableSortedLinkedListKilledException;
import freenet.support.UpdatableSortedLinkedListWithForeignIndex;
import freenet.support.DoublyLinkedList.Item;

/**
 * @author xor
 *
 */
public class FMSBoard extends UpdatableSortedLinkedListItemImpl implements IndexableUpdatableSortedLinkedListItem {
	
	private ObjectContainer db;

	/**
	 * Contains all threads in this board, both as a Hashmap and as a linked list which is sorted by date.
	 * The hashmap is useful for checking whether a message was already stored.
	 * The linked list allows fast displaying of all messages.
	 */
	private UpdatableSortedLinkedListWithForeignIndex mThreads = new UpdatableSortedLinkedListWithForeignIndex();
	
	/**
	 * Contains orphans for which even the parent thread did not exist. They are currently listed in the threads list and have to be removed
	 * from it if their thread is found.
	 */
	private LinkedList<FMSMessage> mAbsoluteOrphans = new LinkedList<FMSMessage>(); 

	private final FMSBoard self = this;
	
	private final FMSMessageManager mMessageManager;
	
	private final String mName;
	
	private String mDescription;
	
	/**
	 * Get a list of fields which the database should create an index on.
	 */
	public static String[] getIndexedFields() {
		return new String[] {"mName"};
	}
	
	public FMSBoard(FMSMessageManager newMessageManager, String newName, String newDescription) {
		if(newName==null || newName.length() == 0)
			throw new IllegalArgumentException("Empty board name.");
		
		assert(newMessageManager != null);
		mMessageManager = newMessageManager;
		// FIXME: Remove anything dangerous from name and description.
		mName = newName;
		setDescription(newDescription);
	}

	/**
	 * @return the Description
	 */
	public String getDescription() {
		return mDescription;
	}
	
	/**
	 * @param description The description to set.
	 */
	public void setDescription(String newDescription) {
		//FIXME: Remove anything dangerous from description.
		mDescription = newDescription!=null ? newDescription : "";
	}

	/**
	 * @return The name
	 */
	public String getName() {
		return mName;
	}
	
	/**
	 * Called by the <code>FMSMessageManager</code> to add a just received message to the board.
	 * The job for this function is to find the right place in the thread-tree for the new message and to move around older messages
	 * if a parent message of them is received.
	 */
	public void addMessage(FMSMessage newMessage) throws UpdatableSortedLinkedListKilledException {	
		if(newMessage.isThread()) {	/* The message is a thread */
			mThreads.add(newMessage);
		}
		else
		{
			FreenetURI parentURI = newMessage.getParentURI();
			FMSMessage parentMessage = mMessageManager.get(parentURI); /* TODO: This allows crossposting. Figure out whether we need to handle it specially */
			if(parentMessage != null) {
				parentMessage.addChild(newMessage);
				db.store(parentMessage);
			} else { /* The message is an orphan */
				FMSMessage parentThread = mMessageManager.get(newMessage.getParentThreadURI());
				if(parentThread != null) {
					parentThread.addChild(newMessage);	/* We found its parent thread so just stick it in there for now */
					db.store(parentThread);
				}
				else { /* The message is an absolute orphan */
					mThreads.add(newMessage); /* TODO: Instead of hiding the message completely, we make it look like a thread. Reconsider this. */
					mAbsoluteOrphans.add(newMessage);
					
					/* 
					 * FIXME: The MessageManager should try to download the parent message if it's poster has enough trust.
					 * If it is programmed to do that, it will check its Hashtable whether the parent message already exists.
					 * We also do that here, therefore, when implementing parent message downloading, please do the Hashtable checking only once. 
					 */
				}
			} 
		}
		
		db.commit();
		
		linkOrphansToNewParent(newMessage);
	}
	
	private void linkOrphansToNewParent(FMSMessage newMessage) throws UpdatableSortedLinkedListKilledException {
		if(newMessage.isThread()) {
			for(FMSMessage o : mAbsoluteOrphans) {	/* Search in the orphans for messages which belong to this thread */
				if(o.getParentThreadURI().equals(newMessage.getURI())) {
					newMessage.addChild(o);
					mAbsoluteOrphans.remove(o);
					mThreads.remove(o);
				}
			}
		}
		else {
			FMSMessage parentThread = (FMSMessage)mThreads.get(newMessage.getParentThreadURI());
			if(parentThread != null) {	/* Search in its parent thread for its children */
				Iterator<FMSMessage> iter = parentThread.childrenIterator();
				while(iter.hasNext()) {
					FMSMessage parentThreadChild = iter.next();
					
					if(parentThreadChild.getParentURI().equals(newMessage.getURI())) { /* We found its parent, yeah! */
						iter.remove();	/* It's a child of the newMessage, not of the parentThread */
						newMessage.addChild(parentThreadChild);
					}
				}
				db.store(parentThread);
			} else { /* The new message is an absolute orphan, find its children amongst the other absolute orphans */
				for(FMSMessage o : mAbsoluteOrphans) {
					if(o.getParentURI().equals(newMessage.getURI())) {
						newMessage.addChild(o);
						mAbsoluteOrphans.remove(o);
					}
				}
			}
		}
		
		db.store(newMessage);
		db.commit();
	}
	

	/**
	 * Get all messages in the board. The view is specified to the FMSOwnIdentity displaying it, therefore you have to pass one as parameter.
	 * @param identity The identity viewing the board.
	 * @return An iterator of the message which the identity will see (based on its trust levels).
	 */
	public synchronized Iterator<FMSMessage> threadIterator(final FMSOwnIdentity identity) {
		return new Iterator<FMSMessage>() {
			private final FMSOwnIdentity mIdentity = identity;
			private final ObjectSet<FMSMessage> mMessages;
			private final Iterator<FMSMessage> iter;
			private FMSMessage next;
			 
			{
				Query q = db.query();
				q.constrain(FMSMessage.class);
				q.descend("mBoards").constrain(mName); /* FIXME: mBoards is an array. Does constrain() check whether it contains the element mName? */
				q.descend("mParentURI").constrain(null);
				q.orderDescending();
				mMessages = q.execute();
				iter = mMessages.iterator();
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

	/* (non-Javadoc)
	 * @see freenet.support.IndexableUpdatableSortedLinkedListItem#indexValue()
	 */
	public Object indexValue() {
		return mName;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		FMSBoard b = (FMSBoard)o;
		return mName.compareTo(b.getName());
	}
}
