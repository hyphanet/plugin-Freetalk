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

	protected FMSIdentityManager mIdentityManager;

	public FMSMessageManager(ObjectContainer myDB, FMSIdentityManager myIdentityManager) {
		assert(myDB != null);
		assert(myIdentityManager != null);

		db = myDB;
		mIdentityManager = myIdentityManager;
	}

	public synchronized FMSMessage get(FreenetURI uri) {
		Query query = db.query();
		query.constrain(FMSMessage.class);
		query.descend("mURI").constrain(uri);
		ObjectSet<FMSMessage> result = query.execute();

		assert(result.size() <= 1);

		return (result.size() == 0) ? null : result.next();
	}

	public synchronized FMSBoard getBoardByName(String name) {
		Query query = db.query();
		query.constrain(FMSBoard.class);
		query.descend("mName").constrain(name);
		ObjectSet<FMSBoard> result = query.execute();

		assert(result.size() <= 1);

		return (result.size() == 0) ? null : result.next();
	}

	/**
	 * Get an iterator of all boards.
	 */
	public synchronized Iterator<FMSBoard> boardIterator() {
		/* FIXME: Accelerate this query. db4o should be configured to keep an alphabetic index of boards */
		Query query = db.query();
		query.constrain(FMSBoard.class);
		query.descend("mName").orderDescending();

		ObjectSet<FMSBoard> result = query.execute();

		return result.iterator();
	}

	/**
	 * Returns true if the message was not downloaded yet and any of the FMSOwnIdentity wants the message.
	 */
	protected synchronized boolean shouldDownloadMessage(FreenetURI uri, FMSIdentity author) {
		return (get(uri) != null) || mIdentityManager.anyOwnIdentityWantsMessagesFrom(author);
	}
}
