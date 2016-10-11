/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
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
	
	protected final IdentityManager mIdentityManager;
	
	protected final MessageManager mMessageManager;
	
	public MessageInserter(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager, MessageManager myMessageManager) {
		super(myNode, myClient, myName);
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
	}

	/**
	 * Cancels the insert for the given message if one exists.
	 * Synchronizes on this MessageInserter.
	 * The MessageManager must be synchronized after the MessageInserter to prevent deadlocks.
	 */
	public abstract void abortMessageInsert(String messageID);
}
