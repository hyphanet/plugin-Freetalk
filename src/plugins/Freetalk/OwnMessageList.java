/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import plugins.Freetalk.WoT.WoTOwnMessage;
import freenet.keys.FreenetURI;

public abstract class OwnMessageList extends MessageList {

	private boolean iAmBeingInserted = false;

	private boolean iWasInserted = false;

	/**
	 * In opposite to it's parent class, for each <code>OwnMessage</code> only one <code>OwnMessageReference</code> is stored, no matter to how
	 * many boards the OwnMessage is posted.
	 *  
	 * @see MessageList.MessageReference
	 */
	public final class OwnMessageReference extends MessageReference {
		
		public OwnMessageReference(OwnMessage myMessage) {
			super(myMessage.getID(), myMessage.getURI(), null);
		}

	}

	public OwnMessageList(FTOwnIdentity newAuthor, int newIndex) {
		super(newAuthor, newIndex);
	}

	public FreenetURI getInsertURI() {
		return generateURI(((FTOwnIdentity)mAuthor).getInsertURI(), mIndex);
	}

	/**
	 * Add an <code>OwnMessage</code> to this <code>MessageList</code>.
	 * This function synchronizes on the <code>MessageList</code> and the given message.
	 * @throws Exception If the message list is full.
	 */
	public synchronized void addMessage(WoTOwnMessage newMessage) throws Exception {
		synchronized(newMessage) {
			if(iAmBeingInserted || iWasInserted)
				throw new IllegalArgumentException("Trying to add a message to a message list which is already being inserted.");
			
			if(newMessage.getAuthor() != mAuthor)
				throw new IllegalArgumentException("Trying to add a message with wrong author " + newMessage.getAuthor() + " to an own message list of " + mAuthor);
			
			OwnMessageReference ref = new OwnMessageReference(newMessage);
			mMessages.add(ref);
			if(mMessages.size() > 1 && fitsIntoContainer() == false) {
				mMessages.remove(ref);
				throw new Exception("OwnMessageList is full."); /* TODO: Chose a better exception */
			}
			
			newMessage.setMessageList(this);
			store();
		}
	}

	protected abstract boolean fitsIntoContainer();
	
	public synchronized void beginOfInsert() {
		iAmBeingInserted = true;
		store();
	}
	
	public synchronized boolean wasInserted() {
		return iWasInserted;
	}

	public synchronized void markAsInserted() {
		iWasInserted = true;
		store();
	}

}
