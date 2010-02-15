/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import plugins.Freetalk.Board;
import plugins.Freetalk.DatabaseBasedTest;
import plugins.Freetalk.FetchFailedMarker;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.MessageList.MessageListFetchFailedMarker;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.SubscribedBoard.MessageReference;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;

import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.CurrentTimeUTC;


public class WoTMessageManagerTest extends DatabaseBasedTest {
	
	private Freetalk mFreetalk;
	private WoTMessageManager mMessageManager;
	
	private WoTOwnIdentity[] mOwnIdentities;
	
	private Set<Board> mBoards;
	private SubscribedBoard mBoard;

	private int mMessageListIndex = 0;
	
	/**
	 * The threads which we stored in the database. The unit test should test whether board.getThreads() returns the threads in the order in which they are stored
	 * in this list. It should of course also test whether no thread is missing, or no thread is returned even though it should
	 * not be returned as a thread.
	 */
	private LinkedList<String> mThreads;
	
	/**
	 * The replies to each message which we stored in the database. The unit test should test whether the replies show up, whether their order is correct, etc.
	 */
	private Hashtable<String, LinkedList<String>> mReplies;
	
	

	protected void setUp() throws Exception {
		super.setUp();
		
		mFreetalk = new Freetalk(db);
		mMessageManager = mFreetalk.getMessageManager();
		
		constructIdentities();
		constructBoards();
		
		mThreads = new LinkedList<String>();
		mReplies = new Hashtable<String, LinkedList<String>>();
	}
	
	private void constructIdentities() throws MalformedURLException {
		String[] requestSSKs = new String[] {
			"SSK@lY~N0Nk5NQpt6brGgtckFHPY11GzgkDn4VDszL6fwPg,GDQlSg9ncBBF8XIS-cXYb-LM9JxE3OiSydyOaZgCS4k,AQACAAE/WoT",
			"SSK@WcOyByjhHpYE-GeA4f0QTm8WxIMLeuTeHH0OvoIySLI,m2xhPKGLhq1yqpqdYp0Yvbs~qdnJU4PD0NmWga1cwRE,AQACAAE/WoT",
			"SSK@OHIaAMNpKIgdbkWPCOb9phCQoa015NAoiA0ud-9a4TM,5Jp16w6-yS~AiQweFljj-gJck0AYxzu-Nfs6BjKXPsk,AQACAAE/WoT",
			"SSK@VMFi2tyuli54KgLNmMHz4k-XHKlNhlDVGOCFdLL5VRU,00v-jVRVF8P5xrd3kuiAWXHN7RPDxb5kJP9Z8XUqe~A,AQACAAE/WoT",
			"SSK@HH~V2XmCbZp~738qtE67jUg1M5L5flVvQfc2bYpE1o4,c8H39jkp08cao-EJVTV~rISHlcMnlTlpNFICzL4gmZ4,AQACAAE/WoT"
		};
		
		String[] insertSSKs = new String[] {
			"SSK@egaZBiTrPGsiLVBJGT91MOX5jtC6pFIDFDyjt3FcsRI,GDQlSg9ncBBF8XIS-cXYb-LM9JxE3OiSydyOaZgCS4k,AQECAAE/WoT",
			"SSK@Ze0-i5NRq60j549pck~Sb2zsyf98KNKczPsAGgT1lUE,m2xhPKGLhq1yqpqdYp0Yvbs~qdnJU4PD0NmWga1cwRE,AQECAAE/WoT",
			"SSK@RGNZ2LrmnS3DjX5DfpUfDpaqWnMmaLBVH9X8uB9CgRc,5Jp16w6-yS~AiQweFljj-gJck0AYxzu-Nfs6BjKXPsk,AQECAAE/WoT",
			"SSK@XjHet73nz3vIKRHc-Km8GtWCEMuzo6AEMIw16Pft6HA,00v-jVRVF8P5xrd3kuiAWXHN7RPDxb5kJP9Z8XUqe~A,AQECAAE/WoT",
			"SSK@ReQUmaBjHDrRd8Z8kOGMw9dVd5Q3RhhEAsYJQRLuXGY,c8H39jkp08cao-EJVTV~rISHlcMnlTlpNFICzL4gmZ4,AQECAAE/WoT"
		};
		
		mOwnIdentities = new WoTOwnIdentity[requestSSKs.length];
		
		for(int i = 0; i < requestSSKs.length; ++i) {
			FreenetURI requestURI = new FreenetURI(requestSSKs[i]); FreenetURI insertURI = new FreenetURI(insertSSKs[i]);
			mOwnIdentities[i] = new WoTOwnIdentity(WoTOwnIdentity.getIDFromURI(requestURI), requestURI, insertURI, "nickname" + i);
			mOwnIdentities[i].initializeTransient(mFreetalk);
			mOwnIdentities[i].storeWithoutCommit();
		}
		
		db.commit();
	}
	
