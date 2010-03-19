/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Hashtable;
import java.util.Iterator;

import plugins.Freetalk.exceptions.NoSuchIdentityException;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.node.PrioRunnable;
import freenet.support.Executor;
import freenet.support.Logger;

/**
 * @author xor (xor@freenetproject.org)
 * @author saces
 */
public abstract class IdentityManager implements PrioRunnable {

	protected final Freetalk mFreetalk;
	
	protected final ExtObjectContainer db;

	protected final Executor mExecutor;
	
	private final Hashtable<FTIdentity, String> mShortestUniqueNicknameCache = new Hashtable<FTIdentity, String>(1024);
	

	public IdentityManager(Freetalk myFreetalk, Executor myExecutor) {
		Logger.debug(this, "Creating identity manager...");
		mFreetalk = myFreetalk;
		db = mFreetalk.getDatabase();
		mExecutor = myExecutor;
	}

	/**
	 * For being used in JUnit tests to run without a node.
	 */
	public IdentityManager(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
		db = mFreetalk.getDatabase();
		mExecutor = null;
	}
	
	public abstract FTOwnIdentity createOwnIdentity(String newNickname, boolean publishesTrustList, boolean publishesIntroductionPuzzles) throws Exception;
	
	public abstract FTOwnIdentity createOwnIdentity(String newNickname, boolean publishesTrustList, boolean publishesIntroductionPuzzles,
			FreenetURI requestURI, FreenetURI insertURI) throws Exception;

	public abstract Iterable<? extends FTIdentity> getAllIdentities();
	
	public synchronized int countKnownIdentities() {
		/* FIXME: This should probably take an FTOwnIdentity as param and count the identities seen by it */
		Query q = db.query();
		q.constrain(FTIdentity.class);
		q.constrain(FTOwnIdentity.class).not();
		return q.execute().size();
	}

	public abstract Iterator<? extends FTOwnIdentity> ownIdentityIterator();
	
	public abstract FTIdentity getIdentity(String id) throws NoSuchIdentityException;
	
	public abstract FTOwnIdentity getOwnIdentity(String id) throws NoSuchIdentityException;

	public synchronized boolean anyOwnIdentityWantsMessagesFrom(FTIdentity identity) {
		final Iterator<? extends FTOwnIdentity> iter = ownIdentityIterator();
		boolean noOwnIdentities = true;

		while (iter.hasNext()) {
			noOwnIdentities = false;
			FTOwnIdentity oid = iter.next();
			try {
				if (oid.wantsMessagesFrom(identity))
					return true;
			} catch(Exception e) {
				Logger.error(this, "anyOwnIdentityWantsMessagesFrom: wantsMessagesFrom() failed, skipping the current FTOwnIdentity.", e);
			}
		}

		return noOwnIdentities ? true : false;
	}
	
	public void start() {
		mExecutor.execute(this, "Freetalk " + this.getClass().getSimpleName());
		Logger.debug(this, "Started.");
	}

	public abstract void terminate();

	/**
	 * This function does not do any synchronization and does not require any synchronization, therefore you can use it everywhere without causing deadlocks.
	 */
	public abstract String getShortestUniqueName(FTIdentity identity);
}
