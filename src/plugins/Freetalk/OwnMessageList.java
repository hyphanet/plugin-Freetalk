/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.keys.FreenetURI;
import freenet.support.Logger;

// @Indexed // I can't think of any query which would need to get all OwnMessageList objects.
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
			super(myMessage.getID(), myMessage.getRealURI(), null, myMessage.getDate());
		}

	}

	public OwnMessageList(FTOwnIdentity newAuthor, int newIndex) {
		super(newAuthor, newIndex);
	}
	
	public FTOwnIdentity getAuthor() {
		return (FTOwnIdentity)super.getAuthor();
	}

	/**
	 * Get the SSK insert URI of this message list.
	 * @return
	 */
	public FreenetURI getInsertURI() {
		return generateURI(getAuthor().getInsertURI(), mIndex).sskForUSK();
	}

	/**
	 * Add an <code>OwnMessage</code> to this <code>MessageList</code>.
	 * This function synchronizes on the <code>MessageList</code> and the given message.
	 * Stores the given message and this OwnMessageList in the database without committing the transaction.
	 * 
	 * @throws Exception If the message list is full.
	 */
	public synchronized void addMessage(OwnMessage newMessage) {
		synchronized(newMessage) {
			if(iAmBeingInserted || iWasInserted)
				throw new IllegalStateException("Trying to add a message to a message list which is already being inserted.");
			
			if(newMessage.getAuthor() != mAuthor)
				throw new IllegalStateException("Trying to add a message with wrong author " + newMessage.getAuthor() + " to an own message list of " + mAuthor);
			
			OwnMessageReference ref = new OwnMessageReference(newMessage);
			mMessages.add(ref);
			if(mMessages.size() > 1 && fitsIntoContainer() == false) {
				mMessages.remove(ref);
				throw new IllegalStateException("OwnMessageList is full."); /* TODO: Chose a better exception */
			}
			
			newMessage.setMessageList(this);
		}
	}
	
	public synchronized int getMessageCount() {
		return mMessages.size();
	}

	protected boolean fitsIntoContainer() {
		if(getMessageCount() > MAX_MESSAGES_PER_MESSAGELIST)
			return false;
		
		return true;
	}
	
	/**
	 * Stores this OwnMessageList in the database without committing the transaction.
	 */
	public synchronized void beginOfInsert() {
		iAmBeingInserted = true;
		storeWithoutCommit();
	}

	/**
	 * Stores this OwnMessageList in the database without committing the transaction.
	 */
	public synchronized void cancelInsert() {
		if(iWasInserted)
			throw new RuntimeException("The OwnMessageList was already inserted.");
		
		iAmBeingInserted = false;
		storeWithoutCommit();
	}
	
	public synchronized boolean wasInserted() {
		return iWasInserted;
	}

	/**
	 * Stores this OwnMessageList in the database without committing the transaction.
	 */
	public synchronized void markAsInserted() {
		if(iAmBeingInserted == false)
			throw new RuntimeException("Trying to mark a MessageList as 'inserted' which was not marked as 'being inserted': This MUST NOT happen:" +
					" Messages can still be added to a list if it is not marked as being inserted. If it is being inserted already without being marked," +
					" the messages will not be contained in the actually inserted message list."); 
		
		if(iWasInserted)
			Logger.error(this, "markAsInserted called for an already inserted message list: " + this);
			
		iWasInserted = true;
		storeWithoutCommit();
	}

}
