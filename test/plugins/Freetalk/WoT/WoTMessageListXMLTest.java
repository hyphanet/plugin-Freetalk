package plugins.Freetalk.WoT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.TimeZone;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import plugins.Freetalk.Board;
import plugins.Freetalk.DatabaseBasedTest;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.Version;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import freenet.keys.FreenetURI;
import freenet.support.MultiValueTable;

public class WoTMessageListXMLTest extends DatabaseBasedTest {
	
	private WoTMessageManager mMessageManager;
	
	private String mMessageListID;
	
		
	/**
	 * Stores the list of boards for each test message.
	 * Key = Message ID
	 * Value = Board name
	 */
	private MultiValueTable<String, String> mMessageBoards = new MultiValueTable<String, String>(16);
	
	
	private String mHardcodedEncodedMessageList;
	
	private WoTMessageListXML mXML;

	@SuppressWarnings("deprecation")
	@Override public void setUp() throws Exception {
		super.setUp();
		
		mMessageManager = mFreetalk.getMessageManager();
		mXML = new WoTMessageListXML();
		
		HashSet<Board> myBoards1 = new HashSet<Board>();
			myBoards1.add(mMessageManager.getOrCreateBoard("eng.board1"));
			myBoards1.add(mMessageManager.getOrCreateBoard("eng.board2"));
			
		HashSet<Board> myBoards2 = new HashSet<Board>();
			myBoards2.add(mMessageManager.getOrCreateBoard("eng.board3"));
			myBoards2.add(mMessageManager.getOrCreateBoard("eng.board4"));
		
		HashSet<Board> myBoards3 = new HashSet<Board>();
			myBoards3.add(mMessageManager.getOrCreateBoard("eng.board5"));
			myBoards3.add(mMessageManager.getOrCreateBoard("eng.board6"));
		
		FreenetURI authorRequestSSK = new FreenetURI("SSK@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQACAAE/");
		FreenetURI authorInsertSSK = new FreenetURI("SSK@Ykhv0x0K8jtrgOlqWVS4S2Jvmnm64zv5voNjMfz1nYI,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQECAAE/");
		String authorID = WoTIdentity.getIDFromURI(authorRequestSSK);
		WoTOwnIdentity myAuthor = new WoTOwnIdentity(authorID, authorRequestSSK, authorInsertSSK, "Nickname");
		myAuthor.initializeTransient(mFreetalk);
		myAuthor.storeAndCommit();
		

		final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		calendar.set(2009, 06-1, 01); final Date date1 = calendar.getTime();
		calendar.set(2008, 05-1, 02); final Date date2 = calendar.getTime();
		calendar.set(2007, 04-1, 03); final Date date3 = calendar.getTime();
		
		WoTOwnMessage[] messages = new WoTOwnMessage[] {
			mMessageManager.postMessage(null, null, myBoards1, null, myAuthor, "title1", date1, "text1", null),
			mMessageManager.postMessage(null, null, myBoards2, null, myAuthor, "title2", date2, "text2", null),
			mMessageManager.postMessage(null, null, myBoards3, null, myAuthor, "title3", date3,"text3", null),
		};
	
		FreenetURI[] messageURIs = new FreenetURI[] {
			new FreenetURI("CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8"),
			new FreenetURI("CHK@0YUT4BEorqJCETQrLSgHBcw5RL7KQNm6Fbpo3ThzTy4,6RzUH23~TwPQ0IDQcgPoxEYX7yBTgTNydD~uJ0I9DTQ,AAIA--8"),
			new FreenetURI("CHK@H4nfdTqgQUQ0CkdPzvrs2F~IIkjOCnfEn~S042jUxuw,wkCrKtmvmYQzuo3f4v2JlB87wJkK0dspmGJ~ivztYP8,AAIA--8")
		};

		WoTOwnMessageList messageList = new WoTOwnMessageList(myAuthor, 123);
		messageList.initializeTransient(mFreetalk);
		messageList.storeWithoutCommit();
		Persistent.checkedCommit(db, this);
		
		mMessageListID = messageList.getID();
		
		for(int i = 0; i < messages.length; ++i) {
			mMessageManager.onOwnMessageInserted(messages[i].getID(), messageURIs[i]);
		}
		
		for(WoTOwnMessage message : messages) {
			for(Board board : message.getBoards()) 
				mMessageBoards.put(message.getID(), board.getName());
		}
	
		
		mHardcodedEncodedMessageList = new String(
				"<?xml version=\"1.1\" encoding=\"UTF-8\" standalone=\"no\"?>" + 
				"<" + Freetalk.PLUGIN_TITLE + " Version=\"" + Version.getRealVersion() + "\">" + 
				"<MessageList Version=\"1\">" + 
				"<Message Date=\"2009-06-01\" FreenetURI=\"CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8\" ID=\"" + messages[0].getID() + "\">" + 
				"<Board Name=\"eng.board1\"/>" + 
				"<Board Name=\"eng.board2\"/>" + 
				"</Message>" + 
				"<Message Date=\"2008-05-02\" FreenetURI=\"CHK@0YUT4BEorqJCETQrLSgHBcw5RL7KQNm6Fbpo3ThzTy4,6RzUH23~TwPQ0IDQcgPoxEYX7yBTgTNydD~uJ0I9DTQ,AAIA--8\" ID=\"" + messages[1].getID() + "\">" + 
				"<Board Name=\"eng.board3\"/>" + 
				"<Board Name=\"eng.board4\"/>" + 
				"</Message>" + 
				"<Message Date=\"2007-04-03\" FreenetURI=\"CHK@H4nfdTqgQUQ0CkdPzvrs2F~IIkjOCnfEn~S042jUxuw,wkCrKtmvmYQzuo3f4v2JlB87wJkK0dspmGJ~ivztYP8,AAIA--8\" ID=\"" + messages[2].getID() + "\">" + 
				"<Board Name=\"eng.board5\"/>" + 
				"<Board Name=\"eng.board6\"/>" + 
				"</Message>" + 
				"</MessageList>" + 
				"</" + Freetalk.PLUGIN_TITLE + ">"
				);
	}

