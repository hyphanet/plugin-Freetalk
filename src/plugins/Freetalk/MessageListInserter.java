/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.TransferThread;

public abstract class MessageListInserter extends TransferThread {

	protected final IdentityManager mIdentityManager;
	
	protected final MessageManager mMessageManager;
	
	public MessageListInserter(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager, MessageManager myMessageManager) {
		super(myNode, myClient, myName);
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
		clearBeingInsertedFlags();
	}
	
	// TODO: Move function to MessageManager
	/**
	 * Each <code>OwnMessageList</code> has a flag which stores whether it is currently being inserted. The purpose of this function
	 * is to clear those flags at startup.
	 */
	protected abstract void clearBeingInsertedFlags();

}
