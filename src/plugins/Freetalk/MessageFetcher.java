/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.client.HighLevelSimpleClient;
import freenet.node.Node;
import freenet.support.Executor;
import freenet.support.TransferThread;

public abstract class MessageFetcher extends TransferThread {

	protected static final int STARTUP_DELAY = 1 * 60 * 1000;
	protected static final int THREAD_PERIOD = 15 * 60 * 1000; /* FIXME: tweak before release */
	
	protected IdentityManager mIdentityManager;
	protected MessageManager mMessageManager;
	
	public MessageFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager, MessageManager myMessageManager) {
		super(myNode, myClient, myName);
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
	}
}
