/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import plugins.Freetalk.Board;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTMessage;
import plugins.Freetalk.WoT.WoTMessageList;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTMessageURI;
import plugins.Freetalk.WoT.WoTMessageXML;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import freenet.keys.FreenetURI;


public class MessageXMLTest extends DatabaseBasedTest {
	
	private MessageManager mMessageManager;
	private FreenetURI mMessageRealURI;
	private WoTMessageList mMessageList;
	private WoTMessage mMessage;
	
	private String mHardcodedEncodedMessage;
		
	public void setUp() throws Exception {
		super.setUp();
		
		mMessageManager = new WoTMessageManager(db, null);
		
		Board myBoard = new Board("en.board1");
		HashSet<Board> myBoards = new HashSet<Board>();
		myBoards.add(myBoard);
		myBoards.add(new Board("en.board2"));
		
		FreenetURI authorRequestSSK = new FreenetURI("SSK@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQACAAE/");
		FreenetURI authorInsertSSK = new FreenetURI("SSK@Ykhv0x0K8jtrgOlqWVS4S2Jvmnm64zv5voNjMfz1nYI,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQECAAE/");
		WoTIdentity myAuthor = new WoTOwnIdentity(WoTIdentity.getUIDFromURI(authorRequestSSK), authorRequestSSK, authorInsertSSK, "Nickname");
		
		FreenetURI myThreadRealURI = new FreenetURI("CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8");
		String myThreadID = "afe6519b-7fb2-4533-b172-1f966e79d127" + "@" + myAuthor.getUID();
		
		mMessageRealURI = new FreenetURI("CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8");
		String myMessageID = "2a3a8e7e-9e53-4978-a8fd-17b2d92d949c" + "@" + myAuthor.getUID();
		
		List<MessageList.MessageReference> messageReferences = new ArrayList<MessageList.MessageReference>(2);
		for(Board board : myBoards) {
			messageReferences.add(new MessageList.MessageReference(myThreadID, myThreadRealURI, board));
			messageReferences.add(new MessageList.MessageReference(myMessageID, mMessageRealURI, board));
		}
		mMessageList = new WoTMessageList(myAuthor, WoTMessageList.assembleURI(authorRequestSSK, 123), messageReferences);	
		
		List<Attachment> attachments = new ArrayList<Attachment>();
		attachments.add(new Attachment(new FreenetURI("KSK@attachment1"), 10001));
		attachments.add(new Attachment(new FreenetURI("KSK@attachment2"), 10002));
		
		mMessage = WoTMessage.construct(mMessageList, mMessageRealURI, myMessageID, new WoTMessageURI(mMessageList.getURI(), myMessageID),
				new WoTMessageURI(mMessageList.getURI(), myThreadID), myBoards, myBoard, myAuthor,
				"Message title", new Date(109, 4, 3, 16, 15, 14), "Message body\nNew line", attachments);

		mHardcodedEncodedMessage = new String(
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
			"<Freetalk-testing>" +
			"<Message version=\"1\">" +
			"<MessageID><![CDATA[2a3a8e7e-9e53-4978-a8fd-17b2d92d949c@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk]]></MessageID>" + 
			"<Subject><![CDATA[Message title]]></Subject>" +
			"<Date>2009-05-03</Date>" +
			"<Time>16:15:14</Time>" +
			"<Boards>" +
			"<Board><![CDATA[en.board1]]></Board>" +
			"<Board><![CDATA[en.board2]]></Board>" +
			"</Boards>" +
			"<ReplyBoard><![CDATA[en.board1]]></ReplyBoard>" +
			"<InReplyTo>" +
			"<Message>" +
			"<Order>0</Order>" +
			"<MessageID><![CDATA[afe6519b-7fb2-4533-b172-1f966e79d127@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk]]></MessageID>" +
			"<MessageURI><![CDATA[SSK@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQACAAE/Freetalk-testing%7cMessageList-123#afe6519b-7fb2-4533-b172-1f966e79d127]]></MessageURI>" +
			"</Message>" +
			"<Thread>" +
			"<MessageID><![CDATA[2a3a8e7e-9e53-4978-a8fd-17b2d92d949c@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk]]></MessageID>" +
			"<MessageURI><![CDATA[SSK@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQACAAE/Freetalk-testing%7cMessageList-123#2a3a8e7e-9e53-4978-a8fd-17b2d92d949c]]></MessageURI>" +
			"</Thread>" +
			"</InReplyTo>" +
			"<Body><![CDATA[Message body" +
			"New line]]></Body>" +
			"<Attachments>" +
			"<File>" +
			"<Key><![CDATA[KSK@attachment1]]></Key>" +
			"<Size><![CDATA[10001]]></Size>" +
			"</File>" +
			"<File>" +
			"<Key><![CDATA[KSK@attachment2]]></Key>" +
			"<Size><![CDATA[10002]]></Size>" +
			"</File>" +
			"</Attachments>" +
			"</Message>" +
			"</Freetalk-testing>" 
			);
	}

	public void testEncoding() throws Exception {
		ByteArrayOutputStream encodedMessage = new ByteArrayOutputStream(4096);
		WoTMessageXML.encode(mMessage, encodedMessage);
		
		assertEquals(mHardcodedEncodedMessage, encodedMessage.toString().replaceAll("[\r\n]", ""));
	}
	
	/* FIXME: The following code is the XML decoding test. It works on a recent java VM, but on java 1.5 and therefore on Emu a NPE is thrown. I suppose
	 * this is due to a bug in the SAXParser of java 1.5. */
	/*
	public void testDecoding() throws Exception {
		ByteArrayInputStream is = new ByteArrayInputStream(mHardcodedEncodedMessage.getBytes());
		ByteArrayOutputStream decodedAndEncodedMessage = new ByteArrayOutputStream(4096);
		WoTMessageXML.encode(WoTMessageXML.decode(mMessageManager, is, mMessageList, mMessageRealURI), decodedAndEncodedMessage);		
		
		assertEquals(mHardcodedEncodedMessage, decodedAndEncodedMessage.toString().replaceAll("[\r\n]", ""));
	}
	*/
}
