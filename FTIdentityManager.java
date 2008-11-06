/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import plugins.WoT.Identity;
import plugins.WoT.OwnIdentity;
import plugins.WoT.WoT;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.Executor;
import freenet.support.Logger;

/**
 * @author saces, xor
 * 
 */
public abstract class FTIdentityManager implements Runnable, Iterable<FTIdentity> {
	
	protected final ObjectContainer db;

	protected final Executor mExecutor;
	
	public boolean isRunning = true;

	public FTIdentityManager(ObjectContainer myDB, Executor newExecutor) {
		db = myDB;
		mExecutor = newExecutor;
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
