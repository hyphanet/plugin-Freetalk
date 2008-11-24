package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.Executor;
import freenet.support.TransferThread;

public abstract class FTMessageFetcher extends TransferThread {

	protected static final int STARTUP_DELAY = 1 * 60 * 1000;
	protected static final int THREAD_PERIOD = 30 * 60 * 1000; /* FIXME: tweak before release */
	
	protected FTIdentityManager mIdentityManager;
	
	public FTMessageFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, FTIdentityManager myIdentityManager) {
		super(myNode, myClient, myName);
		mIdentityManager = myIdentityManager;
	}
}
