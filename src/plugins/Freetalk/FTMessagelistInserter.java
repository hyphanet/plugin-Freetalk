package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.TransferThread;

public abstract class FTMessagelistInserter extends TransferThread {

	public FTMessagelistInserter(Node myNode, HighLevelSimpleClient myClient, String myName) {
		super(myNode, myClient, myName);
		// TODO Auto-generated constructor stub
	}
	
	public abstract void postMessage(FTOwnIdentity identity, FTMessage message);
}