	private void constructBoards() throws Exception {
		mMessageManager.getOrCreateBoard("en.test");
		mBoard = mMessageManager.subscribeToBoard(mOwnIdentities[0], "en.test");
		
		mBoards = new HashSet<Board>();
		mBoards.add(mBoard);
	}


	private WoTMessageList storeMessageList(WoTOwnIdentity author, FreenetURI uri, MessageList.MessageReference messageRef) throws InvalidParameterException, NoSuchIdentityException {
		List<MessageList.MessageReference> references = new ArrayList<MessageList.MessageReference>(2);
		references.add(messageRef);
		
		WoTMessageList list = new WoTMessageList(author, uri, references);
		list.initializeTransient(mFreetalk);
		list.storeWithoutCommit();
		db.commit();
		
		return list;
		
	}
	
	private WoTMessage createTestMessage(WoTOwnIdentity author, WoTMessage myParent, WoTMessageURI myThreadURI)
		throws MalformedURLException, InvalidParameterException, NoSuchIdentityException, NoSuchMessageException {
		
		FreenetURI myRealURI = new FreenetURI("CHK@");
		UUID myUUID = UUID.randomUUID();
		FreenetURI myListURI = WoTMessageList.assembleURI(author.getRequestURI(), mMessageListIndex++);
		WoTMessageURI myURI = new WoTMessageURI(myListURI + "#" + myUUID);
		
		MessageList.MessageReference ref = new MessageList.MessageReference(myURI.getMessageID(), myRealURI, mBoard);
		
		WoTMessageList myList = storeMessageList(author, myListURI, ref);
		
		WoTMessageURI myParentURI = myParent != null ? myParent.getURI() : null;
		
		WoTMessage message = WoTMessage.construct(myList, myRealURI, myURI.getMessageID(), myThreadURI, myParentURI,
				mBoards, mBoards.iterator().next(),  author, "message " + myUUID, CurrentTimeUTC.get(), "message body " + myUUID, null);
		
		return message;
	}
	
