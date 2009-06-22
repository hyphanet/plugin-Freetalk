/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.test;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import plugins.Freetalk.Board;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessage;
import plugins.Freetalk.WoT.WoTMessageList;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTMessageURI;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.keys.FreenetURI;
import freenet.support.CurrentTimeUTC;

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
public class ThreadTreeTest extends DatabaseBasedTest {
	
	private WoTIdentityManager mIdentityManager;
	private WoTMessageManager mMessageManager;
	
	private Set<Board> mBoards;
	
	private WoTOwnIdentity[] mOwnIdentities;

	private int mMessageListIndex = 0;

	protected void setUp() throws Exception {
		super.setUp();
		
		mIdentityManager = new WoTIdentityManager(db);
		mMessageManager = new WoTMessageManager(db, mIdentityManager);
		
		constructIdentities();
		constructBoards();
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
			mOwnIdentities[i] = new WoTOwnIdentity(WoTOwnIdentity.getUIDFromURI(requestURI), requestURI, insertURI, "nickname" + i);
			mOwnIdentities[i].initializeTransient(db, mIdentityManager);
			mOwnIdentities[i].storeWithoutCommit();
		}
		
		db.commit();
	}
	
	private void constructBoards() throws InvalidParameterException {
		mBoards = new HashSet<Board>();
		mBoards.add(mMessageManager.getOrCreateBoard("en.test"));
	}


	private WoTMessageList constructMessageList(WoTOwnIdentity author) {
		WoTMessageList list = new WoTMessageList(author, WoTMessageList.generateURI(author, mMessageListIndex++));
		list.initializeTransient(db, mMessageManager);
		list.storeWithoutCommit();
		db.commit();
		
		return list;
		
	}
	
	private WoTMessage constructTestMessage(WoTOwnIdentity author, WoTMessageURI myParentURI, WoTMessageURI myThreadURI)
		throws MalformedURLException, InvalidParameterException {
		
		FreenetURI myRealURI = new FreenetURI("CHK@");
		UUID myUUID = UUID.randomUUID();
		
		WoTMessageList myList = constructMessageList(author);
		WoTMessageURI myURI = new WoTMessageURI(myList.getURI() + "#" + myUUID);
		
		return WoTMessage.construct(myList, myRealURI, myURI.getMessageID(), myThreadURI, myParentURI, mBoards, mBoards.iterator().next(), 
				author, "message " + myUUID, CurrentTimeUTC.get(), "message body " + myUUID, null);
	}
}
