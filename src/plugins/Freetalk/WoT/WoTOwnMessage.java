package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import plugins.Freetalk.Board;
import plugins.Freetalk.Identity;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.keys.FreenetURI;
import freenet.support.Base64;

//@Indexed // I can't think of any query which would need to get all WoTOwnMessage objects.
public final class WoTOwnMessage extends OwnMessage {

	public static WoTOwnMessage construct(WoTMessageURI myParentThreadURI, Message newParentMessage, Set<Board> newBoards, Board newReplyToBoard, 
			OwnIdentity newAuthor, String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		
		return new WoTOwnMessage(myParentThreadURI, newParentMessage, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}

	protected WoTOwnMessage(WoTMessageURI myParentThreadURI, Message newParentMessage, Set<Board> newBoards, Board newReplyToBoard, OwnIdentity newAuthor,
			String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		
		// TODO: Add some (configurable?) randomization to the date of the message to make correlation attacks more difficult.
		super(null, null, generateRandomID(newAuthor), null, myParentThreadURI,
			  (newParentMessage == null ? null : newParentMessage.getURI()),
			  newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}

	protected static String generateRandomID(Identity author) {
		return UUID.randomUUID() + "@" + Base64.encode(author.getRequestURI().getRoutingKey());
	}

	/**
	 * Generate the insert URI for a message.
	 */
	public synchronized FreenetURI getInsertURI() {
		return FreenetURI.EMPTY_CHK_URI;
	}

}