	private void verifyStructure() {
		System.gc();
		db.purge();
		System.gc();
		
		Iterator<String> expectedThreads = mThreads.iterator();
	
		for(BoardThreadLink ref : mBoard.getThreads()) {
			// Verify that the thread exists
			assertTrue(expectedThreads.hasNext());
			
			// ... and that it is in the correct position
			assertEquals(expectedThreads.next(), ref.getThreadID());
			
			// Verify the replies of the thread
			
			LinkedList<String> expectedRepliesList= mReplies.get(ref.getThreadID());
			if(expectedRepliesList == null)
				expectedRepliesList = new LinkedList<String>();
			Iterator<String> expectedReplies = expectedRepliesList.iterator(); 
			
			for(MessageReference replyRef : mBoard.getAllThreadReplies(ref.getThreadID(), true)) {
				assertTrue(expectedReplies.hasNext());
				try {
					assertEquals(expectedReplies.next(), replyRef.getMessage().getID());
				} catch(MessageNotFetchedException e) {
					fail();
				}
			}
			
			assertFalse(expectedReplies.hasNext());
		}
		
		assertFalse(expectedThreads.hasNext());
	}

	
	/**
	 * When you obtain a message object from the database, different kinds of other message objects can be queried from the message:
	 * - The thread it belongs to
	 * - The message it is a reply to
	 * - The replies to the message
	 * 
	 * Because messages are downloaded in random order, any of those referenced other messages can be unknown to Freetalk at a single point in time.
	 * For example if the thread a message belongs to was not downloaded yet, the message object contains the FreenetURI of the thread (which is
	 * sort of it's "primary key" in database-speech) but the reference to the actual message object which IS the thread will be null because the
	 * thread was not downloaded yet.
	 * 
	 * Therefore, it is the job of the MessageManager object and Board objects to <b>correctly</b> update associations of Message objects with
	 * each others, for example:
	 * - when a new message is downloaded, all it's already existent children should be linked to it
	 * - when a new thread is downloaded, all messages whose parent messages have not been downloaded yet should be temporarily be set as children
	 *		of the thread, even though they are not.
	 * - when a new message is downloaded, it should be ensured that any temporary parent&child associations mentioned in the previous line are
	 *		 replaced with the real parent&child association with the new message.
	 * - etc.
	 * 
	 * There are many pitfalls in those tasks which might cause messages being permanently linked to wrong parents, children or threads or even
	 * being lost.
	 * Therefore, it is the job of this unit test to use a hardcoded image of an example thread tree with several messages for testing whether
	 * the thread tree is properly reconstructed if the messages are fed in random order to the WoTMessageManager.
	 * 
	 * This is accomplished by feeding the messages in ANY possible order to the WoTMessageManager (each time with a new blank database) and
	 * verifying the thread tree after storing the messages. "Any possible order" means that all permutations of the ordering are covered.
	 * 
	 * As a side effect, this test also ensures that no messages are invisible to the user when querying the WoTMessageManager for messages in a
	 * certain board. This is done by not querying the database for the hardcoded message IDs directly but rather using the client-interface
	 * functions for listing all threads, replies, etc.
	 * 
	 */
	public void testThreading() throws MalformedURLException, InvalidParameterException, NoSuchIdentityException, NoSuchMessageException {
		WoTMessage thread0 = createTestMessage(mOwnIdentities[0], null, null);
			mMessageManager.onMessageReceived(thread0);
			mThreads.addFirst(thread0.getID()); // Single empty thread
			verifyStructure();
		
		{ // Keep the variables in scope so we do not mix them up
		WoTMessage thread1 = createTestMessage(mOwnIdentities[1], null, null); 	
			mMessageManager.onMessageReceived(thread1);	
			mThreads.addFirst(thread1.getID()); // Two empty threads, onMessageReceived called in chronological order
			verifyStructure(); 
		}
		
		{
		WoTMessage thread0reply0 = createTestMessage(mOwnIdentities[2], thread0, thread0.getURI());
			mMessageManager.onMessageReceived(thread0reply0); //First thread receives 1 reply, should be moved to top now
			mReplies.put(thread0.getID(), new LinkedList<String>());
			mReplies.get(thread0.getID()).addLast(thread0reply0.getID()); 
			mThreads.remove(thread0.getID()); mThreads.addFirst(thread0.getID());
			verifyStructure();
		}
		
		WoTMessage thread2reply1; // We'll use it later
		WoTMessage thread2;
		{
		    thread2 = createTestMessage(mOwnIdentities[3], null, null);
			mMessageManager.onMessageReceived(thread2); // Third thread created, should be on top now
			mThreads.addFirst(thread2.getID());
			verifyStructure(); 
			
			WoTMessage thread2reply0 = createTestMessage(mOwnIdentities[0], thread2, thread2.getURI());
			thread2reply1 = createTestMessage(mOwnIdentities[1], thread2reply0, thread2.getURI());
			WoTMessage thread2reply2 = createTestMessage(mOwnIdentities[2], thread2, thread2.getURI());

			mReplies.put(thread2.getID(), new LinkedList<String>());
			// Three replies, onMessageReceived called in chronological order
			mMessageManager.onMessageReceived(thread2reply0); mReplies.get(thread2.getID()).addLast(thread2reply0.getID()); verifyStructure();
			mMessageManager.onMessageReceived(thread2reply1); mReplies.get(thread2.getID()).addLast(thread2reply1.getID());  verifyStructure();
			mMessageManager.onMessageReceived(thread2reply2); mReplies.get(thread2.getID()).addLast(thread2reply2.getID()); verifyStructure();
		}
		
		{
		WoTMessage thread3 = createTestMessage(mOwnIdentities[4], null, null);
			mMessageManager.onMessageReceived(thread3); mThreads.addFirst(thread3.getID());
			verifyStructure(); // Fourth thread created, should be on top now
			WoTMessage thread3reply0 = createTestMessage(mOwnIdentities[0], thread3, thread3.getURI());
			WoTMessage thread3reply1 = createTestMessage(mOwnIdentities[1], thread3reply0, thread3.getURI());
			WoTMessage thread3reply2 = createTestMessage(mOwnIdentities[2], thread3reply1, thread3.getURI());
			WoTMessage thread3reply3 = createTestMessage(mOwnIdentities[3], thread3reply1, thread3.getURI());
			// Four replies, onMessageReceived called in random order
			mReplies.put(thread3.getID(), new LinkedList<String>());
			mReplies.get(thread3.getID()).addLast(thread3reply3.getID()); mMessageManager.onMessageReceived(thread3reply3); verifyStructure();
			mReplies.get(thread3.getID()).addFirst(thread3reply2.getID()); mMessageManager.onMessageReceived(thread3reply2); verifyStructure();
			mReplies.get(thread3.getID()).addFirst(thread3reply0.getID()); mMessageManager.onMessageReceived(thread3reply0); verifyStructure();
			mReplies.get(thread3.getID()).add(1, thread3reply1.getID()); mMessageManager.onMessageReceived(thread3reply1); verifyStructure();
		}
		
		{
			WoTMessage thread4 = thread2reply1;
			WoTMessage thread4reply0 = createTestMessage(mOwnIdentities[0], thread4, thread4.getURI()); // Fork a new thread off thread2reply1
			WoTMessage thread4reply1 = createTestMessage(mOwnIdentities[1], thread4reply0, thread4.getURI()); // Reply to it
			WoTMessage thread4reply2 = createTestMessage(mOwnIdentities[2], null, thread4.getURI()); // Specify no parent, should be set to thread2reply1 FIXME verify this
			WoTMessage thread4reply3 = createTestMessage(mOwnIdentities[2], thread0, thread4.getURI()); // Specify different thread as parent
				mMessageManager.onMessageReceived(thread4reply0);
				mThreads.addFirst(thread4.getID());
				mReplies.put(thread4.getID(), new LinkedList<String>());
				mReplies.get(thread4.getID()).addFirst(thread4reply0.getID());
				verifyStructure();
				
				// Insert the replies in random order, TODO: Try all different orders
				mReplies.get(thread4.getID()).addLast(thread4reply2.getID()); mMessageManager.onMessageReceived(thread4reply2); verifyStructure();
				mReplies.get(thread4.getID()).add(1, thread4reply1.getID()); mMessageManager.onMessageReceived(thread4reply1); verifyStructure();
				mReplies.get(thread4.getID()).add(3, thread4reply3.getID()); mMessageManager.onMessageReceived(thread4reply3); verifyStructure();
		}
		
		{
			WoTMessage thread2reply3 = createTestMessage(mOwnIdentities[0], thread2reply1, thread2.getURI());
			// Replying to thread2reply1 within thread2 should still work even though someone forked a thread off it 
			mMessageManager.onMessageReceived(thread2reply3); mReplies.get(thread2.getID()).addLast(thread2reply3.getID());
			mThreads.remove(thread2.getID()); mThreads.addFirst(thread2.getID()); // thread2 should be on top
			verifyStructure();
		}

	}
	
