/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Iterator;
import java.util.Set;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;

import plugins.FMSPlugin.WoT.FMSIdentityWoT;
import plugins.FMSPlugin.WoT.FMSOwnIdentityWoT;

import freenet.support.Executor;

/**
 * @author saces, xor
 * 
 */
public abstract class FMSIdentityManager implements Iterable<FMSIdentity> {

	protected final ObjectContainer db;

	protected final Executor mExecutor;

	public FMSIdentityManager(ObjectContainer myDB, Executor newExecutor) {
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
