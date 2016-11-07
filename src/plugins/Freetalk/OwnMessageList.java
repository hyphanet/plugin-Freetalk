/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Iterator;

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

		@Override public void databaseIntegrityTest() throws Exception {
			super.databaseIntegrityTest(); 
		}

	}

	@Override public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		checkedActivate(1);
		
		if(!(mAuthor instanceof OwnIdentity))
			throw new IllegalStateException("mAuthor is no OwnIdentity: " + getAuthor());
			
		if(mIsBeingInserted && mWasInserted)
			throw new IllegalStateException("mIsBeingInserted == true and mWasInserted == true");
		
		for(MessageReference ref : getMessages()) {
			if(!(ref instanceof OwnMessageReference))
				throw new IllegalStateException("Found non-own MessageReference: " + ref);
		}
		
		// TODO: Validate mMessages content. Size is validated by parent
	}

	public OwnMessageList(OwnIdentity newAuthor, long newIndex) {
		super(newAuthor, newIndex);
	}

	@Override public OwnIdentity getAuthor() {
		return (OwnIdentity)super.getAuthor();
	}

	/**
	 * Get the SSK insert URI of this message list.
	 * @return
	 */
	public FreenetURI getInsertURI() {
		return generateURI(getAuthor().getInsertURI(), getIndex()).sskForUSK();
	}

	/**
	 * Add an <code>OwnMessage</code> to this <code>MessageList</code>.
	 * This function synchronizes on the <code>MessageList</code> and the given message.
	 * Stores the given message and this OwnMessageList in the database without committing the transaction.
	 * 
	 * @throws Exception If the message list is full.
	 */
	public synchronized void addMessage(OwnMessage newMessage) {
		checkedActivate(1);
		synchronized(newMessage) {
			if(mIsBeingInserted || mWasInserted)
				throw new IllegalStateException("Trying to add a message to a message list which is already being inserted.");
			
			if(newMessage.getAuthor() != mAuthor)
				throw new IllegalStateException("Trying to add a message with wrong author " + newMessage.getAuthor() + " to an own message list of " + getAuthor());
			
			final OwnMessageReference ref = new OwnMessageReference(newMessage);
			ref.initializeTransient(mFreetalk);
			checkedActivate(mMessages, 2);
			mMessages.add(ref);
			if(mMessages.size() > 1 && fitsIntoContainer() == false) {
				mMessages.remove(ref);
				throw new IllegalStateException("OwnMessageList is full."); /* TODO: Chose a better exception */
			}
			
			ref.setMessageList(this);			
			newMessage.setMessageList(this);
		}
	}
	
	// TODO: This has not been tested in practice because we have no code for aborting OwnMessage inserts which are added to a message list already
	// and the OwnIdentity-deletion code does not use it.
	public synchronized void removeMessageWithoutCommit(OwnMessage message) {
		checkedActivate(1);
		synchronized(message) {
			if(!message.wasInserted())
				throw new IllegalStateException("Message was not inserted yet, so it has no URI, cannot be in MessageList");
			
			if(mIsBeingInserted)
				throw new IllegalStateException("MessageList is being inserted, please abort the insert first.");
			
			checkedActivate(mMessages, 2);
			
			for(Iterator<MessageReference> iter = mMessages.iterator(); iter.hasNext(); ) {
				MessageReference ref = iter.next();
				ref.initializeTransient(mFreetalk);
				
				if(ref.getURI().equals(message.getFreenetURI())) {
					iter.remove();
				}
			
				ref.setMessageList(null);
				ref.deleteWithoutCommit();
			}
			
			message.setMessageList(null);
			message.storeWithoutCommit();
		}
	}
	
	public synchronized int getMessageCount() {
		checkedActivate(1);
		checkedActivate(mMessages, 2);
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
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
		mIsBeingInserted = true;
		storeWithoutCommit();
	}

	/**
	 * Stores this OwnMessageList in the database without committing the transaction.
	 */
	public synchronized void cancelInsert() {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
		if(mWasInserted)
			throw new RuntimeException("The OwnMessageList was already inserted.");
		
		mIsBeingInserted = false;
		storeWithoutCommit();
	}
	
	public synchronized boolean wasInserted() {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
		return mWasInserted;
	}

	/**
	 * Stores this OwnMessageList in the database without committing the transaction.
	 */
	public synchronized void markAsInserted() {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
		
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
