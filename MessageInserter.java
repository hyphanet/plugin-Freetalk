/**
 * 
 */
package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.TransferThread;

/**
 * This class periodically searches the database for OwnMessage objects which are marked as not inserted yet and inserts them.
 * To make it insert an OwnMessage, you just have to store the object in the database.
 * 
 * @author xor
 *
 */
public abstract class MessageInserter extends TransferThread {

	protected static final int STARTUP_DELAY = 1 * 60 * 1000;
	protected static final int THREAD_PERIOD = 30 * 60 * 1000; /* FIXME: tweak before release */
	
	protected IdentityManager mIdentityManager;
	
	protected MessageManager mMessageManager;
	
	public MessageInserter(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager, MessageManager myMessageManager) {
		super(myNode, myClient, myName);
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
	}
}
