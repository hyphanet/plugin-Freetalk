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
public abstract class FTIdentityManager implements Iterable<FMSIdentity> {

	protected final ObjectContainer db;

	protected final Executor mExecutor;

	public FTIdentityManager(ObjectContainer myDB, Executor newExecutor) {
		db = myDB;
		mExecutor = newExecutor;
	}

	public synchronized Iterator<FMSIdentity> iterator() {
		ObjectSet<FMSIdentity> ids = db.query(FMSIdentity.class);
		return ids.iterator();
	}

	public synchronized Iterator<FMSOwnIdentity> ownIdentityIterator() {
		ObjectSet<FMSOwnIdentity> oids = db.query(FMSOwnIdentity.class);
		return oids.iterator();
	}

	public synchronized boolean anyOwnIdentityWantsMessagesFrom(FMSIdentity identity) {
		Iterator<FMSOwnIdentity> iter = ownIdentityIterator();

		while (iter.hasNext()) {
			FMSOwnIdentity oid = iter.next();
			if (oid.wantsMessagesFrom(identity))
				return true;
		}

		return false;
	}
}
