/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import freenet.keys.FreenetURI;

/**
 * @author xor
 *
 */
public class FMSBoard {

	private final FMSBoard self = this;
	/*
	 * FIXME: We need a datastructure which is a HashTable and a LinkedList which is sorted by Date of the messages.
	 * java.util.LinkedHashSet is the right thing but it does not provide sorting. 
	 */
	private final Hashtable<FreenetURI, FMSMessage> mMessages = new HashTable<FreenetURI, FMSMessage>();
	private final LinkedList<FMSMessage> mMessagesSorted = new LinkedList<FMSMessage>();
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

	/**
	 * Get all messages in the board. The view is specified to the FMSOwnIdentity displaying it, therefore you have to pass one as parameter.
	 * @param identity The identity viewing the board.
	 * @return An iterator of the message which the identity will see (based on its trust levels).
	 */
	public synchronized Iterator<FMSMessage> messageIterator(final FMSOwnIdentity identity) {
		return new Iterator<FMSMessage>() {
			private final FMSOwnIdentity mIdentity = identity;
			private Iterator<FMSMessage> iter = self.mMessagesSorted.iterator();
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
			
		}
	}
	
}
