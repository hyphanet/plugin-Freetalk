/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;

import plugins.Freetalk.exceptions.NoSuchIdentityException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.Executor;
import freenet.support.Logger;

/**
 * @author xor (xor@freenetproject.org)
 * @author saces
 */
public abstract class IdentityManager {

	protected final Freetalk mFreetalk;
	
	protected final ExtObjectContainer db;
	
	
	protected final ArrayList<NewIdentityCallback> mNewIdentityCallbacks = new ArrayList<NewIdentityCallback>();
	
	protected final ArrayList<NewOwnIdentityCallback> mNewOwnIdentityCallbacks = new ArrayList<NewOwnIdentityCallback>();
	
	protected final ArrayList<IdentityDeletedCallback> mIdentityDeletedCallbacks = new ArrayList<IdentityDeletedCallback>();
	
	protected final ArrayList<OwnIdentityDeletedCallback> mOwnIdentityDeletedCallbacks = new ArrayList<OwnIdentityDeletedCallback>();
	
	protected final ArrayList<ShouldFetchStateChangedCallback> mShouldFetchStateChangedCallbacks = new ArrayList<ShouldFetchStateChangedCallback>();
	
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(IdentityManager.class);
	}
	

	public IdentityManager(Freetalk myFreetalk, Executor myExecutor) {
		if(logDEBUG) Logger.debug(this, "Creating identity manager...");
		mFreetalk = myFreetalk;
		db = mFreetalk.getDatabase();
	}

	/**
	 * For being used in JUnit tests to run without a node.
	 */
	protected IdentityManager(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
		db = mFreetalk.getDatabase();
	}
	
	public abstract OwnIdentity createOwnIdentity(String newNickname, boolean publishesTrustList, boolean publishesIntroductionPuzzles,
			boolean autoSubscribeToNewBoards, boolean displayImages) throws Exception;
	
	public abstract OwnIdentity createOwnIdentity(String newNickname, boolean publishesTrustList, boolean publishesIntroductionPuzzles, boolean autoSubscribeToNewBoards,
			boolean displayImages, FreenetURI requestURI, FreenetURI insertURI) throws Exception;

	public abstract Iterable<? extends Identity> getAllIdentities();
	
	public synchronized int countKnownIdentities() {
		/* TODO: This should probably take an OwnIdentity as param and count the identities seen by it */
		Query q = db.query();
		q.constrain(Identity.class);
		q.constrain(OwnIdentity.class).not();
		return q.execute().size();
	}

	public abstract ObjectSet<? extends OwnIdentity> ownIdentityIterator();
	
	public abstract Identity getIdentity(String id) throws NoSuchIdentityException;
	
	public abstract OwnIdentity getOwnIdentity(String id) throws NoSuchIdentityException;

	public synchronized boolean anyOwnIdentityWantsMessagesFrom(Identity identity) {		
		for(final OwnIdentity oid : ownIdentityIterator()) {
			try {
				if (oid.wantsMessagesFrom(identity))
					return true;
			} catch(NoSuchIdentityException e) {
				// The own identity was deleted meanwhile, ignore
			} catch(Exception e) {
				Logger.error(this, "anyOwnIdentityWantsMessagesFrom: wantsMessagesFrom() failed, skipping the current OwnIdentity.", e);
			}
		}

		return false;
	}


	public interface NewIdentityCallback {
		public void onNewIdentityAdded(Identity identity);
	}
	
	public interface NewOwnIdentityCallback {
		public void onNewOwnIdentityAdded(OwnIdentity identity);
	}
	
	public interface IdentityDeletedCallback {
		public void beforeIdentityDeletion(Identity identity);
	}
	
	public interface OwnIdentityDeletedCallback {
		public void beforeOwnIdentityDeletion(OwnIdentity identity);
	}
	
	public interface ShouldFetchStateChangedCallback {
		public void onShouldFetchStateChanged(Identity messageAuthor, boolean oldShouldFetch, boolean newShouldFetch);
	}


	public final void registerNewIdentityCallback(final NewIdentityCallback listener, final boolean includeOwnIdentities) {
		mNewIdentityCallbacks.add(listener);
		
		if(includeOwnIdentities) {
			registerNewOwnIdentityCallback(new NewOwnIdentityCallback() {
				@Override public void onNewOwnIdentityAdded(OwnIdentity identity) {
					listener.onNewIdentityAdded(identity);
				}
			});
		}
	}

	public final void registerNewOwnIdentityCallback(final NewOwnIdentityCallback listener) {
		mNewOwnIdentityCallbacks.add(listener);
	}
	
	public final void registerIdentityDeletedCallback(final IdentityDeletedCallback listener, final boolean includeOwnIdentities) {
		mIdentityDeletedCallbacks.add(listener);
		
		if(includeOwnIdentities) {
			registerOwnIdentityDeletedCallback(new OwnIdentityDeletedCallback() {
				@Override public void beforeOwnIdentityDeletion(OwnIdentity identity) {
					listener.beforeIdentityDeletion(identity);
				}
			});
		}
	}

	public final void registerOwnIdentityDeletedCallback(final OwnIdentityDeletedCallback listener) {
		mOwnIdentityDeletedCallbacks.add(listener);
	}

	public final void registerShouldFetchStateChangedCallback(final ShouldFetchStateChangedCallback listener) {
		mShouldFetchStateChangedCallbacks.add(listener);
	}
	
	protected final void doNewIdentityCallbacks(final Identity identity) {
		for(NewIdentityCallback callback : mNewIdentityCallbacks) {
			callback.onNewIdentityAdded(identity);
		}
	}
	
	protected final void doNewOwnIdentityCallbacks(final OwnIdentity identity) {
		for(NewOwnIdentityCallback callback : mNewOwnIdentityCallbacks) {
			callback.onNewOwnIdentityAdded(identity);
		}
	}
	
	protected final void doIdentityDeletedCallbacks(final Identity identity) {
		for(IdentityDeletedCallback callback : mIdentityDeletedCallbacks) {
			callback.beforeIdentityDeletion(identity);
		}
	}
	
	protected final void doOwnIdentityDeletedCallbacks(final OwnIdentity identity) {
		for(OwnIdentityDeletedCallback callback : mOwnIdentityDeletedCallbacks) {
			callback.beforeOwnIdentityDeletion(identity);
		}
	}
	
	protected final void doShouldFetchStateChangedCallbacks(final Identity author, boolean oldShouldFetch, boolean newShouldFetch) {
		for(ShouldFetchStateChangedCallback callback : mShouldFetchStateChangedCallbacks) {
			callback.onShouldFetchStateChanged(author, oldShouldFetch, newShouldFetch);
		}
	}


	public abstract void start();

	public abstract void terminate();

	/**
	 * This function does not do any synchronization and does not require any synchronization, therefore you can use it everywhere without causing deadlocks.
	 */
	public abstract String getShortestUniqueName(Identity identity);

	/**
	 * Extracts the OwnIdentity ID from the input Freetalk address 
	 * @param freetalkAddress freetalk address
	 * @return OwnIdentity ID or null on error
	 * 
	 * TODO: Move this function to a better place... it contains references to WoT-related stuff..
	 */
	public static String extractIdFromFreetalkAddress(final String freetalkAddress) {
	    /*
	     * Format of input:
	     *   nickname@_ID_.freetalk
	     * We want the _ID_
	     */
	    final String trailing = "." + Freetalk.WOT_CONTEXT.toLowerCase();
	    try {
	        // sanity checks
	        if (!freetalkAddress.toLowerCase().endsWith(trailing)) {
	            return null;
	        }
	        int ix = freetalkAddress.indexOf('@');
	        if (ix < 0) {
	            return null;
	        }
	        
	        final String id = freetalkAddress.substring(ix+1, freetalkAddress.length()-trailing.length());
	        return id;
	    } catch(Exception ex) {
	        throw new RuntimeException(ex);
	    }
	}
}
