package plugins.Freetalk.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;

import junit.framework.TestCase;
import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.MessageXML;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import freenet.keys.FreenetURI;

public class MessageXMLTest extends TestCase {

	public void testXML() throws Exception {
		ObjectContainer db = Db4o.openFile("test.db4o"); /* FIXME: This HAS to be flushed */
		MessageManager messageManager = new WoTMessageManager(db);
		Board board = new Board(messageManager, "board");
		HashSet<Board> boards = new HashSet<Board>();
		boards.add(board);
		FTOwnIdentity identity = new WoTOwnIdentity("uid1", new FreenetURI("KSK@identityURI"), new FreenetURI("KSK@identityInsertURI"), "nickname");
		Message parentThread = new Message(new FreenetURI("KSK@" + Freetalk.PLUGIN_TITLE + "|" + "Message" + "-" + "1"), null, null, boards, board, identity, "title1", new Date(), "text1", null);
		List<Attachment> attachments = new ArrayList<Attachment>();
		attachments.add(new Attachment(new FreenetURI("KSK@attachment1"), 10001));
		attachments.add(new Attachment(new FreenetURI("KSK@attachment2"), 10002));
		OwnMessage originalMessage = new OwnMessage(parentThread, parentThread, boards, board, identity, "title2", new Date(), 24, "text2", attachments);

		ByteArrayOutputStream encodedMessage = new ByteArrayOutputStream(4096);
		MessageXML.encode(originalMessage, encodedMessage);
		
		ByteArrayInputStream is = new ByteArrayInputStream(encodedMessage.toByteArray());
		ByteArrayOutputStream encodedDecodedEncodedMessage = new ByteArrayOutputStream(4096);
		MessageXML.encode(MessageXML.decode(messageManager, is, identity, originalMessage.getURI()), encodedDecodedEncodedMessage);		
		
		assertTrue("Message mismatch: Encoded:\n" + encodedMessage.toString() + "\nDecoded: " + encodedDecodedEncodedMessage.toString(),
				Arrays.equals(encodedMessage.toByteArray(), encodedDecodedEncodedMessage.toByteArray()));
	}
}
