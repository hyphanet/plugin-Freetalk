/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;

import plugins.Freetalk.exceptions.DuplicateElementException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchWantedStateException;

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
	
	protected final ArrayList<OverallWantedStateChangedCallback> mOverallWantedStateChangedCallbacks = new ArrayList<OverallWantedStateChangedCallback>();
	
	protected final ArrayList<IndividualWantedStateChangedCallback> mIndividualWantedStateChangedCallbacks = new ArrayList<IndividualWantedStateChangedCallback>(); 
	

	public IdentityManager(Freetalk myFreetalk, Executor myExecutor) {
		Logger.debug(this, "Creating identity manager...");
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
	
	public interface OverallWantedStateChangedCallback {
		public void onOverallWantedStateChanged(Identity messageAuthor, boolean oldShouldFetch, boolean newShouldFetch);
	}
	
	public interface IndividualWantedStateChangedCallback {
		public void onIndividualWantedStateChanged(OwnIdentity owner, Identity messageAuthor, boolean oldShouldFetch, boolean newShouldFetch);
	}


	public final void registerNewIdentityCallback(final NewIdentityCallback listener, final boolean includeOwnIdentities) {
		mNewIdentityCallbacks.add(listener);
		
		if(includeOwnIdentities) {
			registerNewOwnIdentityCallback(new NewOwnIdentityCallback() {
				public void onNewOwnIdentityAdded(OwnIdentity identity) {
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
				public void beforeOwnIdentityDeletion(OwnIdentity identity) {
					listener.beforeIdentityDeletion(identity);
				}
			});
		}
	}

	public final void registerOwnIdentityDeletedCallback(final OwnIdentityDeletedCallback listener) {
		mOwnIdentityDeletedCallbacks.add(listener);
	}

	public final void registerOverallWantedStateChangedCallback(final OverallWantedStateChangedCallback listener) {
		mOverallWantedStateChangedCallbacks.add(listener);
	}
	
	public final void registerIndividualWantedStateChangedCallback(final IndividualWantedStateChangedCallback listener) {
		mIndividualWantedStateChangedCallbacks.add(listener);
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
	
	private final void doOverallWantedStateChangedCallbacks(final Identity author, boolean oldShouldFetch, boolean newShouldFetch) {
		for(OverallWantedStateChangedCallback callback : mOverallWantedStateChangedCallbacks) {
			callback.onOverallWantedStateChanged(author, oldShouldFetch, newShouldFetch);
		}
	}
	
	private final void doIndividualShouldFetchStateChangedCallbacks(final OwnIdentity owner, final Identity author, boolean oldShouldFetch, boolean newShouldFetch) {
		for(IndividualWantedStateChangedCallback callback : mIndividualWantedStateChangedCallbacks) {
			callback.onIndividualWantedStateChanged(owner, author, oldShouldFetch, newShouldFetch);
		}
	}
	
	private synchronized final IdentityWantedState getIndividualShouldFetchState(OwnIdentity owner, Identity author) throws NoSuchWantedStateException {
		final Query q = db.query();
		q.constrain(IdentityWantedState.class);
		q.descend("mOwner").constrain(owner).identity();
		q.descend("mRatedIdentity").constrain(author).identity();
		final ObjectSet<IdentityWantedState> result = new Persistent.InitializingObjectSet<IdentityWantedState>(mFreetalk, q);
		
		switch(result.size()) {
			case 1: return result.next();
			case 0: throw new NoSuchWantedStateException("owner: " + owner + "; author: " + author);
			default: throw new DuplicateElementException("owner: " + owner + "; author: " + author);
		}
	}

	
	/**
	 * @return True, if any IdentityWantedState is true for the given author. Looks at each object, the value is not cached.
	 */
	private synchronized final boolean getOverallShouldFetchState(Identity author) {
		final Query q = db.query();
		q.constrain(IdentityWantedState.class);
		q.descend("mRatedIdentity").constrain(author).identity();
		
		// TODO: Optimization: For public gateway mode with large amounts of OwnIdentities we should cache this in the Identity object.
		// Make sure to adapt the callers (especially setOverallShouldFetchState)
		
		for(IdentityWantedState state : new Persistent.InitializingObjectSet<IdentityWantedState>(mFreetalk, q)) {
			if(state.get())
				return true;
		}
		
		return false;
	}
	
	protected synchronized final void setOverallShouldFetchState(final String authorID, boolean shouldFetch) {
		synchronized(db.lock()) {
			try {
				final Identity author = getIdentity(authorID);
				
				// We do not have to do update the fetch state since it is not cached - getOverallShouldFetchState computes it from all individual states
				
				assert(getOverallShouldFetchState(author) == shouldFetch);
				
				// if(stateChanged)	// We cannot figure that out, we trust the caller that it is the case
					doOverallWantedStateChangedCallbacks(author, !shouldFetch, shouldFetch);
				
				Persistent.checkedCommit(db, this);
			} catch (Exception e) {
				Persistent.checkedRollbackAndThrow(db, this, new RuntimeException(e));
			}
		}
	}
	
	protected synchronized final void setIndividualShouldFetchState(final String ownerID, final String authorID, boolean shouldFetch) throws NoSuchIdentityException {
		synchronized(db.lock()) {
			try {
				final OwnIdentity owner = getOwnIdentity(ownerID);
				final Identity author = getIdentity(authorID);
				
				IdentityWantedState state;
				boolean stateChanged;
				try {
					state = getIndividualShouldFetchState(owner, author);
					stateChanged = state.set(shouldFetch);
				} catch(NoSuchWantedStateException e) {
					state = new IdentityWantedState(owner, author, shouldFetch, null);
					state.initializeTransient(mFreetalk);
					stateChanged = true;
				}
				state.storeWithoutCommit();
				
				if(stateChanged)
					doIndividualShouldFetchStateChangedCallbacks(owner, author, !shouldFetch, shouldFetch);
					
				Persistent.checkedCommit(db, this);
			} catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
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
