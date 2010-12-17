/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

// @IndexedField // I can't think of any query which would need to get all OwnMessage objects.
public abstract class OwnMessage extends Message {

	protected OwnMessage(MessageURI newURI, FreenetURI newFreenetURI, MessageID newID, MessageList newMessageList, MessageURI newThreadURI,
			MessageURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, Identity newAuthor, String newTitle, Date newDate,
			String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		super(newURI, newFreenetURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText,
				newAttachments);
	}
	
	public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		checkedActivate(3);
		
		if(!(mAuthor instanceof OwnIdentity))
			throw new IllegalStateException("mAuthor is no OwnIdentity: " + mAuthor);
		
		if(mMessageList == null) {
			if(mFreenetURI != null)
				throw new IllegalStateException("mMessageList == null but mFreenetURI == " + mFreenetURI);
			
			if(mURI != null)
				throw new IllegalStateException("mMessageList == null but mURI == " + mURI);
		} // else is handled by super.databaseIntegrityTest();
	}
	

	// TODO: I doubt that this is needed, was probably a quickshot. Remove it if not and make the parent function final.
	/* Override for synchronization */	
	@Override
	public synchronized MessageURI getURI() {
		return mURI;
	}
	
	public abstract MessageURI calculateURI();

	/**
	 * Generate the insert URI for a message.
	 */
	public abstract FreenetURI getInsertURI();

	/**
	 * @throws RuntimeException If the message was not inserted yet and therefore the real URI is unknown.
	 * @return The CHK URI of the message.
	 */
	public synchronized FreenetURI getFreenetURI() {
		if(mFreenetURI == null)
			throw new RuntimeException("getFreenetURI() called on the not inserted message " + this);
		
		return mFreenetURI;
	}
	
	// TODO: Remove the debug code if we are sure that db4o works
	public synchronized boolean testFreenetURIisNull() {
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
		mMessageList = newMessageList;
		mURI = calculateURI();
		storeWithoutCommit();
	}

	/**
	 * Tells whether this message was inserted already. If a message was inserted, this does not necessarily mean that it will be visible to anyone:
	 * The message might only become visible if the message list which lists it has been inserted. This is implementation dependent, for example {@see WoTOwnMessage}.
	 */
	public synchronized boolean wasInserted() {
		return (mFreenetURI != null);
	}

	/**
	 * Marks the message as inserted by storing the given URI as it's URI.
	 * Stores this OwnMessage in the database without committing the transaction. 
	 */
	public synchronized void markAsInserted(FreenetURI myFreenetURI) {
		mFreenetURI = myFreenetURI;
		storeWithoutCommit();
	}

    public String toString() {
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
		
		return super.toString() + " (intializeTransient() not called!, message URI may be null, here it is: " + mURI + ")";
    }
}
