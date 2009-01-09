/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;

import freenet.keys.FreenetURI;

public abstract class OwnMessageList extends MessageList {
	
	private boolean iAmBeingInserted = false;
	
	private boolean iWasInserted = false;

	public OwnMessageList(FTOwnIdentity newAuthor, int newIndex) {
		super(newAuthor, newIndex, new ArrayList<FreenetURI>(32)); // TODO: Figure out a reasonable value
	}
	
	public FreenetURI getInsertURI() {
		return generateURI(((FTOwnIdentity)mAuthor).getInsertURI());
	}
	
	/**
	 * Add an <code>OwnMessage</code> to this <code>MessageList</code>.
	 * This function synchronizes on the <code>MessageList</code> and the given message.
	 */
	public synchronized void addMessage(OwnMessage newMessage) {
		synchronized(newMessage) {
			if(iAmBeingInserted || iWasInserted)
				throw new RuntimeException("Trying to add a message to a message list which is already being inserted.");
			
			if(newMessage.getAuthor() != mAuthor)
				throw new IllegalArgumentException("Trying to add a message with wrong author " + newMessage.getAuthor() + " to an own message list of " + mAuthor);
			
			mMessages.add(new MessageReference(newMessage.getRealURI()));
			newMessage.setMessageList(this);
		}
	}

	/* Old code from OwnMessage, might be needed here maybe.
	public synchronized void incrementInsertIndex() {
		synchronized(OwnMessage.class) {
			int freeIndex = mMessageManager.getFreeMessageIndex((FTOwnIdentity)mAuthor);
			mIndex = Math.max(mIndex+1, freeIndex);
			mID = generateID(mAuthor, mIndex);
			store();
		}
	}
	*/
	
	public synchronized void beginOfInsert() {
		iAmBeingInserted = true;
	}
	
	public synchronized boolean wasInserted() {
		return iWasInserted;
	}

	public synchronized void markAsInserted() {
		iWasInserted = true;
	}

}
