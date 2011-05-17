/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.TransferThread;

public abstract class MessageFetcher extends TransferThread {
	
	protected final Freetalk mFreetalk;
	
	protected final IdentityManager mIdentityManager;
	
	protected final MessageManager mMessageManager;
	
	public MessageFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, Freetalk myFreetalk, IdentityManager myIdentityManager, MessageManager myMessageManager) {
		super(myNode, myClient, myName);
		mFreetalk = myFreetalk;
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
	}
}
