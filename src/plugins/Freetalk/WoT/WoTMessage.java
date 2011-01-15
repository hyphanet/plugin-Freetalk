/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Board;
import plugins.Freetalk.Identity;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;

/**
 * This is the current implementation of the message class in Freetalk using the WoT plugin.
 * 
 * WoTMessages are inserted by Freetalk with a CHK URI as plaintext XML. The CHK URIs are published in message lists which are inserted under the
 * SSK URI of the message author. It is important to know that CHK URIs are not signed and SSK URIs are signed. One would assume that it would
 * make sense to insert messages as SSK so you can hand over the URI of a message to someone else and he can verify that they really come from
 * the identity which is specified in the XML. But signed URIs of messages are provided by Freetalk by appending the message ID of the chosen
 * message to the SSK URI of the message list in which it is published (separated by "#").
 * 
 * The reason why messages are inserted as CHK is the following: A single SSK block is limited to 1 KiB in size. This will be exceeded by most
 * messages due to quoting. When inserting something as SSK which is more than 1 KiB, an additional CHK block will be inserted which is 32 KiB.
 * Therefore, if messages were inserted as SSK, most of them would be a SSK block and a CHK block. So downloading a message would usually result
 * in the lookup of two blocks instead of one and lookups are very expensive for the network.
 * 
 * Further, it does even more make sense to insert messages as CHK when it comes to message lists: Message lists tell the URIs of several messages
 * and the board in which they were posted. Message lists are necessary because we need to provide the user of Freetalk with the ability to chose
 * the board from which he wants to download messages. If that was not possible, everyone would download all messages which would generate too
 * much load on the network. Because message lists are downloaded before the messages, they have to be inserted as SSK/USK anyway (we need to be
 * able to guess the URIs of new message lists), so the message lists are signed already and there is no need anymore to sign the messages, 
 * therefore messages can be inserted as CHK.
 * 
 * Activation policy: Class WoTMessage does automatic activation on its own.
 * This means that objects of class WoTMessage can be activated to a depth of only 1 when querying them from the database.
 * All methods automatically activate the object to any needed higher depth.
 * 
 * @author xor
 */
// @IndexedField // I can't think of any query which would need to get all WoTMessage objects.
public final class WoTMessage extends Message {

	/**
	 * Constructor for received messages.
	 */
	public static WoTMessage construct(MessageList newMessageList, FreenetURI myFreenetURI, MessageID newID, WoTMessageURI newThreadURI, WoTMessageURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, Identity newAuthor, String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		// The Message constructor allows MessageList to be null, we don't since every WoT message should be obtained from a MessageList
		if(newMessageList == null)
			throw new NullPointerException("MessageList is null");
		
		return new WoTMessage(WoTOwnMessage.calculateURI(newMessageList, newID), myFreenetURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}

	protected WoTMessage(WoTMessageURI newURI, FreenetURI newFreenetURI, MessageID newID, MessageList newMessageList, WoTMessageURI newThreadURI,
			WoTMessageURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, Identity newAuthor, String newTitle, Date newDate,
			String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		super(newURI, newFreenetURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText,
				newAttachments);
	}
	
	@Override
	public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		if(!(super.getURI() instanceof WoTMessageURI))
			throw new IllegalStateException("super.getURI() == " + super.getURI());
		
		if(!(super.getAuthor() instanceof WoTIdentity))
			throw new IllegalStateException("super.getAuthor() == " + super.getAuthor());
		
		try {
			if(!(super.getThreadURI() instanceof WoTMessageURI))
				throw new IllegalStateException("super.getThreadURI() == " + super.getThreadURI());
			
			if(!(super.getThread() instanceof WoTMessage))
				throw new IllegalStateException("super.getThread() == " + super.getThread());
		} catch(NoSuchMessageException e) {}
		
		try {
			if(!(super.getParentURI() instanceof WoTMessageURI))
				throw new IllegalStateException("super.getParentURI() == " + super.getParentURI());
			
			if(!(super.getParent() instanceof WoTMessage))
				throw new IllegalStateException("super.getParent() == " + super.getParent());
		} catch(NoSuchMessageException e) {}
		
		if(!(super.getMessageList() instanceof WoTMessageList))
			throw new IllegalStateException("super.getMessageList() == " + super.getMessageList());
	}
	
	@Override
	public WoTMessageURI getURI() { /* Not synchronized because only OwnMessage might change the URI */
		return (WoTMessageURI)super.getURI();
	}

}
