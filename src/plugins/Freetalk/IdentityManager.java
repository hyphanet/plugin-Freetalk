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
	
	/**
	 * Interface function for child classes of IdentityManager.
	 * Must be called when a new {@link Identity} was stored to the database.
	 * 
	 * Must NOT be called when a new {@link OwnIdentity} was stored - use handleNewOwnIdentityWithoutCommit instead.
	 * 
	 * This will NOT mark the identity as wanted - identities are allowed to exist even if no OwnIdentity wants their messages.
	 */
	protected final void handleNewIdentityWithoutCommit(final Identity identity) {
		if(identity instanceof OwnIdentity)
			throw new IllegalArgumentException("OwnIdentity passed to handleNewIdentityWithoutCommit, see JavaDoc.");
		
		doNewIdentityCallbacks(identity);
	}
	
	private final void doNewIdentityCallbacks(final Identity identity) {
		Logger.minor(this, "New Identity, doing callbacks: " + identity);
		
		for(NewIdentityCallback callback : mNewIdentityCallbacks) {
			callback.onNewIdentityAdded(identity);
		}
	}
	
	/**
	 * Interface function for child classes of IdentityManager.
	 * Must be called when a new {@link OwnIdentity} was stored to the database.
	 * 
	 * This will NOT mark the identity as wanted - identities are allowed to exist even if no OwnIdentity wants their messages.
	 * Further, this will NOT even mark the own identity as wanted by itself, this must be done explicitly by handleIndividualWantedStateChangedWithoutCommit
	 */
	protected final void handleNewOwnIdentityWithoutCommit(final OwnIdentity identity) {
		doNewIdentityCallbacks(identity);
		doNewOwnIdentityCallbacks(identity);
	}
	
	private final void doNewOwnIdentityCallbacks(final OwnIdentity identity) {
		Logger.minor(this, "New OwnIdentity, doing callbacks: " + identity);
		
		for(NewOwnIdentityCallback callback : mNewOwnIdentityCallbacks) {
			callback.onNewOwnIdentityAdded(identity);
		}
	}
	
	/**
	 * Interface function for child classes of IdentityManager.
	 * Must be called before an identity is being deleted from the database.
	 * 
	 * Must NOT be called when a new {@link OwnIdentity} is being deleted - use handleOwnIdentityDeletedWithoutCommit instead.
	 * 
	 * This will delete all wanted-states of the identity, effectively marking it as unwanted for message fetching.
	 */
	protected final void handleIdentityDeletedWithoutCommit(final Identity identity) {
		if(identity instanceof OwnIdentity)
			throw new IllegalArgumentException("OwnIdentity passed to handleIdentityDeletedWithoutCommit, see JavaDoc.");
		
		deleteAllWantedStatesWithoutCommit(identity); // This also does the callbacks.
		
		doIdentityDeletedCallbacks(identity);
	}
	
	private final void doIdentityDeletedCallbacks(final Identity identity) {
		Logger.minor(this, "Identity deleted, doing callbacks: " + identity);
		
		for(IdentityDeletedCallback callback : mIdentityDeletedCallbacks) {
			callback.beforeIdentityDeletion(identity);
		}
	}
	
	/**
	 * Interface function for child classes of IdentityManager.
	 * Must be called before an {@link OwnIdentity} is being deleted from the database.
	 * 
	 * This will delete all wanted-states of the identity, effectively marking it as unwanted for message fetching.
	 */
	protected final void handleOwnIdentityDeletedWithoutCommit(final OwnIdentity identity) {
		deleteAllWantedStatesWithoutCommit(identity); // This also does the callbacks.
		
		doIdentityDeletedCallbacks(identity);
		doOwnIdentityDeletedCallbacks(identity);
	}
	
	private final void doOwnIdentityDeletedCallbacks(final OwnIdentity identity) {
		Logger.minor(this, "OwnIdentity deleted, doing callbacks: " + identity);
		
		for(OwnIdentityDeletedCallback callback : mOwnIdentityDeletedCallbacks) {
			callback.beforeOwnIdentityDeletion(identity);
		}
	}
	
	/**
	 * Interface function for child classes of IdentityManager.
	 * Must be called when the wanted-state of an author-{@link Identity} in the scope of an {@link OwnIdentity} changes
	 * - in other words, when an OwnIdentity changes his decision of whether it wants messages from the author-Identity.
	 * 
	 * This will take care of all necessities which arise from that: Message deletion / message subscription, etc.
	 * It will also notice when the overall wanted state has changed (due to all individual states being changed to true/false) and
	 * do the necessary callbacks - that's why there is no function "handleOverallWantedStateChangedWithoutCommit".
	 * @throws NoSuchIdentityException 
	 */
	protected final void handleIndividualWantedStateChangedWithoutCommit(final OwnIdentity owner, final Identity author, boolean newShouldFetch) {
		setIndividualWantedStateWithoutCommit(owner, author, newShouldFetch); // This also does the callbacks.
	}
	
	private final void doOverallWantedStateChangedCallbacks(final Identity author, boolean oldShouldFetch, boolean newShouldFetch) {
		Logger.minor(this, "Overall wanted state changed from " + oldShouldFetch + " to " + newShouldFetch + ", doing callbacks: " + author);
		
		for(OverallWantedStateChangedCallback callback : mOverallWantedStateChangedCallbacks) {
			callback.onOverallWantedStateChanged(author, oldShouldFetch, newShouldFetch);
		}
	}
	
	private final void doIndividualWantedStateChangedCallbacks(final OwnIdentity owner, final Identity author, boolean oldShouldFetch, boolean newShouldFetch) {
		Logger.minor(this, "Individual wanted state changed from " + oldShouldFetch + " to " + newShouldFetch + ", doing callbacks: " + author);
		
		for(IndividualWantedStateChangedCallback callback : mIndividualWantedStateChangedCallbacks) {
			callback.onIndividualWantedStateChanged(owner, author, oldShouldFetch, newShouldFetch);
		}
	}
	
	protected synchronized final IdentityWantedState getIndividualWantedState(OwnIdentity owner, Identity author) throws NoSuchWantedStateException {
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
	private synchronized final boolean getOverallWantedState(Identity author) {
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
	
//	protected synchronized final void setOverallWantedState(final String authorID, boolean shouldFetch) {
//		synchronized(db.lock()) {
//			try {
//				final Identity author = getIdentity(authorID);
//				
//				// We do not have to do update the fetch state since it is not cached - getOverallShouldFetchState computes it from all individual states
//				
//				assert(getOverallWantedState(author) == shouldFetch);
//				
//				// if(stateChanged)	// We cannot figure that out, we trust the caller that it is the case
//					doOverallWantedStateChangedCallbacks(author, !shouldFetch, shouldFetch);
//				
//				Persistent.checkedCommit(db, this);
//			} catch (Exception e) {
//				Persistent.checkedRollbackAndThrow(db, this, new RuntimeException(e));
//			}
//		}
//	}
	
	protected final void setIndividualWantedStateWithoutCommit(final OwnIdentity owner, final Identity author, boolean shouldFetch) {
		final boolean oldOverallWantedState = getOverallWantedState(author);
		
		IdentityWantedState state;
		boolean stateChanged;
		
		try {
			state = getIndividualWantedState(owner, author);
			stateChanged = state.set(shouldFetch);
		} catch(NoSuchWantedStateException e) {
			state = new IdentityWantedState(owner, author, shouldFetch, null);
			state.initializeTransient(mFreetalk);
			stateChanged = true;
		}
		state.storeWithoutCommit();

		if(stateChanged) {
			doIndividualWantedStateChangedCallbacks(owner, author, !shouldFetch, shouldFetch);
		
			// TODO: Optimization: We could avoid getOverallWantedState in some cases by considering in which way the individual state changed.
			if(oldOverallWantedState != getOverallWantedState(author))
				doOverallWantedStateChangedCallbacks(author, oldOverallWantedState, !oldOverallWantedState);
		}
	}
	
//	protected synchronized final void setIndividualWantedState(final String ownerID, final String authorID, boolean shouldFetch) throws NoSuchIdentityException {
//		synchronized(db.lock()) {
//			try {
//				final OwnIdentity owner = getOwnIdentity(ownerID);
//				final Identity author = getIdentity(authorID);
//				
//				IdentityWantedState state;
//				boolean stateChanged;
//				try {
//					state = getIndividualWantedState(owner, author);
//					stateChanged = state.set(shouldFetch);
//				} catch(NoSuchWantedStateException e) {
//					state = new IdentityWantedState(owner, author, shouldFetch, null);
//					state.initializeTransient(mFreetalk);
//					stateChanged = true;
//				}
//				state.storeWithoutCommit();
//				
//				if(stateChanged)
//					doIndividualWantedStateChangedCallbacks(owner, author, !shouldFetch, shouldFetch);
//					
//				Persistent.checkedCommit(db, this);
//			} catch(RuntimeException e) {
//				Persistent.checkedRollbackAndThrow(db, this, e);
//			}
//		}
//	}
	
	/**
	 * Deletes all global and individual wanted-states of the given author, effectively marking him as unwanted.
	 * After this, it calls the wanted-state-changed callbacks. 
	 */
	protected final void deleteAllWantedStatesWithoutCommit(final Identity author) {
		final Query q = db.query();
		q.constrain(IdentityWantedState.class);
		q.descend("mRatedIdentity").constrain(author).identity();
		
		// TODO: Optimization: For public gateway mode with large amounts of OwnIdentities we should cache this in the Identity object.
		// Make sure to adapt the callers (especially setOverallShouldFetchState)
		
		boolean oldOverallWantedState = false;
		
		for(IdentityWantedState state : new Persistent.InitializingObjectSet<IdentityWantedState>(mFreetalk, q)) {
			if(!oldOverallWantedState && state.get()) // We check overallWantedState first as it is faster than get()
				oldOverallWantedState = true;
				
			doIndividualWantedStateChangedCallbacks(state.getOwner(), author, state.get(), false);
			state.deleteWithoutCommit();
		}
		
		if(oldOverallWantedState==true) // The identity was wanted by at least one own identity and now nobody wants it
			doOverallWantedStateChangedCallbacks(author, true, false);		
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