	public void testEncode() throws TransformerException, ParserConfigurationException, NoSuchMessageException, NoSuchMessageListException {
		ByteArrayOutputStream encodedMessageList = new ByteArrayOutputStream(4096);
		
		System.gc(); db.purge(); System.gc();
		
		mXML.encode(mMessageManager, (WoTOwnMessageList)mMessageManager.getOwnMessageList(mMessageListID), encodedMessageList);
		
		assertEquals(mHardcodedEncodedMessageList, encodedMessageList.toString().replaceAll("[\r\n]", ""));
	}

	public void testDecode() throws Exception {
		WoTMessageList decodedList;
		
		{
			ByteArrayInputStream is = new ByteArrayInputStream(mHardcodedEncodedMessageList.getBytes("UTF-8"));
			WoTOwnMessageList messageList = (WoTOwnMessageList)mMessageManager.getOwnMessageList(mMessageListID);
			decodedList = mXML.decode(mFreetalk, messageList.getAuthor(), messageList.getURI(), is);
			decodedList.initializeTransient(mFreetalk);
		}

		System.gc(); db.purge(); System.gc();
		
		/* Now we check every message reference we receive from the decoded XML. For each seen [message, board] pair we remove that
		 * pair from the messageBoards table. If the table is empty at the end, the XML decoding has not dropped any of the pairs */
		for(MessageList.MessageReference ref : decodedList) {
			WoTOwnMessage message = (WoTOwnMessage)mMessageManager.getOwnMessage(ref.getMessageID());
			assertTrue("A board was listed in the message list multiple times: " + ref.getBoard().getName(),
					mMessageBoards.containsElement(message.getID(), ref.getBoard().getName()));
			
			mMessageBoards.removeElement(message.getID(), ref.getBoard().getName());
		}
		
		assertTrue("Not all boards or messages were specified in the message list.", mMessageBoards.isEmpty());
	}

}
