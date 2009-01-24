/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.keys.FreenetURI;

public abstract class OwnMessage extends Message {

	protected OwnMessage(FreenetURI newURI, FreenetURI newRealURI, String newID, MessageList newMessageList, FreenetURI newThreadURI,
			FreenetURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, FTIdentity newAuthor, String newTitle, Date newDate,
			String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		super(newURI, newRealURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText,
				newAttachments);
	}

	/* Override for synchronization */
	@Override
	public synchronized FreenetURI getURI() {
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

	public synchronized void setMessageList(OwnMessageList newMessageList) {
		mMessageList = newMessageList;
	}

	public synchronized boolean wasInserted() {
		return (mRealURI != null);
	}

	public synchronized void markAsInserted(FreenetURI myRealURI) {
		mRealURI = myRealURI;
	}

}
