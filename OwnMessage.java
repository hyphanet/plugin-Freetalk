/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.exceptions.InvalidParameterException;

import freenet.keys.FreenetURI;

public final class OwnMessage extends Message {
	
	public static OwnMessage construct(Message newParentThread, Message newParentMessage, Set<Board> newBoards, Board newReplyToBoard, FTOwnIdentity newAuthor,
			String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		return new OwnMessage(newParentThread, newParentMessage, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}

	protected OwnMessage(Message newParentThread, Message newParentMessage, Set<Board> newBoards, Board newReplyToBoard, FTOwnIdentity newAuthor,
			String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		super(null, null, generateRandomID(newAuthor), null, (newParentThread == null ? null : newParentThread.getURI()),
			  (newParentMessage == null ? null : newParentMessage.getURI()),
			  newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}

	/* Override for synchronization */
	@Override
	public synchronized FreenetURI getURI() {
		return mURI;
	}
	

	/**
	 * Generate the insert URI for a message. The URI is constructed as:
	 * CHK@hash/Freetalk|Message|id.xml
	 * where id is the message ID.
	 */
	public synchronized FreenetURI getInsertURI() {
		return new FreenetURI("CHK", Freetalk.PLUGIN_TITLE + "|" + "Message" + "|" + mID + ".xml");
	}
	
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
