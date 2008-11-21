/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Iterator;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.Executor;
import freenet.support.Logger;

/**
 * @author xor
 *
 */
public abstract class FTMessageManager implements Runnable {
	
	protected final FTMessageManager self = this;

	protected ObjectContainer db;
	
	protected Executor mExecutor;

	protected FTIdentityManager mIdentityManager;

	public FTMessageManager(ObjectContainer myDB, Executor myExecutor, FTIdentityManager myIdentityManager) {
		Logger.debug(this, "Starting message manager...");
		assert(myDB != null);
		assert(myIdentityManager != null);

		db = myDB;
		mExecutor = myExecutor;
		mIdentityManager = myIdentityManager;
		mExecutor.execute(this, "FT Identity Manager");
	}

	/**
	 * Get a message by its URI. The transient fields of the returned message will be initialized already.
	 */
	public synchronized FTMessage get(FreenetURI uri) {
		Query query = db.query();
		query.constrain(FTMessage.class);
		query.descend("mURI").constrain(uri);
		ObjectSet<FTMessage> result = query.execute();

		assert(result.size() <= 1);
		
		if(result.size() == 0)
			return null;
		else {
			FTMessage m = result.next();
			m.initializeTransient(db);
			return m;
		}
	}

	/**
	 * Get a board by its name. The transient fields of the returned board will be initialized already.
	 */
	public synchronized FTBoard getBoardByName(String name) {
		Query query = db.query();
		query.constrain(FTBoard.class);
		query.descend("mName").constrain(name);
		ObjectSet<FTBoard> result = query.execute();

		assert(result.size() <= 1);

		if(result.size() == 0)
			return null;
		else {
			FTBoard b = result.next();
			b.initializeTransient(db, this);
			return b;
		}
	}

	/**
	 * Get an iterator of all boards. The transient fields of the returned boards will be initialized already.
	 */
	public synchronized Iterator<FTBoard> boardIterator() {
		return new Iterator<FTBoard>() {
			private Iterator<FTBoard> iter;
			
			{
				/* FIXME: Accelerate this query. db4o should be configured to keep an alphabetic index of boards */
				Query query = db.query();
				query.constrain(FTBoard.class);
				query.descend("mName").orderDescending();

				ObjectSet<FTBoard> result = query.execute();
				iter = result.iterator();
			}

			public boolean hasNext() {
				return iter.hasNext();
			}

			public FTBoard next() {
				FTBoard next = iter.next();
				next.initializeTransient(db, self);
				return next;
			}

			public void remove() {
				throw new UnsupportedOperationException("Boards cannot be deleted yet.");
			}
			
		};
	}

	/**
	 * Returns true if the message was not downloaded yet and any of the FTOwnIdentity wants the message.
	 */
	protected synchronized boolean shouldDownloadMessage(FreenetURI uri, FTIdentity author) {
		return (get(uri) != null) || mIdentityManager.anyOwnIdentityWantsMessagesFrom(author);
	}
	
	public abstract void terminate();
}
