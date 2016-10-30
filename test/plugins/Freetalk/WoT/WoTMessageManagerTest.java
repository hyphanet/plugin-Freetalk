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
import plugins.Freetalk.Message;
import plugins.Freetalk.Message.MessageID;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.MessageList.MessageListFetchFailedMarker;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.SubscribedBoard.BoardMessageLink;
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
	
	private WoTMessageManager mMessageManager;
	
	private WoTOwnIdentity[] mOwnIdentities;
	
	private Set<SubscribedBoard> mSubscribedBoards;
	private SubscribedBoard mSubscribedBoard;
	
	private Set<Board> mBoards;
	private Board mBoard;

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
	
	/**
	 * For threads and replies contains true if the specified message should be fetched, false if only a ghost reference should exist for it.
	 */
	private Hashtable<String, Boolean> mFetchedStates;
	
	

	@Override protected void setUp() throws Exception {
		super.setUp();
		
		mMessageManager = mFreetalk.getMessageManager();
		
		constructIdentities();
		constructBoards();
		
		mThreads = new LinkedList<String>();
		mReplies = new Hashtable<String, LinkedList<String>>(64);
		mFetchedStates = new Hashtable<String, Boolean>(64);
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
		
		Persistent.checkedCommit(db, this);
	}
	
	private void constructBoards() throws Exception {
		mMessageManager.getOrCreateBoard("eng.test");
		mSubscribedBoard = mMessageManager.subscribeToBoard(mOwnIdentities[0], "eng.test");
		
		mSubscribedBoards = new HashSet<SubscribedBoard>();
		mSubscribedBoards.add(mSubscribedBoard);
		
		mBoard = mMessageManager.getBoardByName(mSubscribedBoard.getName());
		mBoards = new HashSet<Board>();
		for(SubscribedBoard board : mSubscribedBoards)
			mBoards.add(mMessageManager.getBoardByName(board.getName()));
	}


	private WoTMessageList storeMessageList(WoTOwnIdentity author, FreenetURI uri, MessageList.MessageReference messageRef) throws InvalidParameterException, NoSuchIdentityException {
		List<MessageList.MessageReference> references = new ArrayList<MessageList.MessageReference>(2);
		references.add(messageRef);
		
		WoTMessageList list = new WoTMessageList(mFreetalk, author, uri, references);
		list.initializeTransient(mFreetalk);
		list.storeWithoutCommit();
		Persistent.checkedCommit(db, this);
		
		return list;
		
	}
	
	private WoTMessage createTestMessage(WoTOwnIdentity author, WoTMessage myParent, WoTMessageURI myThreadURI)
		throws MalformedURLException, InvalidParameterException, NoSuchIdentityException, NoSuchMessageException {
		
		FreenetURI myFreenetURI = new FreenetURI("CHK@");
		UUID myUUID = UUID.randomUUID();
		FreenetURI myListURI = WoTMessageList.assembleURI(author.getRequestURI(), mMessageListIndex++);
		WoTMessageURI myURI = new WoTMessageURI(myListURI + "#" + myUUID);
		myURI.initializeTransient(mFreetalk);
		
		MessageList.MessageReference ref = new MessageList.MessageReference(MessageID.construct(myURI.getMessageID()),
				myFreenetURI, mBoard, CurrentTimeUTC.get());
		
		WoTMessageList myList = storeMessageList(author, myListURI, ref);
		
		WoTMessageURI myParentURI = myParent != null ? myParent.getURI() : null;
		
		WoTMessage message = WoTMessage.construct(mFreetalk, myList, myFreenetURI, MessageID.construct(myURI.getMessageID()),
				myThreadURI, myParentURI, mBoards, mBoards.iterator().next(),  author, "message " + myUUID, CurrentTimeUTC.get(),
				"message body " + myUUID, null);
		
		message.initializeTransient(mFreetalk);
		
		return message;
	}
	
	private void verifyStructure() {
		System.gc();
		db.purge();
		System.gc();
		
		final Iterator<String> expectedThreads = mThreads.iterator();
	
		for(final BoardThreadLink threadRef : mSubscribedBoard.getThreads()) {
			// Verify that the thread exists
			assertTrue(expectedThreads.hasNext());
			
			final String expectedThreadID = expectedThreads.next();
				
			// ... and that it is in the correct position
			assertEquals(expectedThreadID, threadRef.getThreadID());
			
			// Verify that the thread Message object exists if it should.
			Message thread;
			try {
				thread = threadRef.getMessage();
				assertTrue(mFetchedStates.get(expectedThreadID));
			} catch(MessageNotFetchedException e) {
				thread = null;
				assertFalse(mFetchedStates.get(expectedThreadID));
			}
			
			// Verify the replies of the thread
			
			LinkedList<String> expectedRepliesList = mReplies.get(threadRef.getThreadID());
			if(expectedRepliesList == null)
				expectedRepliesList = new LinkedList<String>();
			final Iterator<String> expectedReplies = expectedRepliesList.iterator(); 
			
			for(final BoardMessageLink replyRef : mSubscribedBoard.getAllThreadReplies(threadRef.getThreadID(), true)) {
				assertTrue(expectedReplies.hasNext());
				
				try {
					final String expectedReplyID = expectedReplies.next();
					
					assertEquals(expectedReplyID, replyRef.getMessageID());
					assertEquals(threadRef.getThreadID(), replyRef.getThreadID());
					
					// Verify that the Message object exists if it should
					Message reply;
					try {
						reply = replyRef.getMessage();
						assertTrue(mFetchedStates.get(expectedReplyID));
					} catch(MessageNotFetchedException e) {
						reply = null;
						try {
							Message ignoredRealReply = mMessageManager.get(expectedReplyID);
							// The reply was NOT set on this reply link because it does not belong to this thread...
							try {
								assertFalse(ignoredRealReply.getThreadID().equals(threadRef.getThreadID()));
							} catch(NoSuchMessageException e2) { // is a thread itself
								// BoardReplyLinks should NOT be created if the parent is the thread itself
								assertFalse(ignoredRealReply.getID().equals(threadRef.getThreadID()));
							}
						} catch(NoSuchMessageException e3) {
							assertFalse(mFetchedStates.get(expectedReplyID));
						}
					}
					
					Message replyParent;
					
					if(reply != null) {
						assertFalse(reply.isThread());
						assertEquals(expectedReplyID, reply.getID());
						assertEquals(expectedThreadID, reply.getThreadID());
						
						try {
							assertEquals(thread, reply.getThread());
						} catch(NoSuchMessageException e) {
							assertNull(thread);
						}
					
						final String replyParentID = reply.getParentID();
					
						try {
							replyParent = reply.getParent();
							assertTrue(mFetchedStates.get(replyParentID));
						} catch(NoSuchMessageException e) {
							replyParent = null;
							assertFalse(mFetchedStates.get(replyParentID));
						}
					} else {
						replyParent = null;
					}
					
					// We do not specify the parent message for some test messages, check whether its assigned correctly
					
					if(replyParent != null) {
						assertEquals(reply.getParentID(), replyParent.getID());
						if(reply.getParentID().equals(expectedThreadID)) {
							assertEquals(thread, replyParent);
						}
					}
				} catch(MessageNotFetchedException e) {
					fail();
				} catch(NoSuchMessageException e) {
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
			mMessageManager.onMessageReceived(thread0); mFetchedStates.put(thread0.getID(), true);
			mThreads.addFirst(thread0.getID()); // Single empty thread
			
			verifyStructure();
		
		{ // Keep the variables in scope so we do not mix them up
		WoTMessage thread1 = createTestMessage(mOwnIdentities[1], null, null);			
			mMessageManager.onMessageReceived(thread1);	mFetchedStates.put(thread1.getID(), true);
			mThreads.addFirst(thread1.getID()); // Two empty threads, onMessageReceived called in chronological order
			
			verifyStructure(); 
		}
		
		{
		WoTMessage thread0reply0 = createTestMessage(mOwnIdentities[2], thread0, thread0.getURI());
			mMessageManager.onMessageReceived(thread0reply0); mFetchedStates.put(thread0reply0.getID(), true);
			//First thread receives 1 reply, should be moved to top now
			mReplies.put(thread0.getID(), new LinkedList<String>());
			mReplies.get(thread0.getID()).addLast(thread0reply0.getID()); 
			mThreads.remove(thread0.getID()); mThreads.addFirst(thread0.getID());
			verifyStructure();
		}
		
		WoTMessage thread2reply1; // We'll use it later
		WoTMessage thread2;
		{
		    thread2 = createTestMessage(mOwnIdentities[3], null, null); mFetchedStates.put(thread2.getID(), true);
		 // Third thread created, should be on top now
			mMessageManager.onMessageReceived(thread2); mFetchedStates.put(thread2.getID(), true);
			mThreads.addFirst(thread2.getID());
			verifyStructure(); 
			
			WoTMessage thread2reply0 = createTestMessage(mOwnIdentities[0], thread2, thread2.getURI());
			thread2reply1 = createTestMessage(mOwnIdentities[1], thread2reply0, thread2.getURI());
			WoTMessage thread2reply2 = createTestMessage(mOwnIdentities[2], thread2, thread2.getURI());

			mReplies.put(thread2.getID(), new LinkedList<String>());
			// Three replies, onMessageReceived called in chronological order
			mMessageManager.onMessageReceived(thread2reply0); mFetchedStates.put(thread2reply0.getID(), true); 
			mReplies.get(thread2.getID()).addLast(thread2reply0.getID()); verifyStructure();
			mMessageManager.onMessageReceived(thread2reply1); mFetchedStates.put(thread2reply1.getID(), true); 
			mReplies.get(thread2.getID()).addLast(thread2reply1.getID());  verifyStructure();
			mMessageManager.onMessageReceived(thread2reply2); mFetchedStates.put(thread2reply2.getID(), true); 
			mReplies.get(thread2.getID()).addLast(thread2reply2.getID()); verifyStructure();
		}
		
		{
		WoTMessage thread3 = createTestMessage(mOwnIdentities[4], null, null);
			mMessageManager.onMessageReceived(thread3); mFetchedStates.put(thread3.getID(), true);
			mThreads.addFirst(thread3.getID());
			
			verifyStructure(); // Fourth thread created, should be on top now
			WoTMessage thread3reply0 = createTestMessage(mOwnIdentities[0], thread3, thread3.getURI());
			WoTMessage thread3reply1 = createTestMessage(mOwnIdentities[1], thread3reply0, thread3.getURI());
			WoTMessage thread3reply2 = createTestMessage(mOwnIdentities[2], thread3reply1, thread3.getURI());
			WoTMessage thread3reply3 = createTestMessage(mOwnIdentities[3], thread3reply1, thread3.getURI());
			
			// Prevent NPE due to the booleans not being in the table...
			mFetchedStates.put(thread3reply0.getID(), false);
			mFetchedStates.put(thread3reply1.getID(), false);
			mFetchedStates.put(thread3reply2.getID(), false);
			mFetchedStates.put(thread3reply3.getID(), false);
			
			// Four replies, onMessageReceived called in random order
			mReplies.put(thread3.getID(), new LinkedList<String>());
			mMessageManager.onMessageReceived(thread3reply3); mFetchedStates.put(thread3reply3.getID(), true);
			// It's parent should be created as ghost reply because it is not fetched yet
			mReplies.get(thread3.getID()).addLast(thread3reply3.getParentID()); mFetchedStates.put(thread3reply3.getParentID(), false);
			mReplies.get(thread3.getID()).addLast(thread3reply3.getID()); 
			verifyStructure();
			
			mMessageManager.onMessageReceived(thread3reply2); mFetchedStates.put(thread3reply2.getID(), true);
			mReplies.get(thread3.getID()).addFirst(thread3reply2.getID());
			// The ghost reference to its parent should get its date-guess updated and appear at front of the thread
			mReplies.get(thread3.getID()).remove(thread3reply2.getParentID());
			mReplies.get(thread3.getID()).addFirst(thread3reply2.getParentID());
			mFetchedStates.put(thread3reply2.getParentID(), false); // This was done already but we do it again to make it clear
			verifyStructure(); 
			
			mMessageManager.onMessageReceived(thread3reply0); mFetchedStates.put(thread3reply0.getID(), true);
			mReplies.get(thread3.getID()).addFirst(thread3reply0.getID()); 
			verifyStructure();
			
			mMessageManager.onMessageReceived(thread3reply1); mFetchedStates.put(thread3reply1.getID(), true);
			// The ghost reference to reply1 should be converted to a real reference now and be moved to front
			mReplies.get(thread3.getID()).remove(thread3reply1.getID());
			mReplies.get(thread3.getID()).add(1, thread3reply1.getID());
			// TODO: Check whether the date is set correctly...
			verifyStructure();
		}
		
		{
			WoTMessage thread4 = thread2reply1;
			WoTMessage thread4reply0 = createTestMessage(mOwnIdentities[0], thread4, thread4.getURI()); // Fork a new thread off thread2reply1
			WoTMessage thread4reply1 = createTestMessage(mOwnIdentities[1], thread4reply0, thread4.getURI()); // Reply to it
			WoTMessage thread4reply2 = createTestMessage(mOwnIdentities[2], null, thread4.getURI()); // Specify no parent, should be set to thread2reply1
			WoTMessage thread4reply3 = createTestMessage(mOwnIdentities[2], thread0, thread4.getURI()); // Specify different thread as parent message
			
			// Prevent NPE due to the booleans not being in the table...
			mFetchedStates.put(thread4reply0.getID(), false);
			mFetchedStates.put(thread4reply1.getID(), false);
			mFetchedStates.put(thread4reply2.getID(), false);
			mFetchedStates.put(thread4reply3.getID(), false);
			
				mMessageManager.onMessageReceived(thread4reply0); mFetchedStates.put(thread4reply0.getID(), true);
				mThreads.addFirst(thread4.getID());
				mReplies.put(thread4.getID(), new LinkedList<String>());
				mReplies.get(thread4.getID()).addFirst(thread4reply0.getID());
				verifyStructure();
				
				// Insert the replies in random order, TODO: Try all different orders
				mMessageManager.onMessageReceived(thread4reply2); mFetchedStates.put(thread4reply2.getID(), true);
				// We don't need to update the ghost parent reply here because reply2 is a direct reply to the thread and the thread exists...
				mReplies.get(thread4.getID()).addLast(thread4reply2.getID());
				verifyStructure();
				
				mMessageManager.onMessageReceived(thread4reply1); mFetchedStates.put(thread4reply1.getID(), true);
				// We don't need to update the ghost parent reply here because the parent of reply1 exists already
				mReplies.get(thread4.getID()).add(1, thread4reply1.getID());
				verifyStructure();
				
				mMessageManager.onMessageReceived(thread4reply3); mFetchedStates.put(thread4reply3.getID(), true);
				mReplies.get(thread4.getID()).addLast(thread4reply3.getParentID());
				// The fetched state is ignored by verify structure because the parent is a thread...
				// mFetchedStates.put(thread4reply3.getParentID(), true);
				mReplies.get(thread4.getID()).addLast(thread4reply3.getID());
				verifyStructure();
		}
		
		{
			WoTMessage thread2reply3 = createTestMessage(mOwnIdentities[0], thread2reply1, thread2.getURI());
			// Replying to thread2reply1 within thread2 should still work even though someone forked a thread off it 
			mMessageManager.onMessageReceived(thread2reply3); mFetchedStates.put(thread2reply3.getID(), true);
			mReplies.get(thread2.getID()).addLast(thread2reply3.getID());
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
		mMessageManager.onMessageReceived(thread0); mFetchedStates.put(thread0.getID(), true);
		mThreads.addFirst(thread0.getID()); // Single empty thread
		
		WoTMessage thread0reply0 = createTestMessage(mOwnIdentities[1], thread0, thread0.getURI());
		//First thread receives 1 reply, should be moved to top now
		mMessageManager.onMessageReceived(thread0reply0); mFetchedStates.put(thread0reply0.getID(), true); 
		mReplies.put(thread0.getID(), new LinkedList<String>());
		mReplies.get(thread0.getID()).addLast(thread0reply0.getID()); 
		mThreads.remove(thread0.getID()); mThreads.addFirst(thread0.getID());
		verifyStructure();
	
		// Fork a new thread off thread0 by creating a reply to it. The reply should not be deleted because it's from a different identity.
		// After deletion of the author of thread0reply0 thread1 should still be visible, as a ghost thread now. See Board.deleteMessage().
		WoTMessage thread1 = thread0reply0;
		WoTMessage thread1reply0 = createTestMessage(mOwnIdentities[0], thread1, thread1.getURI());
		mMessageManager.onMessageReceived(thread1reply0); mFetchedStates.put(thread1reply0.getID(), true);
		mThreads.addFirst(thread1.getID());
		mReplies.put(thread1.getID(), new LinkedList<String>());
		mReplies.get(thread1.getID()).addFirst(thread1reply0.getID());
		verifyStructure();
	
		{ // This thread should not be deleted because it's from a different identity.
			WoTMessage thread2 = createTestMessage(mOwnIdentities[0], null, null); 	
			mMessageManager.onMessageReceived(thread2); mFetchedStates.put(thread2.getID(), true);
			mThreads.addFirst(thread2.getID()); // Two empty threads, onMessageReceived called in chronological order
			verifyStructure(); 
		}
		
		mMessageManager.beforeIdentityDeletion(mOwnIdentities[1]);
		mFetchedStates.put(thread0.getID(), false);
		mFetchedStates.put(thread0reply0.getID(), false);
		mOwnIdentities[1].deleteWithoutCommit();
		Persistent.checkedCommit(db, this);
		
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
		
		q = db.query();
		q.constrain(FetchFailedMarker.class);
		assertEquals(1, q.execute().size());
		
		mMessageManager.clearExpiredFetchFailedMarkers();
		
		q = db.query();
		q.constrain(FetchFailedMarker.class);
		markers = q.execute();
		assertEquals(1, markers.size());
		
		marker = (MessageListFetchFailedMarker)markers.next();
		
		assertTrue((CurrentTimeUTC.getInMillis() - marker.getDate().getTime()) < 10 * 1000);
		assertEquals(marker.getDate().getTime() + MessageManager.MINIMAL_MESSAGELIST_FETCH_RETRY_DELAY, marker.getDateOfNextRetry().getTime());
		assertEquals(false, marker.isRetryAllowedNow());
		
		q = db.query();
		q.constrain(MessageList.class);
		messageLists = q.execute();
		assertEquals(1, messageLists.size());
		assertEquals(messageLists.next().getID(), marker.getMessageListID());
		
		// Now we simulate a retry of the message list fetch
		
		marker.setDateOfNextRetry(marker.getDate());
		marker.setAllowRetryNow(false); // Needed for clearExpiredFetchFailedMarkers to process the marker
		marker.storeWithoutCommit();
		Persistent.checkedCommit(db, this);
				
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
		assertEquals(false, marker.isRetryAllowedNow());
		
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
