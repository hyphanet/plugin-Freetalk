/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;

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

	private final FMSBoard self = this;
	
	/**
	 * Contains all messages in this board, both as a Hashmap and as a linked list which is sorted by date.
	 * The hashmap is useful for checking whether a message was already stored.
	 * The linked list allows fast displaying of all messages.
	 */
	private final UpdatableSortedLinkedListWithForeignIndex mMessages = new UpdatableSortedLinkedListWithForeignIndex();
	
	private final FMSMessageManager mMessageManager;
	
	private final String mName;
	
	private String mDescription;
	
	public FMSBoard(FMSMessageManager newMessageManager, String newName, String newDescription) {
		if(newName==null || newName.isEmpty())
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
	
	public void addMessage(FMSMessage newMessage) throws UpdatableSortedLinkedListKilledException {
		if(mMessages.containsKey(newMessage)) {
			/* The message was already stored */
			assert(false); /* TODO: Add logging. I don't know whether this should happen. */
			return;
		}
		
		if(newMessage.getParentURI() == null) {
			mMessages.add(newMessage);
			// FIXME: link children to the thread
		}
		else
		{
			FreenetURI parentURI = newMessage.getParentURI();
			FMSMessage parentMessage = mMessageManager.get(parentURI);
			if(parentMessage == null) {
				mMessages.add(newMessage);
				/* FIXME: The MessageManager should try to download the parent message if it's poster has enough trust.
				 * If it is programmed to do that, it will check its Hashtable whether the parent message already exists.
				 * We also do that here, therefore, when implementing parent message downloading, please do the Hashtable checking only once. 
				 */
				return;
			} else {
				parentMessage.addChild(newMessage);
			}
		}
	}
	

	/**
	 * Get all messages in the board. The view is specified to the FMSOwnIdentity displaying it, therefore you have to pass one as parameter.
	 * @param identity The identity viewing the board.
	 * @return An iterator of the message which the identity will see (based on its trust levels).
	 */
	public synchronized Iterator<FMSMessage> messageIterator(final FMSOwnIdentity identity) {
		return new Iterator<FMSMessage>() {
			private final FMSOwnIdentity mIdentity = identity;
			private Iterator<FMSMessage> iter = self.mMessages.iterator();
			private FMSMessage next = iter.hasNext() ? iter.next() : null;

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
