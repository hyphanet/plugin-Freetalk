/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

// @IndexedField // I can't think of any query which would need to get all OwnMessage objects.
public abstract class OwnMessage extends Message {

	protected OwnMessage(Freetalk myFreetalk, MessageURI newURI, FreenetURI newFreenetURI, MessageID newID, MessageList newMessageList, MessageURI newThreadURI,
			MessageURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, Identity newAuthor, String newTitle, Date newDate,
			String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		super(myFreetalk, newURI, newFreenetURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText,
				newAttachments);
	}

	@Override public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		checkedActivate(1);
		
		if(!(mAuthor instanceof OwnIdentity))
			throw new IllegalStateException("mAuthor is no OwnIdentity: " + mAuthor);
		
		if(mMessageList == null) {
			if(mFreenetURI != null)
				throw new IllegalStateException("mMessageList == null but mFreenetURI == " + getFreenetURI());
			
			if(mURI != null)
				throw new IllegalStateException("mMessageList == null but mURI == " + getURI());
		} // else is handled by super.databaseIntegrityTest();
	}
	

	/**
	 * @return The URI of the Message if it was inserted already, null if it was not inserted yet.
	 */
	@Override
	public synchronized MessageURI getURI() {
		checkedActivate(1);
		if(mURI != null)
			mURI.initializeTransient(mFreetalk);
		return mURI;
	}
	
	public abstract MessageURI calculateURI() throws NoSuchMessageListException;

	/**
	 * Generate the insert URI for a message.
	 */
	public abstract FreenetURI getInsertURI();

	/**
	 * @throws RuntimeException If the message was not inserted yet and therefore the real URI is unknown.
	 * @return The CHK URI of the message.
	 */
	@Override public synchronized FreenetURI getFreenetURI() {
		checkedActivate(1);
		
		if(mFreenetURI == null)
			throw new RuntimeException("getFreenetURI() called on the not inserted message " + this);
		
		checkedActivate(mFreenetURI, 2);
		return mFreenetURI;
	}
	
	// TODO: Remove the debug code if we are sure that db4o works
	public synchronized boolean testFreenetURIisNull() {
		checkedActivate(1);
		
		if(mFreenetURI != null) {
			Logger.error(this, "Db4o bug: constrain(null).identity() did not work for " + this);
			return false;
		}
		
		return true;
	}

	/**
	 * Set the message list at which this message is listed to the given one.
	 * Stores this OwnMessage in the database without committing the transaction.
	 */
	public synchronized void setMessageList(OwnMessageList newMessageList) {
		checkedActivate(1);
		
		mMessageList = newMessageList;
		try {
			if(mURI != null) {
				mURI.initializeTransient(mFreetalk);
				mURI.deleteWithoutCommit();
			}
			
			mURI = calculateURI();
		} catch (NoSuchMessageListException e) {
			throw new RuntimeException(e);
		}
		storeWithoutCommit();
	}

	@Override public synchronized MessageList getMessageList() throws NoSuchMessageListException {
		checkedActivate(1);
		if(mMessageList == null)
			throw new NoSuchMessageListException("");
		
		mMessageList.initializeTransient(mFreetalk);
		return mMessageList;
	}
	
	/**
	 * Not allowed for OwnMessage
	 * @throws UnsupportedOperationException Always.
	 */
	@Override
	public void setParent(Message message) {
		throw new UnsupportedOperationException();
		// ATTENTION: If you ever allow this please take care of message deletion routines which assume that OwnMessages do not have mParent!=null
	}

	/**
	 * Not allowed for OwnMessage
	 * @throws UnsupportedOperationException Always.
	 */
	@Override
	public void setThread(Message thread) {
		throw new UnsupportedOperationException();
		// ATTENTION: If you ever allow this please take care of message deletion routines which assume that OwnMessages do not have mThread!=null
	}

	/**
	 * Tells whether this message was inserted already. If a message was inserted, this does not necessarily mean that it will be visible to anyone:
	 * The message might only become visible if the message list which lists it has been inserted. This is implementation dependent, for example {@see WoTOwnMessage}.
	 */
	public synchronized boolean wasInserted() {
		checkedActivate(1);
		return (mFreenetURI != null);
	}

	/**
	 * Marks the message as inserted by storing the given URI as it's URI.
	 * Stores this OwnMessage in the database without committing the transaction. 
	 */
	public synchronized void markAsInserted(FreenetURI myFreenetURI) {
		checkedActivate(1);
		if(mFreenetURI != null)
			throw new RuntimeException("The message was inserted already.");
		mFreenetURI = myFreenetURI;
		storeWithoutCommit();
	}

	@Override public String toString() {
    	if(mDB != null) {
    		MessageURI uri = getURI();
    		if(uri != null)
    			return uri.toString();
    		
    		FreenetURI freenetURI = mFreenetURI; // We cannot use the getter here because it throws if the URI is null.
    		if(freenetURI != null)
    			return freenetURI.toString();
    		
    		return "ID:" + getID() + " (no URI present, inserted=" + wasInserted() + ")";
    	}
    	
		// We do not throw a NPE because toString() is usually used in logging, we want the logging to be robust
		
		Logger.error(this, "toString() called before initializeTransient()!");
		
		return super.toString() + " (intializeTransient() not called!)";
    }
}
