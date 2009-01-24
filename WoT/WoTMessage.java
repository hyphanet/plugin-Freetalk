package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.keys.FreenetURI;

public final class WoTMessage extends Message {

	/**
	 * Constructor for received messages.
	 */
	public static WoTMessage construct(MessageList newMessageList, FreenetURI myRealURI, String newID, FreenetURI newThreadURI, FreenetURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, FTIdentity newAuthor, String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		if (newMessageList == null || newBoards == null || newAuthor == null)
			throw new IllegalArgumentException();
		
		if (newMessageList.getAuthor() != newAuthor)
			throw new InvalidParameterException("Trying to construct a message of " + newAuthor + " with a messagelist which belong to a different author: " + newMessageList.getAuthor());
		
		return new WoTMessage(calculateURI(newMessageList, newID), myRealURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}

	protected WoTMessage(FreenetURI newURI, FreenetURI newRealURI, String newID, MessageList newMessageList, FreenetURI newThreadURI,
			FreenetURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, FTIdentity newAuthor, String newTitle, Date newDate,
			String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		super(newURI, newRealURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText,
				newAttachments);
	}

	public static FreenetURI calculateURI(MessageList myMessageList, String myID) {
		FreenetURI uri = myMessageList.getURI();
		uri = uri.setDocName(uri.getDocName() + "#" + myID);
		return uri;
	}

	/**
	 * Get the URI of the message. This returns the SSK URI of the WoTMessageList with the ID of the message attached.
	 * @see WoTMessage.calculateURI()
	 */
	public FreenetURI getURI() { /* Not synchronized because only OwnMessage might change the URI */
		return mURI;
	}
}
