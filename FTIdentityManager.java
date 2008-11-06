/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Iterator;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;

import freenet.support.Executor;

/**
 * @author saces, xor
 * 
 */
public abstract class FTIdentityManager implements Runnable, Iterable<FTIdentity> {
	
	protected final ObjectContainer db;

	protected final Executor mExecutor;
	
	public boolean isRunning = true;

	public FTIdentityManager(ObjectContainer myDB, Executor myExecutor) {
		db = myDB;
		mExecutor = myExecutor;
		mExecutor.execute(this, "FT Identity Manager");
	}

	public synchronized Iterator<FTIdentity> iterator() {
		ObjectSet<FTIdentity> ids = db.query(FTIdentity.class);
		return ids.iterator();
	}

	public synchronized Iterator<FTOwnIdentity> ownIdentityIterator() {
		ObjectSet<FTOwnIdentity> oids = db.query(FTOwnIdentity.class);
		return oids.iterator();
	}

	public synchronized boolean anyOwnIdentityWantsMessagesFrom(FTIdentity identity) {
		Iterator<FTOwnIdentity> iter = ownIdentityIterator();

		while (iter.hasNext()) {
			FTOwnIdentity oid = iter.next();
			if (oid.wantsMessagesFrom(identity))
				return true;
		}

		return false;
	}
	
	public abstract void run();
}
