/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Iterator;

import plugins.Freetalk.exceptions.NoSuchIdentityException;

import com.db4o.ObjectContainer;
import com.db4o.query.Query;

import freenet.node.PrioRunnable;
import freenet.support.Executor;
import freenet.support.Logger;

/**
 * @author saces, xor
 * 
 */
public abstract class IdentityManager implements PrioRunnable, Iterable<FTIdentity> {
	
	protected final ObjectContainer db;

	protected final Executor mExecutor;

	public IdentityManager(ObjectContainer myDB, Executor myExecutor) {
		Logger.debug(this, "Creating identity manager...");
		db = myDB;
		mExecutor = myExecutor;
	}

	public IdentityManager() {
		db = null;
		mExecutor = null;
	}

	@SuppressWarnings("unchecked")
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
				i.initializeTransient(db, IdentityManager.this);
				return i;
			}

			public void remove() {
				throw new UnsupportedOperationException("Cannot delete identities.");
			}
		};
	}
	
	public synchronized int countKnownIdentities() {
		/* FIXME: This should probably take an FTOwnIdentity as param and count the identities seen by it */
		Query q = db.query();
		q.constrain(FTIdentity.class);
		q.constrain(FTOwnIdentity.class).not();
		return q.execute().size();
	}

	@SuppressWarnings("unchecked")
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
				oi.initializeTransient(db, IdentityManager.this);
				return oi;
			}

			public void remove() {
				throw new UnsupportedOperationException("Cannot delete own identities via ownIdentityIterator().");
			}
		};
	}
	
	public abstract FTIdentity getIdentity(String uid) throws NoSuchIdentityException;
	
	public abstract FTOwnIdentity getOwnIdentity(String uid) throws NoSuchIdentityException;

	public synchronized boolean anyOwnIdentityWantsMessagesFrom(FTIdentity identity) {
		Iterator<FTOwnIdentity> iter = ownIdentityIterator();
		boolean noOwnIdentities = true;

		while (iter.hasNext()) {
			noOwnIdentities = false;
			FTOwnIdentity oid = iter.next();
			if (oid.wantsMessagesFrom(identity))
				return true;
		}

		return noOwnIdentities ? true : false;
	}
	
	public synchronized void addNewIdentity(FTIdentity identity) {
		/* FIXME: implement */
	}
	
	public synchronized void addNewOwnIdentity(FTOwnIdentity identity) {
		/* FIXME: implement. */
		
	}
	
	public abstract void terminate();

}
