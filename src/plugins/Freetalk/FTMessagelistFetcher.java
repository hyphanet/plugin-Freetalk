package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.TransferThread;

public abstract class FTMessagelistFetcher extends TransferThread {

	public FTMessagelistFetcher(Node myNode, HighLevelSimpleClient myClient, String myName) {
		super(myNode, myClient, myName);
		// TODO Auto-generated constructor stub
	}


}
