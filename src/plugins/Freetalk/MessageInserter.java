/**
 * 
 */
package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.TransferThread;

/**
 * @author xor
 *
 */
public abstract class MessageInserter extends TransferThread {

	protected static final int STARTUP_DELAY = 1 * 60 * 1000;
	protected static final int THREAD_PERIOD = 30 * 60 * 1000; /* FIXME: tweak before release */
	
	protected IdentityManager mIdentityManager;
	
	public MessageInserter(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager) {
		super(myNode, myClient, myName);
		mIdentityManager = myIdentityManager;
	}
	
	public abstract void postMessage(FTOwnIdentity identity, Message message);
	
}
