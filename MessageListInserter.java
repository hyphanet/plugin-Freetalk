package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.TransferThread;

public abstract class MessageListInserter extends TransferThread {

	public MessageListInserter(Node myNode, HighLevelSimpleClient myClient, String myName) {
		super(myNode, myClient, myName);
		// TODO Auto-generated constructor stub
	}
	
	public abstract void postMessage(FTOwnIdentity identity, Message message);
}
