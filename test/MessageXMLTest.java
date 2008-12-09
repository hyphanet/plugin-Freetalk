package plugins.Freetalk.test;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import junit.framework.TestCase;
import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.MessageXML;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.keys.FreenetURI;

public class MessageXMLTest extends TestCase {

	public void testXML() throws MalformedURLException, InvalidParameterException, TransformerException, ParserConfigurationException {
		MessageManager messageManager = new WoTMessageManager();
		Board board = new Board(messageManager, "board");
		HashSet<Board> boards = new HashSet<Board>();
		boards.add(board);
		FTOwnIdentity identity = new WoTOwnIdentity("uid1", new FreenetURI("KSK@identityURI"), new FreenetURI("KSK@identityInsertURI"), "nickname");
		Message parentThread = new Message(new FreenetURI("KSK@messageURI"), null, null, boards, board, identity, "title1", new Date(), 23, "text1", null);
		List<Attachment> attachments = new ArrayList<Attachment>();
		attachments.add(new Attachment(new FreenetURI("KSK@attachment1"), 10001));
		attachments.add(new Attachment(new FreenetURI("KSK@attachment2"), 10002));
		OwnMessage originalMessage = new OwnMessage(parentThread, parentThread, boards, board, identity, "title2", new Date(), 24, "text2", attachments);
		MessageXML.encode(originalMessage, System.out);
		
		/* FIXME: As soon as the decoder is available, decode the message again and check whether the decoded message equals the original one */
	}

}
