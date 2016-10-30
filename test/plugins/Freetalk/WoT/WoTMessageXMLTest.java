/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import javax.activation.MimeType;

import plugins.Freetalk.Board;
import plugins.Freetalk.DatabaseBasedTest;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Version;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.Message.MessageID;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.Persistent;
import freenet.keys.FreenetURI;
import freenet.support.CurrentTimeUTC;


public class WoTMessageXMLTest extends DatabaseBasedTest {

	private MessageManager mMessageManager;
	private WoTMessageXML mXML;
	
	private FreenetURI mMessageFreenetURI;
	
	private String mMessageListID;
	private String mMessageID;
	
	private String mHardcodedEncodedMessage;
		
	@Override public void setUp() throws Exception {
		super.setUp();

		mMessageManager = mFreetalk.getMessageManager();
		mXML = new WoTMessageXML();
		
		Board myBoard = mMessageManager.getOrCreateBoard("eng.board1");
		HashSet<Board> myBoards = new HashSet<Board>();
		myBoards.add(myBoard);
		myBoards.add(mMessageManager.getOrCreateBoard("eng.board2"));
		
		FreenetURI authorRequestSSK = new FreenetURI("SSK@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQACAAE/");
		FreenetURI authorInsertSSK = new FreenetURI("SSK@Ykhv0x0K8jtrgOlqWVS4S2Jvmnm64zv5voNjMfz1nYI,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQECAAE/");
		WoTIdentity myAuthor = new WoTOwnIdentity(WoTIdentity.getIDFromURI(authorRequestSSK), authorRequestSSK, authorInsertSSK, "Nickname");
		myAuthor.initializeTransient(mFreetalk);
		myAuthor.storeAndCommit();
		
		FreenetURI myThreadFreenetURI = new FreenetURI("CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8");
		MessageID myThreadID = MessageID.construct("afe6519b-7fb2-4533-b172-1f966e79d127" + "@" + myAuthor.getID());
		
		FreenetURI myParentFreenetURI = new FreenetURI("CHK@H4nfdTqgQUQ0CkdPzvrs2F~IIkjOCnfEn~S042jUxuw,wkCrKtmvmYQzuo3f4v2JlB87wJkK0dspmGJ~ivztYP8,AAIA--8");
		MessageID myParentID = MessageID.construct("d5b0dcc4-91cb-4870-8ab9-8588e895fa5d" + "@" + myAuthor.getID());
		
		mMessageFreenetURI = new FreenetURI("CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8");
		MessageID myMessageID = MessageID.construct("2a3a8e7e-9e53-4978-a8fd-17b2d92d949c" + "@" + myAuthor.getID());
		
		List<MessageList.MessageReference> messageReferences = new ArrayList<MessageList.MessageReference>(2);
		for(Board board : myBoards) {
			messageReferences.add(new MessageList.MessageReference(myThreadID, myThreadFreenetURI, board, CurrentTimeUTC.get()));
			messageReferences.add(new MessageList.MessageReference(myParentID, myParentFreenetURI, board, CurrentTimeUTC.get()));
			messageReferences.add(new MessageList.MessageReference(myMessageID, mMessageFreenetURI, board, CurrentTimeUTC.get()));
		}
		WoTMessageList messageList = new WoTMessageList(mFreetalk, myAuthor, WoTMessageList.assembleURI(authorRequestSSK, 123), messageReferences);
		messageList.initializeTransient(mFreetalk);
		messageList.storeWithoutCommit();
		Persistent.checkedCommit(db, this);
		mMessageListID = messageList.getID();
		
		List<Attachment> attachments = new ArrayList<Attachment>();
		attachments.add(new Attachment(new FreenetURI("KSK@attachment1"), new MimeType("text/plain"), 10001));
		attachments.add(new Attachment(new FreenetURI("KSK@attachment2"), new MimeType("audio/ogg"), 10002));
		
		final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		calendar.set(2009, 05-1, 03, 16, 15, 14); final Date date = calendar.getTime();
		
		WoTMessage message = WoTMessage.construct(mFreetalk, messageList, mMessageFreenetURI, myMessageID,
				new WoTMessageURI(messageList.getURI(), myThreadID), // Thread
				new WoTMessageURI(messageList.getURI(), myParentID), // Parent
				myBoards, myBoard, myAuthor,
				"Message title <no-xml-tag>", date, "Message body <no-xml-tag>\nNew line[quote] quoted text [/quote]", attachments);
		
		mMessageID = message.getID();
		
		message.initializeTransient(mFreetalk);
		message.storeAndCommit();
	       
		mHardcodedEncodedMessage = new String(
			"<?xml version=\"1.1\" encoding=\"UTF-8\" standalone=\"no\"?>" +
			"<" + Freetalk.PLUGIN_TITLE + " Version=\"" + Version.getRealVersion() + "\">" + 
			"<Message Date=\"2009-05-03 16:15:14\" ID=\"2a3a8e7e-9e53-4978-a8fd-17b2d92d949c@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk\" Version=\"1\">" + 
			"<Boards>" +
			"<Board Name=\"eng.board1\"/>" +
			"<Board Name=\"eng.board2\"/>" +
			"<ReplyToBoard Name=\"eng.board1\"/>" +
			"</Boards>" +
			"<InReplyTo>" +
			"<Thread URI=\"SSK@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQACAAE/" + Freetalk.PLUGIN_TITLE + "%7cMessageList-123#afe6519b-7fb2-4533-b172-1f966e79d127\"/>" +
			"<Message URI=\"SSK@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQACAAE/" + Freetalk.PLUGIN_TITLE + "%7cMessageList-123#d5b0dcc4-91cb-4870-8ab9-8588e895fa5d\"/>" +
			"</InReplyTo>" +
			"<Subject>Message title &lt;no-xml-tag&gt;</Subject>" +
			"<Body>Message body &lt;no-xml-tag&gt;\n" +
			"New line[quote] quoted text [/quote]</Body>" +
			"<Attachments>" +
			"<File MIMEType=\"text/plain\" Size=\"10001\" URI=\"KSK@attachment1\"/>" +
			"<File MIMEType=\"audio/ogg\" Size=\"10002\" URI=\"KSK@attachment2\"/>" +
			"</Attachments>" +
			"</Message>" +
			"</" + Freetalk.PLUGIN_TITLE + ">"
			);
	}

	public void testEncode() throws Exception {
		System.gc(); db.purge(); System.gc();
		
		ByteArrayOutputStream encodedMessage = new ByteArrayOutputStream(4096);
		mXML.encode(mMessageManager.get(mMessageID), encodedMessage);
		
		assertEquals(mHardcodedEncodedMessage, encodedMessage.toString().replace("\r\n", "\n"));
	}

	public void testDecode() throws Exception {
		System.gc(); db.purge(); System.gc();
		
		ByteArrayInputStream is = new ByteArrayInputStream(mHardcodedEncodedMessage.getBytes("UTF-8"));
		ByteArrayOutputStream decodedAndEncodedMessage = new ByteArrayOutputStream(4096);
		Message decodedMessage = mXML.decode(mFreetalk, is, (WoTMessageList)mMessageManager.getMessageList(mMessageListID), mMessageFreenetURI);
		decodedMessage.initializeTransient(mFreetalk);
		mXML.encode(decodedMessage, decodedAndEncodedMessage);		
		
		assertEquals(mHardcodedEncodedMessage, decodedAndEncodedMessage.toString().replace("\r\n", "\n"));
	}
	
}
