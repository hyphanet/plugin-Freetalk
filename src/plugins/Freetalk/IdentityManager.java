/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

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

	public IdentityManager(Freetalk myFreetalk, Executor myExecutor) {
		Logger.debug(this, "Creating identity manager...");
		mFreetalk = myFreetalk;
		db = mFreetalk.getDatabase();
		mExecutor = myExecutor;
	}

	/**
	 * For being used in JUnit tests to run without a node.
	 */
	public IdentityManager(ExtObjectContainer myDB) {
		mFreetalk = null;
		db = myDB;
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

	public abstract void terminate();

	/// format the name of an author
	public String shortestUniqueName(FTIdentity identity, int maxLength) {
		String nick = identity.getNickname(maxLength-5);
		String id = identity.getID();
		int longestCommonPrefix = 0;
		String formatted;

		// FIXME: use a map when this gets slow
		for(FTIdentity i : getAllIdentities()) {
			String otherID = i.getID();
			if(i.getNickname(maxLength-5).equals(nick) && !otherID.equals(id)) {
				if(longestCommonPrefix == 0) {
					longestCommonPrefix = 1;
				}
				while(id.substring(0, longestCommonPrefix).equals(otherID.substring(0, longestCommonPrefix))){
					longestCommonPrefix++;
				}
			}
		}
		if(longestCommonPrefix > 0) {
			return nick + "@" + id.substring(0, longestCommonPrefix);
		} else {
			return nick;
		}
	} 

}
