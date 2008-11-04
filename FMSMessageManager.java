/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Iterator;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.UpdatableSortedLinkedListWithForeignIndex;

/**
 * @author xor
 *
 */
public abstract class FMSMessageManager {
	
	protected ObjectContainer db;
	
	/**
	 * Contains all boards which where found in a message. References to all messages of a board are stored in
	 * the board. Adding a newly downloaded message therefore is done by searching its board and calling 
	 * <code>addMessage()</code> on that board. Further, the message is also added to mMessages, see below.
	 */
	protected UpdatableSortedLinkedListWithForeignIndex mBoards = new UpdatableSortedLinkedListWithForeignIndex();
	
	public synchronized FMSMessage get(FreenetURI uri) {
		Query query = db.query();
		query.constrain(FMSMessage.class);
		query.descend("mURI").constrain(uri);
		ObjectSet<FMSMessage> result = query.execute();
		
		return (result.size() == 0) ? null : result.next();
	}
	
	public synchronized FMSBoard getBoardByName(String name) {
		return (FMSBoard)mBoards.get(name);
	}
	
	/**
	 * Get an iterator of boards which the identity could subscribe to.
	 * @param identity
	 * @return
	 */
	public synchronized Iterator<FMSBoard> boardIterator(FMSOwnIdentity identity) {
		return (Iterator<FMSBoard>)mBoards.iterator();
	}
	

	/**
	 * Returns true if the message was not downloaded yet and any of the FMSOwnIdentity wants the message.
	 * @param uri
	 * @return
	 */
	protected abstract boolean shouldDownloadMessage(FreenetURI uri);
}
