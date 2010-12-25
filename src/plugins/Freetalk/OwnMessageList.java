/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import plugins.Freetalk.Message.MessageID;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

// @IndexedField // I can't think of any query which would need to get all OwnMessageList objects.
public abstract class OwnMessageList extends MessageList {

	private boolean mIsBeingInserted = false;

	private boolean mWasInserted = false;

	/**
	 * In opposite to it's parent class, for each <code>OwnMessage</code> only one <code>OwnMessageReference</code> is stored, no matter to how
	 * many boards the OwnMessage is posted.
	 *  
	 * @see MessageList.MessageReference
	 */
	public static final class OwnMessageReference extends MessageReference {
		
		public OwnMessageReference(OwnMessage myMessage) {
			super(MessageID.construct(myMessage), myMessage.getFreenetURI(), null, myMessage.getDate());
		}
		
		public void databaseIntegrityTest() throws Exception {
			super.databaseIntegrityTest(); 
		}

	}
	
	public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		checkedActivate(3);
		
		if(!(mAuthor instanceof OwnIdentity))
			throw new IllegalStateException("mAuthor is no OwnIdentity: " + mAuthor);
			
		if(mIsBeingInserted && mWasInserted)
			throw new IllegalStateException("mIsBeingInserted == true and mWasInserted == true");
		
		for(MessageReference ref : mMessages) {
			if(!(ref instanceof OwnMessageReference))
				throw new IllegalStateException("Found non-own MessageReference: " + ref);
		}
		
		// TODO: Validate mMessages content. Size is validated by parent
	}

	public OwnMessageList(OwnIdentity newAuthor, long newIndex) {
		super(newAuthor, newIndex);
	}
	
	public OwnIdentity getAuthor() {
		return (OwnIdentity)super.getAuthor();
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
			if(mIsBeingInserted || mWasInserted)
				throw new IllegalStateException("Trying to add a message to a message list which is already being inserted.");
			
			if(newMessage.getAuthor() != mAuthor)
				throw new IllegalStateException("Trying to add a message with wrong author " + newMessage.getAuthor() + " to an own message list of " + mAuthor);
			
			OwnMessageReference ref = new OwnMessageReference(newMessage);
			mMessages.add(ref);
			if(mMessages.size() > 1 && fitsIntoContainer() == false) {
				mMessages.remove(ref);
				throw new IllegalStateException("OwnMessageList is full."); /* TODO: Chose a better exception */
			}
			
			ref.setMessageList(this);			
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
		mIsBeingInserted = true;
		storeWithoutCommit();
	}

	/**
	 * Stores this OwnMessageList in the database without committing the transaction.
	 */
	public synchronized void cancelInsert() {
		if(mWasInserted)
			throw new RuntimeException("The OwnMessageList was already inserted.");
		
		mIsBeingInserted = false;
		storeWithoutCommit();
	}
	
	public synchronized boolean wasInserted() {
		return mWasInserted;
	}

	/**
	 * Stores this OwnMessageList in the database without committing the transaction.
	 */
	public synchronized void markAsInserted() {
		if(mIsBeingInserted == false)
			throw new RuntimeException("Trying to mark a MessageList as 'inserted' which was not marked as 'being inserted': This MUST NOT happen:" +
					" Messages can still be added to a list if it is not marked as being inserted. If it is being inserted already without being marked," +
					" the messages will not be contained in the actually inserted message list."); 
		
		if(mWasInserted)
			Logger.error(this, "markAsInserted called for an already inserted message list: " + this);
			
		mWasInserted = true;
		mIsBeingInserted = false;
		storeWithoutCommit();
	}

}