	/**
	 * Tests whether deleting an own identity also deletes it's threads and message lists.
	 * 
	 * TODO: Also test for non-own identities.
	 * TODO: Also check whether deleting MessageFetchFailedReference and MessageListFetchFailedReference works.
	 */
	public void testOnIdentityDeletion() throws MalformedURLException, InvalidParameterException, NoSuchIdentityException, NoSuchMessageException {
		WoTMessage thread0 = createTestMessage(mOwnIdentities[1], null, null);
		mMessageManager.onMessageReceived(thread0);
		mThreads.addFirst(thread0.getID()); // Single empty thread
		
		WoTMessage thread0reply0 = createTestMessage(mOwnIdentities[1], thread0, thread0.getURI());
		mMessageManager.onMessageReceived(thread0reply0); //First thread receives 1 reply, should be moved to top now
		mReplies.put(thread0.getID(), new LinkedList<String>());
		mReplies.get(thread0.getID()).addLast(thread0reply0.getID()); 
		mThreads.remove(thread0.getID()); mThreads.addFirst(thread0.getID());
		verifyStructure();
	
		// Fork a new thread off thread0 by creating a reply to it. The reply should not be deleted because it's from a different identity.
		// After deletion of the author of thread0reply0 thread1 should still be visible, as a ghost thread now. See Board.deleteMessage().
		WoTMessage thread1 = thread0reply0;
		WoTMessage thread1reply0 = createTestMessage(mOwnIdentities[0], thread1, thread1.getURI());
		mMessageManager.onMessageReceived(thread1reply0);
		mThreads.addFirst(thread1.getID());
		mReplies.put(thread1.getID(), new LinkedList<String>());
		mReplies.get(thread1.getID()).addFirst(thread1reply0.getID());
		verifyStructure();
	
		{ // This thread should not be deleted because it's from a different identity.
			WoTMessage thread2 = createTestMessage(mOwnIdentities[0], null, null); 	
			mMessageManager.onMessageReceived(thread2);	
			mThreads.addFirst(thread2.getID()); // Two empty threads, onMessageReceived called in chronological order
			verifyStructure(); 
		}
		
		mMessageManager.onIdentityDeletion(mOwnIdentities[1]);
		mOwnIdentities[1].deleteWithoutCommit();
		db.commit();
		
		// onIdentityDeletion should have deleted that thread because it only contains messages from the deleted identity.
		mThreads.remove(thread0.getID());
		// Thread 1 should still be visible even though it's message is from the deleted identity because it contains a reply from another identity.
	
		verifyStructure(); // Check whether Board.deleteMessage() worked.
		
		try {
			mMessageManager.getOwnMessage(thread0.getID());
			fail("onIdentityDeletion() did not delete a Message object!");
		}
		catch(NoSuchMessageException e) { }
		
		try {
			mMessageManager.getOwnMessageList(thread0.getMessageList().getID());
			fail("onIdentityDeletion() did not delete a MessageLis objectt!");
		}
		catch(NoSuchMessageListException e) { }
	}
	
