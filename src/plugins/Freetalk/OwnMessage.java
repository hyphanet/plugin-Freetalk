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

public abstract class OwnMessage extends Message {

	protected OwnMessage(MessageURI newURI, FreenetURI newRealURI, String newID, MessageList newMessageList, MessageURI newThreadURI,
			MessageURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, FTIdentity newAuthor, String newTitle, Date newDate,
			String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		super(newURI, newRealURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText,
				newAttachments);
	}

	/* Override for synchronization */
	@Override
	public synchronized MessageURI getURI() {
		return mURI;
	}

	/**
	 * Generate the insert URI for a message.
	 */
	public abstract FreenetURI getInsertURI();

	/**
	 * @throws RuntimeException If the message was not inserted yet and therefore the real URI is unknown.
	 * @return The CHK URI of the message.
	 */
	public synchronized FreenetURI getRealURI() {
		if(mRealURI == null)
			throw new RuntimeException("getRealURI() called on the not inserted message " + this);
		
		return mRealURI;
	}

	/**
	 * Set the message list at which this message is listed to the given one.
	 * Stores this OwnMessage in the database without committing the transaction.
	 */
	public synchronized void setMessageList(OwnMessageList newMessageList) {
		mMessageList = newMessageList;
		storeWithoutCommit();
	}

	/**
	 * Tells whether this message was inserted already. If a message was inserted, this does not necessarily mean that it will be visible to anyone:
	 * The message might only become visible if the message list which lists it has been inserted. This is implementation dependent, for example {@see WoTOwnMessage}.
	 */
	public synchronized boolean wasInserted() {
		return (mRealURI != null);
	}

	/**
	 * Marks the message as inserted by storing the given URI as it's URI.
	 * Stores this OwnMessage in the database without committing the transaction. 
	 */
	public synchronized void markAsInserted(FreenetURI myRealURI) {
		mRealURI = myRealURI;
		storeWithoutCommit();
	}

    public String toString() {
    	if(mDB != null) {
    		MessageURI uri = getURI();
    		if(uri != null)
    			return uri.toString();
    		
    		FreenetURI realURI = getRealURI();
    		if(realURI != null)
    			return realURI.toString();
    		
    		return "ID:" + getID() + " (no URI present, inserted=" + wasInserted() + ")";
    	}
    	
		// We do not throw a NPE because toString() is usually used in logging, we want the logging to be robust
		
		Logger.error(this, "toString() called before initializeTransient()!");
		
		return super.toString() + " (intializeTransient() not called!, message URI may be null, here it is: " + mURI + ")";
    }
}
