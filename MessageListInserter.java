/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
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
