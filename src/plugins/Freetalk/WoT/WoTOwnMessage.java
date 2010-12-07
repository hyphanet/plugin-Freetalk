/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Board;
import plugins.Freetalk.Message;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;

/**
 * @author xor (xor@freenetproject.org)
 */
//@IndexedField // I can't think of any query which would need to get all WoTOwnMessage objects.
public final class WoTOwnMessage extends OwnMessage {

	public static WoTOwnMessage construct(WoTMessageURI myParentThreadURI, Message newParentMessage, Set<Board> newBoards, Board newReplyToBoard, 
			OwnIdentity newAuthor, String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		
		return new WoTOwnMessage(myParentThreadURI, newParentMessage, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}

	protected WoTOwnMessage(WoTMessageURI myParentThreadURI, Message newParentMessage, Set<Board> newBoards, Board newReplyToBoard, OwnIdentity newAuthor,
			String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		
		// TODO: Add some (configurable?) randomization to the date of the message to make correlation attacks more difficult.
		super(null, null, MessageID.constructRandomID(newAuthor), null, myParentThreadURI,
			  (newParentMessage == null ? null : newParentMessage.getURI()),
			  newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}
	
	@Override
	public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		if(!(super.getURI() instanceof WoTMessageURI))
			throw new IllegalStateException("super.getURI() == " + super.getURI());
		
		if(!(super.getAuthor() instanceof WoTOwnIdentity))
			throw new IllegalStateException("super.getAuthor() == " + super.getAuthor());
		
		if(!(super.getThreadURI() instanceof WoTMessageURI))
			throw new IllegalStateException("super.getThreadURI() == " + super.getThreadURI());
		
		if(!(super.getParentURI() instanceof WoTMessageURI))
			throw new IllegalStateException("super.getParentURI() == " + super.getParentURI());
		
		try {
			if(!(super.getThread() instanceof WoTMessage))
				throw new IllegalStateException("super.getThread() == " + super.getThread());
		} catch(NoSuchMessageException e) {}
		
		try {
			if(!(super.getParent() instanceof WoTMessage))
				throw new IllegalStateException("super.getParent() == " + super.getParent());
		} catch(NoSuchMessageException e) {}
		
		if(!(super.getMessageList() instanceof WoTOwnMessageList))
			throw new IllegalStateException("super.getMessageList() == " + super.getMessageList());
	}

	/**
	 * Generate the insert URI for a message.
	 */
	public synchronized FreenetURI getInsertURI() {
		return FreenetURI.EMPTY_CHK_URI;
	}

}