	public void testOnMessageFetchFailed() {
		
	}
	
	@SuppressWarnings("unchecked")
	public void testOnMessageListFetchFailed() {
		WoTOwnIdentity author = mOwnIdentities[0];
		Query q;
		ObjectSet<FetchFailedMarker> markers;
		ObjectSet<MessageList> messageLists;
		MessageListFetchFailedMarker marker;
		
		q = db.query();
		q.constrain(FetchFailedMarker.class);
		assertEquals(0, q.execute().size());
		
		q = db.query();
		q.constrain(MessageList.class);
		assertEquals(0, q.execute().size());
		
		mMessageManager.onMessageListFetchFailed(author, WoTMessageList.assembleURI(author.getRequestURI(), 1), FetchFailedMarker.Reason.DataNotFound);
		mMessageManager.clearExpiredFetchFailedMarkers();
		
		q = db.query();
		q.constrain(FetchFailedMarker.class);
		markers = q.execute();
		assertEquals(1, markers.size());
		
		marker = (MessageListFetchFailedMarker)markers.next();
		
		assertTrue((CurrentTimeUTC.getInMillis() - marker.getDate().getTime()) < 10 * 1000);
		assertEquals(marker.getDate().getTime() + MessageManager.MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY, marker.getDateOfNextRetry().getTime());
		
		q = db.query();
		q.constrain(MessageList.class);
		messageLists = q.execute();
		assertEquals(1, messageLists.size());
		assertEquals(messageLists.next().getID(), marker.getMessageListID());
		
		// Now we simulate a retry of the message list fetch
		
		marker.setDateOfNextRetry(marker.getDate());
		marker.storeWithoutCommit(); db.commit();
				
		mMessageManager.clearExpiredFetchFailedMarkers();
		
		q = db.query();
		q.constrain(FetchFailedMarker.class);
		markers = q.execute();
		assertEquals(1, markers.size());
		assertEquals(marker, markers.next());
		
		q = db.query();
		q.constrain(MessageList.class);
		messageLists = q.execute();
		assertEquals(0, messageLists.size());
		
		mMessageManager.onMessageListFetchFailed(author, WoTMessageList.assembleURI(author.getRequestURI(), 1), FetchFailedMarker.Reason.DataNotFound);
		
		q = db.query();
		q.constrain(FetchFailedMarker.class);
		markers = q.execute();
		assertEquals(1, markers.size());
		assertEquals(marker, markers.next());
		assertTrue((CurrentTimeUTC.getInMillis() - marker.getDate().getTime()) < 10 * 1000);
		assertEquals(marker.getDate().getTime() + Math.min(MessageManager.MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY*2, MessageManager.MAXIMAL_MESSAGELIST_FETCH_RETRY_DELAY),
					marker.getDateOfNextRetry().getTime());
		assertEquals(1, marker.getNumberOfRetries());
		
		q = db.query();
		q.constrain(MessageList.class);
		messageLists = q.execute();
		assertEquals(1, messageLists.size());
		assertEquals(messageLists.next().getID(), marker.getMessageListID());
		
		// Simulate failure with existing marker and existing ghost message list, i.e. the message list fetcher tried to fetch even though it shouldn't.
		
		mMessageManager.onMessageListFetchFailed(author, WoTMessageList.assembleURI(author.getRequestURI(), 1), FetchFailedMarker.Reason.DataNotFound);
		
		q = db.query();
		q.constrain(FetchFailedMarker.class);
		markers = q.execute();
		assertEquals(1, markers.size());
		assertEquals(marker, markers.next());
		
		q = db.query();
		q.constrain(MessageList.class);
		messageLists = q.execute();
		assertEquals(1, messageLists.size());
		assertEquals(messageLists.next().getID(), marker.getMessageListID());
	}
}
