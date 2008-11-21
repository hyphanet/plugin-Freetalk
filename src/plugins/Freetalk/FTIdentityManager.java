/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Iterator;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.support.Executor;
import freenet.support.Logger;

/**
 * @author saces, xor
 * 
 */
public abstract class FTIdentityManager implements Runnable, Iterable<FTIdentity> {
	
	protected final ObjectContainer db;

	protected final Executor mExecutor;

	public FTIdentityManager(ObjectContainer myDB, Executor myExecutor) {
		Logger.debug(this, "Creating identity manager...");
		db = myDB;
		mExecutor = myExecutor;
		mExecutor.execute(this, "FT Identity Manager");
	}

	public synchronized Iterator<FTIdentity> iterator() {
		return new Iterator<FTIdentity> () {
			Iterator<FTIdentity> iter;
			
			{
				 Query q = db.query();
				 q.constrain(FTIdentity.class);
				 iter = q.execute().iterator();
			}
			
			public boolean hasNext() {
				return iter.hasNext();
			}

			public FTIdentity next() {
				FTIdentity i = iter.next();
				i.initializeTransient(db);
				return i;
			}

			public void remove() {
				throw new UnsupportedOperationException("Cannot delete identities.");
			}
		};
	}

	public synchronized Iterator<FTOwnIdentity> ownIdentityIterator() {
		return new Iterator<FTOwnIdentity> () {
			Iterator<FTOwnIdentity> iter;
			
			{
				 Query q = db.query();
				 q.constrain(FTOwnIdentity.class);
				 iter = q.execute().iterator();
			}
			
			public boolean hasNext() {
				return iter.hasNext();
			}

			public FTOwnIdentity next() {
				FTOwnIdentity oi = iter.next();
				oi.initializeTransient(db);
				return oi;
			}

			public void remove() {
				throw new UnsupportedOperationException("Cannot delete own identities via ownIdentityIterator().");
			}
		};
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
	
	public synchronized void addNewIdentity(FTIdentity identity) {
		/* FIXME: implement */
	}
	
	public synchronized void addNewOwnIdentity(FTOwnIdentity identity) {
		/* FIXME: implement. */
		
	}
	
	public abstract void terminate();
}
