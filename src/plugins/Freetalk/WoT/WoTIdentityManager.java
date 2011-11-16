/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.PluginTalkerBlocking;
import plugins.Freetalk.exceptions.DuplicateIdentityException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchWantedStateException;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import plugins.Freetalk.exceptions.NotTrustedException;
import plugins.Freetalk.exceptions.WoTDisconnectedException;
import plugins.Freetalk.tasks.PersistentTask;
import plugins.Freetalk.tasks.PersistentTaskManager;
import plugins.Freetalk.tasks.WoT.IntroduceIdentityTask;

import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.node.FSParseException;
import freenet.node.PrioRunnable;
import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
import freenet.support.Base64;
import freenet.support.CurrentTimeUTC;
import freenet.support.Executor;
import freenet.support.IllegalBase64Exception;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.TrivialTicker;
import freenet.support.api.Bucket;
import freenet.support.io.NativeThread;

/**
 * An identity manager which uses the identities from the WoT plugin.
 * 
 * FIXME: Optimization: All functions which require WoT(Own)Identity objects just for obtaining the ID from them to query values out of caches
 * (shortest unique nickname cache, WebOfTrustCache) should be changed to only require an identity ID to make sure that callers don't
 * query identities from the database uselessly
 * FIXME: Also re-check whether the functions which already have duplicates which take a ID instead of an identity are used everywhere where they could be.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class WoTIdentityManager extends IdentityManager implements PrioRunnable {
	
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (3 * 60 * 1000) : (5 * 60 * 1000);
	
	private static final int SHORTEST_NICKNAME_CACHE_UPDATE_DELAY = 30 * 1000;
	
	
	/** The amount of time between each attempt to connect to the WoT plugin */
	private static final int WOT_RECONNECT_DELAY = 5 * 1000; 
	

	private boolean mConnectedToWoT = false;

	/** If true, this identity manager is being use in a unit test - it will return 0 for any score / trust value then */
	private final boolean mIsUnitTest;


	private final TrivialTicker mTicker;
	private final Random mRandom;

	private PluginTalkerBlocking mTalker = null;
	
	/**
	 * Caches the shortest unique nickname for each identity. Key = Identity it, Value = Shortest nickname.
	 */
	private volatile HashMap<String, String> mShortestUniqueNicknameCache = new HashMap<String, String>();
	
	private boolean mShortestUniqueNicknameCacheNeedsUpdate = true;
	
	/**
	 * @see SubscriptionClient
	 */
	private final SubscriptionClient mSubscriptionClient;
	
	/**
	 * @see WebOfTrustCache
	 */
	private final WebOfTrustCache mWoTCache = new WebOfTrustCache(this);
	
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(WoTIdentityManager.class);
	}
	

	public WoTIdentityManager(Freetalk myFreetalk, Executor myExecutor) {
		super(myFreetalk);
		mIsUnitTest = false;
		
		mTicker = new TrivialTicker(myExecutor);
		mRandom = mFreetalk.getPluginRespirator().getNode().fastWeakRandom;
		mSubscriptionClient = new SubscriptionClient(mFreetalk.getIdentityManager(), mFreetalk.getPluginRespirator(), mTicker);
	}
	
	/**
	 * For being used in JUnit tests to run without a node.
	 */
	public WoTIdentityManager(Freetalk myFreetalk) {
		super(myFreetalk);
		mIsUnitTest = true;
		mTicker = null;
		mRandom = null;
		mSubscriptionClient = new SubscriptionClient(mFreetalk.getIdentityManager(), null, null);
	}
	
	// FIXME: Check all callers of callHandle* for whether they do proper synchronization as needed by the subscribers of these events
	// This is a hack, instead subscribers should state which synchronization they need, however we have no infrastructure for that yet.
	
	/**
	 * Called by this WoTIdentityManager after a new WoTIdentity has been stored to the database and before committing the transaction.
	 * 
	 * The purpose of this function is to do necessary stuff which is specific to WoTIdentityManager and then call 
	 * super.handleNewIdentityWithoutCommit, which deals with general stuff and deploys the new-own-identity-callbacks.
	 * 
	 * Specific stuff which done is:
	 * - Marks the shortest unique nickname cache for an update.
	 */
	private final void callHandleNewIdentityWithoutCommit(final WoTIdentity identity) {		
		queueShortesUniqueNicknameCacheUpdate();
		
		super.handleNewIdentityWithoutCommit(identity);
	}
	
	/**
	 * Called by this WoTIdentityManager after a new WoTOwnIdentity has been stored to the database and before committing the transaction.
	 * 
	 * The purpose of this function is to do necessary stuff which is specific to WoTIdentityManager and then call 
	 * super.handleNewOwnIdentityWithoutCommit, which deals with general stuff and deploys the new-own-identity-callbacks.
	 * 
	 * Specific stuff which done is:
	 * - Marks the shortest unique nickname cache for an update.
	 * - Adds the "Freetalk" WOT-context to the identity and stores an {@link IntroduceIdentityTask} for the identity.
	 * Then
	 * - Stores an {@link IntroduceIdentityTask} for the identity
	 * 
	 * You have to lock this WoTIdentityManager, the PersistentTaskManager and the database before calling this function.
	 */
	private final void callHandleNewOwnIdentityWithoutCommit(final WoTOwnIdentity newIdentity) {
		queueShortesUniqueNicknameCacheUpdate();
		
		// TODO: Do after an own message is posted. I have not decided at which place to do this :|
		try {
			addFreetalkContext(newIdentity);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
			
		PersistentTask introductionTask = new IntroduceIdentityTask(newIdentity);
		mFreetalk.getTaskManager().storeTaskWithoutCommit(introductionTask);
		
		super.handleNewOwnIdentityWithoutCommit(newIdentity);
	}
	
	/**
	 * Called by this WoTIdentityManager before a WoTIdentity is deleted from the database (and thus before committing the transaction).
	 * Not called for WoTOwnIdentity objects.
	 * 
	 * The purpose of this function is to do necessary stuff which is specific to WoTIdentityManager and then call 
	 * super.handleIdentityDeletedWithoutCommit, which deals with general stuff and deploys the identity-deleted-callbacks.
	 * 
	 * Specific stuff which done is:
	 * - Marks the shortest unique nickname cache for an update.
	 */
	private final void callHandleIdentityDeletedWithoutCommit(final WoTIdentity identity) {
		queueShortesUniqueNicknameCacheUpdate();
		
		super.handleIdentityDeletedWithoutCommit(identity);
	}
	
	/**
	 * Called by this WoTIdentityManager before a WoTOwnIdentity is deleted from the database (and thus before committing the transaction).
	 * 
	 * The purpose of this function is to do necessary stuff which is specific to WoTIdentityManager and then call 
	 * super.handleOwnIdentityDeletedWithoutCommit, which deals with general stuff and deploys the own-identity-deleted-callbacks.
	 * 
	 * Specific stuff which done is:
	 * - Marks the shortest unique nickname cache for an update.
	 */
	private final void callHandleOwnIdentityDeletedWithoutCommit(final WoTOwnIdentity identity) {
		queueShortesUniqueNicknameCacheUpdate();
		
		// We don't need to delete the PersistentTask which callHandleNewOwnIdentityWithoutCommit created,
		// the PersistentTaskManager registers a callback for identity deletion & deletes all related tasks.
		
		super.handleOwnIdentityDeletedWithoutCommit(identity);
	}
	
	/**
	 * Called by this WoTIdentityManager after the wanted-state of an identity has changed, i.e. its WOT-score changed from "good" to "bad"
	 * or vice versa in the view of an own identity, which changes the "do we want the messages of that identity?" state, we call it "wanted state".
	 * 
	 * The purpose of this function is to do necessary stuff which is specific to WoTIdentityManager and then call 
	 * super.handleIndividualWantedStateChangedWithoutCommit, which deals with general stuff and deploys the wanted-state-changed-callbacks.
	 */
	private final void callHandleIndividualWantedStateChangedWithoutCommit(final WoTOwnIdentity owner, final WoTIdentity author, boolean newShouldFetch) {
		super.handleIndividualWantedStateChangedWithoutCommit(owner, author, newShouldFetch);
	}
	
	
	/**
	 * Sends a blocking FCP message to the WoT plugin, checks whether the reply is really the expected reply message and throws an exception
	 * if not. Also checks whether the reply is an error message and throws an exception if it is. Therefore, the purpose of this function
	 * is that the callee can assume that no errors occurred if no exception is thrown.
	 * 
	 * @param params The params of the FCP message.
	 * @param expectedReplyMessage The excepted content of the "Message" field of the SimpleFieldSet of the reply message.
	 * @return The unmodified HashResult object which was returned by the PluginTalker.
	 * @throws WoTDisconnectedException If the connection to WoT was lost. 
	 * @throws Exception If the WoT plugin replied with an error message or not with the expected message.
	 */
	private PluginTalkerBlocking.Result sendFCPMessageBlocking(SimpleFieldSet params, Bucket data, String expectedReplyMessage) throws Exception {
		if(mTalker == null)
			throw new WoTDisconnectedException();
		
		PluginTalkerBlocking.Result result;
		try {
			result = mTalker.sendBlocking(params, data);
		} catch (PluginNotFoundException e) {
			throw new WoTDisconnectedException();
		}
		
		if(result.params.get("Message").equals("Error")) {
			final String description = result.params.get("Description");
			
			if(description.indexOf("UnknownIdentityException") >= 0)
				throw new NoSuchIdentityException(description);
			
			throw new Exception("FCP message " + result.params.get("OriginalMessage") + " failed: " + description);
		}
		
		if(result.params.get("Message").equals(expectedReplyMessage) == false)
			throw new Exception("FCP message " + params.get("Message") + " received unexpected reply: " + result.params.get("Message"));
		
		return result;
	}
	
	public synchronized WoTOwnIdentity createOwnIdentity(String newNickname, boolean publishesTrustList, boolean publishesIntroductionPuzzles, 
			boolean autoSubscribeToNewBoards, boolean displayImages)
		throws Exception  {
		
		Logger.normal(this, "Creating new own identity via FCP, nickname: " + newNickname);
		
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "CreateIdentity");
		params.putOverwrite("Nickname", newNickname);
		params.putOverwrite("PublishTrustList", publishesTrustList ? "true" : "false");
		params.putOverwrite("PublishIntroductionPuzzles", publishesIntroductionPuzzles ? "true" : "false");
		params.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		PluginTalkerBlocking.Result result = sendFCPMessageBlocking(params, null, "IdentityCreated");
		
		// The cleanest implementation of identity creation would NOT create the WoTOwnIdentity object in the database now:
		// We receive "new identity" events from WOT and have event-handling code for this, it would be ideal to only update the database there.
		// HOWEVER we need to store the user's settings such as autoSubscribeToNewBoards, displayImages, etc. and there is no suitable place
		// for storing them besides the database.
		
		WoTOwnIdentity identity = new WoTOwnIdentity(result.params.get("ID"),
				new FreenetURI(result.params.get("RequestURI")),
				new FreenetURI(result.params.get("InsertURI")),
				newNickname, autoSubscribeToNewBoards, displayImages);
		
		identity.initializeTransient(mFreetalk);
		
		Logger.normal(this, "Created WoTOwnidentity via FCP, now storing... " + identity);
		
		synchronized(mFreetalk.getTaskManager()) { // Required by onNewOwnidentityAdded
		synchronized(Persistent.transactionLock(db)) {
			try {
				identity.storeWithoutCommit();
				callHandleNewOwnIdentityWithoutCommit(identity);
				identity.checkedCommit(this);
				Logger.normal(this, "Stored new WoTOwnIdentity " + identity);
				
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
		}
		
		return identity;
	}
	
	public synchronized WoTOwnIdentity createOwnIdentity(String newNickname, boolean publishesTrustList, boolean publishesIntroductionPuzzles, boolean autoSubscribeToNewBoards,
			boolean displayImages, FreenetURI newRequestURI, FreenetURI newInsertURI) throws Exception {
		Logger.normal(this, "Creating new own identity via FCP, nickname: " + newNickname);
		
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "CreateIdentity");
		params.putOverwrite("Nickname", newNickname);
		params.putOverwrite("PublishTrustList", publishesTrustList ? "true" : "false");
		params.putOverwrite("PublishIntroductionPuzzles", publishesTrustList && publishesIntroductionPuzzles ? "true" : "false");
		params.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		params.putOverwrite("RequestURI", newRequestURI.toString());
		params.putOverwrite("InsertURI", newInsertURI.toString());
		PluginTalkerBlocking.Result result = sendFCPMessageBlocking(params, null, "IdentityCreated");
		
		// The cleanest implementation of identity creation would NOT create the WoTOwnIdentity object in the database now:
		// We receive "new identity" events from WOT and have event-handling code for this, it would be ideal to only update the database there.
		// HOWEVER we need to store the user's settings such as autoSubscribeToNewBoards, displayImages, etc. and there is no suitable place
		// for storing them besides the database.
		
		/* We take the URIs which were returned by the WoT plugin instead of the requested ones because this allows the identity to work
		 * even if the WoT plugin ignores our requested URIs: If we just stored the URIs we requested, we would store an identity with
		 * wrong URIs which would result in the identity not being useable. */
		WoTOwnIdentity identity = new WoTOwnIdentity(result.params.get("ID"),
				new FreenetURI(result.params.get("RequestURI")),
				new FreenetURI(result.params.get("InsertURI")),
				newNickname, autoSubscribeToNewBoards, displayImages);
		
		identity.initializeTransient(mFreetalk);
		
		Logger.normal(this, "Created WoTOwnidentity via FCP, now storing... " + identity);
		
		synchronized(mFreetalk.getTaskManager()) {
		synchronized(Persistent.transactionLock(db)) {
			try {
				identity.storeWithoutCommit();
				callHandleNewOwnIdentityWithoutCommit(identity);
				identity.checkedCommit(this);
				Logger.normal(this, "Stored new WoTOwnIdentity " + identity);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollback(db, this, e);
			}
		}
		}
		
		return identity;
	}
	
	public ObjectSet<WoTIdentity> getAllIdentities() {
		final Query q = db.query();
		q.constrain(WoTIdentity.class);
		return new Persistent.InitializingObjectSet<WoTIdentity>(mFreetalk, q);
	}
	
	public ObjectSet<WoTOwnIdentity> ownIdentityIterator() {
		final Query q = db.query();
		q.constrain(WoTOwnIdentity.class);
		return new Persistent.InitializingObjectSet<WoTOwnIdentity>(mFreetalk, q);

	}

	public synchronized WoTIdentity getIdentity(String id) throws NoSuchIdentityException {
		final Query q = db.query();
		q.constrain(WoTIdentity.class);
		q.descend("mID").constrain(id);
		final ObjectSet<WoTIdentity> result = new Persistent.InitializingObjectSet<WoTIdentity>(mFreetalk, q);
		
		switch(result.size()) {
			case 1: return result.next();
			case 0: throw new NoSuchIdentityException(id);
			default: throw new DuplicateIdentityException(id);
		}
	}
	
	public Identity getIdentityByURI(FreenetURI uri) throws NoSuchIdentityException {
		return getIdentity(WoTIdentity.getIDFromURI(uri));
	}
	
	public synchronized WoTOwnIdentity getOwnIdentity(String id) throws NoSuchIdentityException {
		final Query q = db.query();
		q.constrain(WoTOwnIdentity.class);
		q.descend("mID").constrain(id);
		final ObjectSet<WoTOwnIdentity> result = new Persistent.InitializingObjectSet<WoTOwnIdentity>(mFreetalk, q);
		
		switch(result.size()) {
			case 1: return result.next();
			case 0: throw new NoSuchIdentityException(id);
			default: throw new DuplicateIdentityException(id);
		}
	}

	/**
	 * If you only have IDs of the identities available, please use {@link #getScore(String, String)}
	 * instead of querying the identity objects from the database - that function obtains the score without
	 * a database query which is significantly faster.
	 */
	public int getScore(final WoTOwnIdentity truster, final WoTIdentity trustee) throws NotInTrustTreeException {
		return getScore(truster.getID(), trustee.getID());
	}
	
	public int getScore(final String truster, final String trustee) throws NotInTrustTreeException {
		return mWoTCache.getScore(truster, trustee);
	}

	/**
	 * If you only have IDs of the identities available, please use {@link #getTrust(String, String)}
	 * instead of querying the identity objects from the database - that function obtains the trust without
	 * a database query which is significantly faster.
	 */
	public byte getTrust(final WoTIdentity truster, final WoTIdentity trustee) throws NotTrustedException {
		return getTrust(truster.getID(), trustee.getID());
	}

	
	public byte getTrust(final String trusterID, final String trusteeID) throws NotTrustedException {
		return mWoTCache.getTrust(trusterID, trusteeID);
	}

	
	/**
	 * Not synchronized, the involved identities might be deleted during the query or some other WoT client might modify the trust value
	 * during the query - which is not really a problem, you should not be modifying trust values of your own identity with multiple clients simultaneously.
	 */
	public void setTrust(WoTOwnIdentity truster, WoTIdentity trustee, byte trust, String comment) throws Exception {
		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "SetTrust");
		request.putOverwrite("Truster", truster.getID());
		request.putOverwrite("Trustee", trustee.getID());
		request.putOverwrite("Value", Integer.toString(trust));
		request.putOverwrite("Comment", comment);

		sendFCPMessageBlocking(request, null, "TrustSet");
		
		// We will receive an event-notification from WOT about the trust change anyway, it is probably better to only have one place
		// where we handle trust changes and that place is the event-handling code.
		
		// mWoTCache.putTrust(truster.getID(), trustee.getID(), trust, comment);
	}
	
	/**
	 * Get the number of trust values for a given identity with the ability to select which values should be counted.
	 * 
	 * Not synchronized, the involved identity might be deleted during the query - which is not really a problem.
	 * 
	 * @param selection Use 1 for counting trust values greater than or equal to zero, 0 for counting trust values exactly equal to 0 and -1 for counting trust
	 * 		values less than zero.
	 */
	public int getReceivedTrustsCount(WoTIdentity trustee, int selection) throws Exception {
		return mWoTCache.getReceivedTrustCount(trustee.getID(), selection);
	}
	
	public int getGivenTrustsCount(WoTIdentity truster, int selection) throws Exception {
		return mWoTCache.getGivenTrustCount(truster.getID(), selection);
	}
	
	public static final class IntroductionPuzzle {
		public final String ID;
		public final String MimeType;
		public final byte[] Data;
		
		protected IntroductionPuzzle(String myID, String myMimeType, byte[] myData) {
			if(myID == null) throw new NullPointerException();
			if(myMimeType == null) throw new NullPointerException();
			if(myData == null) throw new NullPointerException();
			
			ID = myID;
			MimeType = myMimeType;
			Data = myData;
		}
	}
	
	/**
	 * Get a set of introduction puzzle IDs which the given own identity might solve.
	 * 
	 * The puzzle's data is not returned because when generating HTML for displaying the puzzles we must reference them with a IMG-tag and we cannot
	 * embed the data of the puzzles in the IMG-tag because embedding image-data has only recently been added to browsers and many do not support it yet.
	 * 
	 * @param ownIdentity The identity which wants to solve the puzzles.
	 * @param amount The amount of puzzles to request.
	 * @return A list of the IDs of the puzzles. The amount might be less than the requested amount and even zero if WoT has not downloaded puzzles yet.
	 * @throws Exception 
	 */
	public List<String> getIntroductionPuzzles(WoTOwnIdentity ownIdentity, int amount) throws Exception {
		ArrayList<String> puzzleIDs = new ArrayList<String>(amount + 1);
		
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "GetIntroductionPuzzles");
		params.putOverwrite("Identity", ownIdentity.getID());
		params.putOverwrite("Type", "Captcha"); // TODO: Don't hardcode the String
		params.put("Amount", amount);
		
		try {
			SimpleFieldSet result = sendFCPMessageBlocking(params, null, "IntroductionPuzzles").params;
			
			for(int idx = 0; ; idx++) {
				String id = result.get("Puzzle" + idx);
				
				if(id == null || id.equals("")) /* TODO: Figure out whether the second condition is necessary */
					break;
				
				puzzleIDs.add(id);
			}
		}
		catch(PluginNotFoundException e) {
			Logger.error(this, "Getting puzzles failed", e);
		}
		
		return puzzleIDs;
	}
	
	public IntroductionPuzzle getIntroductionPuzzle(String id) throws Exception {
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "GetIntroductionPuzzle");
		params.putOverwrite("Puzzle", id);
		
		try {
			SimpleFieldSet result = sendFCPMessageBlocking(params, null, "IntroductionPuzzle").params;
			
			try {
				return new IntroductionPuzzle(id, result.get("MimeType"), Base64.decodeStandard(result.get("Data")));
			}
			catch(RuntimeException e) {
				Logger.error(this, "Parsing puzzle failed", e);
			}
			catch(IllegalBase64Exception e) {
				Logger.error(this, "Parsing puzzle failed", e);
			}
			
			return null;
		}
		catch(PluginNotFoundException e) {
			Logger.error(this, "Getting puzzles failed", e);
			
			return null;
		}
	}
	
	public void solveIntroductionPuzzle(WoTOwnIdentity ownIdentity, String puzzleID, String solution) throws Exception {
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "SolveIntroductionPuzzle");
		params.putOverwrite("Identity", ownIdentity.getID());
		params.putOverwrite("Puzzle", puzzleID);
		params.putOverwrite("Solution", solution);
		
		sendFCPMessageBlocking(params, null, "PuzzleSolved");
	}
	

	private synchronized void addFreetalkContext(WoTIdentity oid) throws Exception {
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "AddContext");
		params.putOverwrite("Identity", oid.getID());
		params.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		sendFCPMessageBlocking(params, null, "ContextAdded");
	}
	
	private void importIdentity(boolean ownIdentity, String identityID, String requestURI, String insertURI, String nickname, long importID) {
		synchronized(this) {
			try {
				final Identity existingIdentity = getIdentity(identityID);
				
				// If we receive an event which tells us that the type of an identity has changed, we shall 
				// handle it as if it was deleted and then added again - this is the easiest way to implement handling type-changes
				// FIXME: Implement this in the callers of this function.
				if((existingIdentity instanceof OwnIdentity) != ownIdentity)
					throw new RuntimeException("Type of identity changed, cannot import: " + existingIdentity);
				
				// The identity already exists because:
				// - WoT currently does not differentiate between "new identity" notifications and "identity changed" notifications
				// - When the user creates an own identity via Freetalk, it is stored in the database directly without waiting for the WOT callback
				// TODO: Process "identity changed" notifications, maybe there is some information on identities with which we should deal
				if(logDEBUG)
					Logger.debug(this, "Not importing identity, it already exists: " + identityID);
				return;
			} catch(NoSuchIdentityException e) {}
		
			// FIXME: Check whether this locking is enough / too much
			synchronized(mFreetalk.getTaskManager()) {
			synchronized(Persistent.transactionLock(db)) {
				try {
					Logger.normal(this, "Importing identity from WoT: " + requestURI);
					final WoTIdentity id = ownIdentity ? new WoTOwnIdentity(identityID, new FreenetURI(requestURI), new FreenetURI(insertURI), nickname) :
						new WoTIdentity(identityID, new FreenetURI(requestURI), nickname);
	
					id.initializeTransient(mFreetalk);
					id.setLastReceivedFromWoT(importID);
					id.storeWithoutCommit();
	
					if(ownIdentity)
						callHandleNewOwnIdentityWithoutCommit((WoTOwnIdentity)id);
					else 
						callHandleNewIdentityWithoutCommit(id);
	
					id.checkedCommit(this);
				}
				catch(Exception e) {
					Persistent.checkedRollbackAndThrow(db, this, new RuntimeException(e));
				}
			}
			}
		}
	}
	
	/**
	 * Imports a {@link WoTIdentity} from a {@link SimpleFieldSet} with contents as specified by the WOT FCP function "GetIdentity"
	 */
	private final void importIdentity(final boolean ownIdentity, final SimpleFieldSet data) {
		assert(data.get("Message").equals(ownIdentity ? "OwnIdentity" : "Identity"));
		
		final String nickname = data.get("Nickname");
		final String identityID = data.get("ID");
		
		// FIXME: Check whether we successfully obtain the identity due to identity-changed notifications when the nickname is available.
		if(nickname == null) {
			Logger.normal(this, "Ignoring identity while nickname is null: " + identityID);
			return;
		}
		
		final String requestURI = data.get("RequestURI");
		final String insertURI = ownIdentity ? data.get("InsertURI") : null;
		final long importID = mRandom.nextLong();
		
		importIdentity(ownIdentity, identityID, requestURI, insertURI, nickname, importID);
	}
	
	/**
	 * Imports a {@link WoTTrust} from a {@link SimpleFieldSet} with contents as specified by the WOT FCP function "GetTrust"
	 */
	private final void importTrustValue(final SimpleFieldSet data) throws NoSuchIdentityException {
		// FIXME: Handle trust deletion
		
		assert(data.get("Message").equals("Trust"));
		
		final String trusterID = data.get("Truster");
		final String trusteeID = data.get("Trustee");
		final byte value = Byte.parseByte(data.get("Value"));
		final String comment = data.get("Comment");
		
		// We don't need a transaction, the trust-values are not stored in the database
				
		mWoTCache.putTrust(trusterID, trusteeID, value, comment);

		// No need to synchronize the wanted-states of identities here: Only the score of an identity is relevant.
	}
	
	/**
	 * Imports a {@link WoTScore} from a {@link SimpleFieldSet} with contents as specified by the WOT FCP function "GetScore"
	 */
	private final void importScoreValue(final SimpleFieldSet data) throws NoSuchIdentityException {
		// FIXME: Handle score deletion
		
		assert(data.get("Message").equals("Score"));
		
		final String trusterID = data.get("Truster");
		final String trusteeID = data.get("Trustee");
		final int value = Integer.parseInt(data.get("Value"));
		
		synchronized(mWoTCache) {
			boolean wantedStateChanged = false;
			
			try {
				if(Integer.signum(mWoTCache.getScore(trusterID, trusteeID)) != Integer.signum(value)) 
					wantedStateChanged = true;
			} catch (NotInTrustTreeException e) {
				wantedStateChanged = true;
			}
			
			if(!wantedStateChanged) {
				// The score did not change from positive to negative (or vice-versa) so we do not need to update 
				// the database contents and can update the cache & return without synchronizing on this WoTIdentityManager
				mWoTCache.putScore(trusterID, trusteeID, value);
				return;
			} else {
				// We cannot update the cache now due to locking issues - see the comment below
			}
		}
		
		// The wanted-state changed so we need to synchronize on this WoTIdentityManager & update the wanted-state in the database.
		// We must release & re-take the WoTCache lock because the locking order must be: WoTIdentityManager first, then WoTCache.
		// FIXME: Figure out whether we need all of those locks
		synchronized(this) {
		synchronized(mFreetalk.getMessageManager()) {
		synchronized(mFreetalk.getTaskManager()) {
		synchronized(mWoTCache) {
		synchronized(Persistent.transactionLock(db)) {
			try {
				// We must re-detect whether it has really changed because we have released the lock on the WoTCache in between.
				boolean wantedStateChanged = false;
				try {
					if(Integer.signum(mWoTCache.getScore(trusterID, trusteeID)) != Integer.signum(value)) 
						wantedStateChanged = true;
				} catch (NotInTrustTreeException e) {
					wantedStateChanged = true;
				}
				
				mWoTCache.putScore(trusterID, trusteeID, value);
				
				if(wantedStateChanged) {
					final boolean shouldFetch = value >= 0;
					
					callHandleIndividualWantedStateChangedWithoutCommit(getOwnIdentity(trusterID), getIdentity(trusteeID), shouldFetch);
				}
				Persistent.checkedCommit(db, this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
		}
		}
		}
		}
	}
	
	
	/**
	 * Parses the identities in the given SimpleFieldSet & imports all (except those which do not have a nickname yet because they were not downloaded)
	 * Deletes any identities in the database which are not included in the passed SimpleFieldSet.
	 * 
	 * FIXME: Check the transaction-layout, especially the locking, should be similar to synchronizeScoreValues
	 * FIXME: Add a validation mode: For debugging, WOT also triggers an identity synchronization during shutdown: We can then check whether our
	 * cache which we created from incremental change-notifications is correct.
	 */
	private synchronized void synchronizeIdentities(SimpleFieldSet params) {
		synchronized(mFreetalk.getMessageManager()) {
		synchronized(mFreetalk.getTaskManager()) {
		synchronized(Persistent.transactionLock(db)) {
		try {
			Logger.normal(this, "Parsing received identities...");
		
			int count;
			int ignoredCount = 0;
			int newCount = 0;
			int typeChangedCount = 0;
			
			// For being able to delete the identities which do not exist anymore in the given SimpleFieldSet we generate a random ID of this import
			// and store it on all received identities. The stored identities which do NOT have these ID are obsolete and must be deleted.
			final long importID = mRandom.nextLong();
			
			for(count = 0; ; count++) {
				final String identityID = params.get("Identity"+count);
				if(identityID == null || identityID.length() == 0)
					break;
				
				final String requestURI = params.get("RequestURI"+count);
				final String insertURI = params.get("InsertURI"+count);
				final String nickname = params.get("Nickname"+count);
				
				final boolean isOwnIdentity = insertURI == null || insertURI.length() == 0;
					
				if(nickname == null || nickname.length() == 0) {
					// If an identity publishes an invalid nickname in one of its first WoT inserts then WoT will return an empty
					// nickname for that identity until a new XML was published with a valid nickname. We ignore the identity until
					// then to prevent confusing error logs..
					continue;
				}
				
	
				WoTIdentity id;
	
				try {
					id = getIdentity(identityID);
				} catch(NoSuchIdentityException e) {
					id = null;
				}
	
				if(id == null) {
					importIdentity(isOwnIdentity, identityID, requestURI, insertURI, nickname, importID);
					++newCount;
				} else {
					if(isOwnIdentity != (id instanceof WoTOwnIdentity)) {
						// The type of the identity changed so we need to delete and re-import it.
						++typeChangedCount;
	
						try {
							Logger.normal(this, "Identity type changed, replacing it: " + id);
							// We MUST NOT take the following locks because deleteIdentity does other locks (MessageManager/TaskManager) which must happen before...
							// synchronized(id)
							// synchronized(Persistent.transactionLock(db)) 
							deleteIdentity(id);
							importIdentity(isOwnIdentity, identityID, requestURI, insertURI, nickname, importID);
						}
						catch(Exception e) {
							Logger.error(this, "Replacing a WoTIdentity with WoTOwnIdentity failed.", e);
							throw new RuntimeException(e);
						}
	
					} else { // Normal case: Update the last received ID of the identity;
						if(logDEBUG) Logger.debug(this, "Not importing already existing identity " + requestURI);
						
						++ignoredCount;
						synchronized(id) {
							id.setLastReceivedFromWoT(importID);
							id.storeWithoutCommit();
						}
					}
				}
	
			}
			
			Logger.normal(this, "Deleting obsolete identities...");
			
			int deletedCount = 0;
			
			for(WoTIdentity obsoleteIdentity : getObsoleteIdentities(importID)) {
				try {
					deleteIdentity(obsoleteIdentity);
					++deletedCount;
				} catch(RuntimeException e) {
					Logger.error(this, "Deleting obsolete identity failed", e);
				}
			}
			
			Logger.normal(this, "Finished deleting obsolete identities.");
			
			Logger.normal(this, "synchronizeIdentities received " + count + " identities. Ignored " + ignoredCount + "; New: " + newCount + 
					"; deleted: " + deletedCount + "; type changed: " + typeChangedCount);
		
			Persistent.checkedCommit(db, this);
		} catch(RuntimeException e) {
			Persistent.checkedRollbackAndThrow(db, this, e);
		}
		}
		}
		}
	}
	
	/**
	 * FIXME: Add a validation mode: For debugging, WOT also triggers a trust synchronization during shutdown: We can then check whether our
	 * cache which we created from incremental change-notifications is correct.
	 */
	private final void synchronizeTrustValues(final SimpleFieldSet data) {
		synchronized(this) {
		synchronized(mWoTCache) {
		// We do not have to do a transaction since we only act upon the WoT-cache which is in-memory
		//synchronized(Persistent.transactionLock(db)) {
		try {
			mWoTCache.clearTrustValues();
			
			for(int index=0; ; ++index) {
				final String trusterID = data.get("Truster" + index);
				
				if(trusterID == null || trusterID.length() == 0)
					break;
				
				final String trusteeID = data.get("Trustee" + index);
				final byte value = Byte.parseByte(data.get("Value" + index));
				final String comment = data.get("Comment" + index);
				
				mWoTCache.putTrust(trusterID, trusteeID, value, comment);
				
				// No need to synchronize the wanted-states of identities here: Only the score of an identity is relevant.
			}
		} catch(Exception e) {
			// We are no transaction.
			//	Persistent.checkedRollbackAndThrow(db, this, new RuntimeException(e));
			
			// We do not need to undo the changes to the WOT-cache here:
			// If an exception is thrown during the trust-synchronization, WOT will be told that the current synchronization-processing
			// failed and try to synchronize again after some minutes.
			throw new RuntimeException(e);
		}
		// }
		}
		}
	}
	
	/**
	 * FIXME: Add a validation mode: For debugging, WOT also triggers a score synchronization during shutdown: We can then check whether our
	 * cache which we created from incremental change-notifications is correct.
	 */
	private final void synchronizeScoreValues(final SimpleFieldSet data) {
		synchronized(this) {
		synchronized(mFreetalk.getMessageManager()) { // FIXME: Do we need this lock?
		synchronized(mFreetalk.getTaskManager()) { // FIXME: Do we need this lock?
		synchronized(mWoTCache) {
		synchronized(Persistent.transactionLock(db)) {
		try {
			mWoTCache.clearScoreValues();
			
			for(int index=0; ; ++index) {
				final String trusterID = data.get("Truster" + index);
				
				if(trusterID == null || trusterID.length() == 0)
					break;
				
				final String trusteeID = data.get("Trustee" + index);
				final int value = Integer.parseInt(data.get("Value" + index));
				
				mWoTCache.putScore(trusterID, trusteeID, value);		
				
				final WoTOwnIdentity truster = getOwnIdentity(trusterID);
				final WoTIdentity trustee = getIdentity(trusteeID);
				final boolean identityIsWanted = (value >= 0);
				
				boolean doCallback = false;
				
				try {
					if(getIndividualWantedState(truster, trustee).callingSetWouldChangeWantedState(identityIsWanted))
						doCallback = true;
				} catch(NoSuchWantedStateException e) {
					doCallback = true;	
				}
				
				if(doCallback)
					callHandleIndividualWantedStateChangedWithoutCommit(truster, trustee, identityIsWanted);
			}
		} catch(Exception e) {
			// We do not need to undo the changes to the WOT-cache here:
			// If an exception is thrown during the score-synchronization, WOT will be told that the current synchronization-processing
			// failed and try to synchronize again after some minutes.
			
			Persistent.checkedRollbackAndThrow(db, this, new RuntimeException(e));
		}
		}
		}
		}
		}
		}
	}
	

	
	/**
	 * Get the identities which were last seen in an import with a different ID than the given one.
	 */
	private ObjectSet<WoTIdentity> getObsoleteIdentities(long importID) {
		final Query q = db.query();
		q.constrain(WoTIdentity.class);
		q.descend("mLastReceivedFromWoT").constrain(importID).not();
		return new Persistent.InitializingObjectSet<WoTIdentity>(mFreetalk, q);
	}
	
	private synchronized void deleteIdentity(WoTIdentity identity) {	
		identity.initializeTransient(mFreetalk);
		
		synchronized(mFreetalk.getMessageManager()) { // FIXME: Do we need this lock?
		synchronized(mFreetalk.getTaskManager()) { // FIXME: Do we need this lock?
		synchronized(identity) {
		synchronized(Persistent.transactionLock(db)) {
			try {
				if(identity instanceof WoTOwnIdentity)
		 			callHandleOwnIdentityDeletedWithoutCommit((WoTOwnIdentity)identity);
				else
					callHandleIdentityDeletedWithoutCommit(identity);
				
				identity.deleteWithoutCommit();
				identity.checkedCommit(this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
		}
		}
		}
	}

	private synchronized boolean connectToWoT() {
		if(mTalker != null) { /* Old connection exists */
			SimpleFieldSet sfs = new SimpleFieldSet(true);
			sfs.putOverwrite("Message", "Ping");
			try {
				mTalker.sendBlocking(sfs, null); /* Verify that the old connection is still alive */
				return true;
			}
			catch(PluginNotFoundException e) {
				mTalker = null;
				/* Do not return, try to reconnect in next try{} block */
			}
		}
		
		try {
			mTalker = new PluginTalkerBlocking(mFreetalk.getPluginRespirator());
			mFreetalk.handleWotConnected();
			return true;
		} catch(PluginNotFoundException e) {
			mFreetalk.handleWotDisconnected();
			return false;
		}
	}
	
	private static final class SubscriptionClient implements FredPluginTalker, Runnable, PrioRunnable {
		
		private final WoTIdentityManager mIdentityManager;
		private final PluginRespirator mPR;
		private final TrivialTicker mTicker;
		private final Random mRandom;
		private PluginTalker mTalker;
		
		/**
		 * The IDs of the Subscriptions we filed at the WebOfTrust-plugin
		 */
		private final HashSet<String> mSubscriptions = new HashSet<String>();		
		
		private boolean mLastPingSuccessful = false;
		
		public SubscriptionClient(WoTIdentityManager myIdentityManager, PluginRespirator pr, TrivialTicker myTicker){
			mIdentityManager = myIdentityManager;
			mPR = pr;
			mTicker = myTicker;
			mRandom = mPR == null ? null : mPR.getNode().fastWeakRandom;
			mTalker = null;
			
			createFCPMessageHandlers();
		}
		
		public void start() {
			Logger.debug(this, "Starting...");
			mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), 0, false, true);
			Logger.debug(this, "Started.");
		}
		
		public void stop() {
			Logger.debug(this, "Terminating...");
			try {
				mTicker.shutdown();
				unsubscribeAllSubscriptions();
				disconnectFromWoT();
			} catch(RuntimeException e) {
				Logger.error(this, "Termination failed", e);
			}
			Logger.debug(this, "Terminated");
		}
		
		@Override
		public int getPriority() {
			return NativeThread.LOW_PRIORITY;
		}
		
		public void run() {
			if(logDEBUG) Logger.debug(this, "Main loop running...");
			 
			try {
				checkConnectionToWoT();	
				checkSubscriptions();
			} finally {
				final long sleepTime = connectedToWoT() ? (THREAD_PERIOD/2 + mRandom.nextInt(THREAD_PERIOD)) : WOT_RECONNECT_DELAY;
				Logger.debug(this, "Sleeping for " + (sleepTime / (60*1000)) + " minutes.");
				mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), sleepTime, false, true);
			}
			
			Logger.debug(this, "Main loop finished.");
		}
		
		private synchronized boolean checkConnectionToWoT() {
			if(mTalker != null) {
				// The handler for Ping-reply sets mLastPingSuccessful=true... so we set it to false upon sending a ping.
				// This function is called by run() every few minutes, so if we reach this point the connection is alive
				// if mLastPingSuccesful=true ... if false the connection is dead and we try to reconnect.
				if(mLastPingSuccessful) {
					final SimpleFieldSet sfs = new SimpleFieldSet(true);
					sfs.putOverwrite("Message", "Ping");
					mLastPingSuccessful = false;
					mTalker.send(sfs, null);
					return true;
				} else
					mTalker = null;
			}
			
			try {
				mTalker = mPR.getPluginTalker(this, Freetalk.WEB_OF_TRUST_NAME, UUID.randomUUID().toString());
				mLastPingSuccessful = true;
				checkSubscriptions();
				return true;
			} catch(PluginNotFoundException e) {
				return false;
			}
		}
		
		public boolean connectedToWoT() {
			return mTalker != null;
		}
		
		private void disconnectFromWoT() {
			// TODO: Implement proper disconnection in PluginTalker
			mTalker = null;
		}
		
		
		private void checkSubscriptions() {
			if(mSubscriptions.size() == 3)
				return;
			
			if(!connectedToWoT())
				return;
			
			subscribe("IdentityList");
			subscribe("TrustList");
			subscribe("ScoreList");
		}
		
		private void subscribe(final String contentName) {
			final SimpleFieldSet request = new SimpleFieldSet(true);
			request.putOverwrite("Message", "Subscribe");
			request.putOverwrite("To", contentName);
			mTalker.send(request, null);
		}
		
		private void unsubscribe(final String subscriptionID) {
			final SimpleFieldSet request = new SimpleFieldSet(true);
			request.putOverwrite("Message", "Unsubscribe");
			request.putOverwrite("SubscriptionID", subscriptionID);
			mTalker.send(request, null);
		}
		
		/**
		 * Unsubscribes from all existing subscriptions to the WOT plugin.
		 * Does not prevent new subscriptions from being created afterwards.
		 * - You should make sure that you call this after all code which could create subscriptions has been terminated.
		 */
		private void unsubscribeAllSubscriptions() {
			synchronized(mSubscriptions) {
				for(String subscription : mSubscriptions) {
					unsubscribe(subscription);
				}
				
				do {
					try {
						mSubscriptions.wait();
					} catch(InterruptedException e) {}
				} while(!mSubscriptions.isEmpty());
			}
		}
		
		private static interface FCPMessageHandler {
			public void handle(final SimpleFieldSet params);
		}
		
		private final HashMap<String, FCPMessageHandler> mFCPHandlers = new HashMap<String, FCPMessageHandler>();
		
		@Override
		public void onReply(String pluginname, String indentifier, SimpleFieldSet params, Bucket data) {
			mFCPHandlers.get(params.get("Message")).handle(params);
		}
		
		private void createFCPMessageHandlers() {
			mFCPHandlers.put("Subscribed", new FCPMessageHandler() {
				@Override
				public void handle(final SimpleFieldSet params) {
					synchronized(mSubscriptions) {
						mSubscriptions.add(params.get("Subscription"));
						mSubscriptions.notifyAll();
						assert(mSubscriptions.size() <= 3);
					}
				}
			});
			
			mFCPHandlers.put("Unsubscribed", new FCPMessageHandler() {
				@Override
				public void handle(final SimpleFieldSet params) {
					synchronized(mSubscriptions) {
						final boolean subscriptionExisted = mSubscriptions.remove(params.get("Subscription"));
						assert(subscriptionExisted);
						mSubscriptions.notifyAll();
					}
				}
			});

			// Upon subscribing to the list of identities, the WOT-plugin sends an initial list of all identities to synchronize us.
			mFCPHandlers.put("Identities", new FCPMessageHandler() {
				@Override
				public void handle(SimpleFieldSet params) {
					mIdentityManager.synchronizeIdentities(params);
				}
			});
			
			// Upon subscribing to the list of trust values, the WOT-plugin sends an initial list of all identities to synchronize us.
			mFCPHandlers.put("TrustValues", new FCPMessageHandler() {
				@Override
				public void handle(SimpleFieldSet params) {
					mIdentityManager.synchronizeTrustValues(params);
				}
			});
			
			
			// Upon subscribing to the list of trust values, the WOT-plugin sends an initial list of all identities to synchronize us.
			mFCPHandlers.put("ScoreValues", new FCPMessageHandler() {
				@Override
				public void handle(SimpleFieldSet params) {
					mIdentityManager.synchronizeScoreValues(params);
				}
			});
			
			mFCPHandlers.put("Identity", new FCPMessageHandler() {
				@Override
				public void handle(SimpleFieldSet params) {
					mIdentityManager.importIdentity(false, params);				
				}
			});
			
			mFCPHandlers.put("OwnIdentity", new FCPMessageHandler() {
				@Override
				public void handle(SimpleFieldSet params) {
					mIdentityManager.importIdentity(true, params);
				}
			});
			
			mFCPHandlers.put("Trust", new FCPMessageHandler() {
				@Override
				public void handle(SimpleFieldSet params) {
					try {
						mIdentityManager.importTrustValue(params);
					} catch (NoSuchIdentityException e) {
						throw new RuntimeException(e);
					}
				}
			});
			
			mFCPHandlers.put("Score", new FCPMessageHandler() {
				@Override
				public void handle(SimpleFieldSet params) {
					try {
						mIdentityManager.importScoreValue(params);
					} catch(NoSuchIdentityException e) {
						throw new RuntimeException(e);
					}
				}
			});
			
			mFCPHandlers.put("Pong", new FCPMessageHandler() {
				@Override
				public void handle(SimpleFieldSet params) {
					synchronized(SubscriptionClient.this) {
						mLastPingSuccessful = true;
					}
				}
			});
		}
		
	}

	public void run() { 
		Logger.debug(this, "Main loop running...");
		 
		try {
			mConnectedToWoT = connectToWoT();
			
			if(mShortestUniqueNicknameCacheNeedsUpdate) {
				try {
					updateShortestUniqueNicknameCache();
				} catch(Exception e) {
					Logger.error(this, "Initializing shortest unique nickname cache failed", e);
				}
			}
			
		} finally {
			final long sleepTime =  mConnectedToWoT ? (THREAD_PERIOD/2 + mRandom.nextInt(THREAD_PERIOD)) : WOT_RECONNECT_DELAY;
			if(logDEBUG) Logger.debug(this, "Sleeping for " + (sleepTime / (60*1000)) + " minutes.");
			mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), sleepTime, false, true);
		}
		
		if(logDEBUG) Logger.debug(this, "Main loop finished.");
	}
	
	public int getPriority() {
		return NativeThread.MIN_PRIORITY;
	}
	
	private void queueShortesUniqueNicknameCacheUpdate() {
		// TODO: Do not abuse the main loop for running this - it also checks the connection to WOT
		mShortestUniqueNicknameCacheNeedsUpdate = true;
		mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), SHORTEST_NICKNAME_CACHE_UPDATE_DELAY, false, true);
	}
	
	public void start() {
		if(logDEBUG) Logger.debug(this, "Starting...");
		
		if(logDEBUG)
			deleteDuplicateIdentities();
		
		mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), 0, false, true);
		
		mSubscriptionClient.start();
		
		if(logDEBUG) Logger.debug(this, "Started.");
	}
	
	/**
	 * Checks for duplicate identity objects and deletes duplicates if they exist.
	 * I have absolutely NO idea why Bombe does happen to have a duplicate identity, I see no code path which could cause this.
	 * TODO: Get rid of this function if nobody reports a duplicate for some time - the function was added at 2011-01-10
	 */
	private synchronized void deleteDuplicateIdentities() {
		WoTMessageManager messageManager = mFreetalk.getMessageManager();
		PersistentTaskManager taskManager = mFreetalk.getTaskManager();
		
		synchronized(messageManager) {
		synchronized(taskManager) {
		synchronized(Persistent.transactionLock(db)) {
			try {
				HashSet<String> deleted = new HashSet<String>();

				Logger.debug(this, "Searching for duplicate identities ...");

				for(WoTIdentity identity : getAllIdentities()) {
					Query q = db.query();
					q.constrain(WoTIdentity.class);
					q.descend("mID").constrain(identity.getID());
					q.constrain(identity).identity().not();
					ObjectSet<WoTIdentity> duplicates = new Persistent.InitializingObjectSet<WoTIdentity>(mFreetalk, q);
					
					for(WoTIdentity duplicate : duplicates) {
						if(deleted.contains(duplicate.getID()) == false) {
							Logger.error(duplicate, "Deleting duplicate identity " + duplicate.getRequestURI());
							deleteIdentity(duplicate);
						}
					}
					deleted.add(identity.getID());
				}
				Persistent.checkedCommit(db, this);

				Logger.debug(this, "Finished searching for duplicate identities.");
			}
			catch(RuntimeException e) {
				Persistent.checkedRollback(db, this, e);
			}
		}
		}
		}
	}
	
	public void terminate() {
		if(logDEBUG) Logger.debug(this, "Terminating ...");
		mTicker.shutdown();
		mSubscriptionClient.stop();
		if(logDEBUG) Logger.debug(this, "Terminated.");
	}

	
	// TODO: This function should be a feature of WoT.
	private synchronized void updateShortestUniqueNicknameCache() {
		if(logDEBUG) Logger.debug(this, "Updating shortest unique nickname cache...");
		
		// We don't use getAllIdentities() because we do not need to have intializeTransient() called on each identity, we only query strings anyway.
		final Query q = db.query();
		q.constrain(WoTIdentity.class);
		ObjectSet<WoTIdentity> result = new Persistent.InitializingObjectSet<WoTIdentity>(mFreetalk, q);
		final WoTIdentity[] identities = result.toArray(new WoTIdentity[result.size()]);
		
		Arrays.sort(identities, new Comparator<WoTIdentity>() {

			public int compare(WoTIdentity i1, WoTIdentity i2) {
				return i1.getFreetalkAddress().compareToIgnoreCase(i2.getFreetalkAddress());
			}
			
		});
		
		final String[] nicknames = new String[identities.length];
		
		for(int i=0; i < identities.length; ++i) {
			nicknames[i] = identities[i].getNickname();
			
			int minLength = nicknames[i].length();
			int firstDuplicate;
			
			do {
				firstDuplicate = i;
				
				while((firstDuplicate-1) >= 0 && nicknames[firstDuplicate-1].equalsIgnoreCase(nicknames[i])) {
					--firstDuplicate;
				}
			
				if(firstDuplicate < i) {
					++minLength;
					
					for(int j=i; j >= firstDuplicate; --j) {
						nicknames[j] = identities[j].getFreetalkAddress(minLength);
					}
				}
			} while(firstDuplicate != i);
		}
		
		final HashMap<String,String> newCache = new HashMap<String, String>(identities.length * 2);
		
		for(int i = 0; i < identities.length; ++i)
			newCache.put(identities[i].getID(), nicknames[i]);
		
		mShortestUniqueNicknameCache = newCache;
		mShortestUniqueNicknameCacheNeedsUpdate = false;
		
		if(logDEBUG) Logger.debug(this, "Finished updating shortest unique nickname cache.");
	}

	@Override
	public String getShortestUniqueName(Identity identity) {
		// We must not synchronize anything according to the specification of this function (to prevent deadlocks)
		String nickname = mShortestUniqueNicknameCache.get(identity.getID());
		
		if(nickname == null)
			nickname = identity.getFreetalkAddress();
		
		return nickname;
	}
	
	/**
	 * This class provides an in-memory cache of all {@link WoTTrust} and {@link WoTScore} values of WOT.
	 * This significantly speeds up queries which are needed by the web interface.
	 */
	private final class WebOfTrustCache implements NewIdentityCallback, IdentityDeletedCallback {
		
		private final class TrustKey {
			public final String mTrusterID;
			public final String mTrusteeID;
			
			public TrustKey(final WoTTrust trust) {
				mTrusterID = trust.getTruster().getID();
				mTrusteeID = trust.getTrustee().getID();
			}
			
			public TrustKey(final String trusterID, final String trusteeID) {
				mTrusterID = trusterID;
				mTrusteeID = trusteeID;
			}
				
			
			@Override
			public boolean equals(final Object o) {
				final TrustKey other = (TrustKey)o;
				return mTrusterID.equals(other.mTrusterID) && mTrusteeID.equals(other.mTrusteeID);
			}
			
			@Override
			public int hashCode() {
				return mTrusterID.hashCode() ^ mTrusteeID.hashCode();
			}
		}
		
		private final class TrustCount {
			public int mPositiveTrusts = 0;
			public int mNegativeTrusts = 0;
			
			public int get(int selection) {
				if(selection > 0)
					return mPositiveTrusts;
				else if(selection < 0)
					return mNegativeTrusts;
				
				throw new RuntimeException("0 is included in positive selection");
			}
			
			public void remove(int trustValue) {
				if(trustValue >= 0)
					--mPositiveTrusts;
				else
					--mNegativeTrusts;
			}
			
			public void add(int trustValue) {
				if(trustValue >= 0)
					++mPositiveTrusts;
				else
					++mNegativeTrusts;
			}
		}
		
	
		/**
		 * For each <Truster,Trustee>-key the trust value is stored
		 */
		private final HashMap<TrustKey, Byte> mTrustCache = new HashMap<TrustKey, Byte>();
		
		/**
		 * For each Truster-ID-key the given trust count is stored
		 */
		private final HashMap<String, TrustCount> mGivenTrustCountCache = new HashMap<String, TrustCount>();
		
		/**
		 * For each Trustee-ID the received trust count is stored
		 */
		private final HashMap<String, TrustCount> mReceivedTrustCountCache = new HashMap<String, TrustCount>();
		
		/**
		 * For each <Truster,Trustee>-key the score value is stored
		 */
		private final HashMap<TrustKey, Integer> mScoreCache = new HashMap<TrustKey, Integer>();
		
		/**
		 * For each Trustee-ID the received score count is stored
		 */
		private final HashMap<String, TrustCount> mReceivedScoreCountCache = new HashMap<String, TrustCount>();

		
		protected WebOfTrustCache(WoTIdentityManager mIdentityManager) {
			mIdentityManager.registerNewIdentityCallback(this, true);
			mIdentityManager.registerIdentityDeletedCallback(this, true);
		}
		
		@Override
		public void onNewIdentityAdded(Identity identity) {
			final String id = identity.getID();
			mGivenTrustCountCache.put(id, new TrustCount());
			mReceivedTrustCountCache.put(id, new TrustCount());
			mReceivedScoreCountCache.put(id, new TrustCount());
		}
		
		@Override
		public void beforeIdentityDeletion(Identity identity) {
			final String id = identity.getID();
			mGivenTrustCountCache.remove(id);
			mReceivedTrustCountCache.remove(id);
			mReceivedScoreCountCache.remove(id);
			
		}
		
		// TODO: Create getter methods in WoTIdentityManager which actually use this caching function...
		public byte getTrust(final String trusterID, final String trusteeID) throws NotTrustedException {
			assert(identityExists(trusterID));
			assert(identityExists(trusteeID));
			
			final Byte value = mTrustCache.get(new TrustKey(trusterID, trusteeID));
			if(value == null) throw new NotTrustedException(trusterID, trusteeID);
			return value;
		}
		
		public String getTrustComment(final String trusterID, final String trusteeID) {
			throw new UnsupportedOperationException("Not implemented yet");
			// TODO: Implement in putTrust
			
//			assert(identityExists(trusterID));
//			assert(identityExists(trusteeID));
		}
		
		public int getGivenTrustCount(final String trusterID, int selection) {
			assert(identityExists(trusterID));
			return mReceivedTrustCountCache.get(trusterID).get(selection);
		}

		
		public int getReceivedTrustCount(final String trusteeID, int selection) {
			assert(identityExists(trusteeID));
			
			return mGivenTrustCountCache.get(trusteeID).get(selection);
		}

		public int getScore(final String trusterID, final String trusteeID) throws NotInTrustTreeException {
			assert(ownIdentityExists(trusterID));
			assert(identityExists(trusteeID));
			
			final Integer value = mScoreCache.get(new TrustKey(trusterID, trusteeID));
			if(value == null) throw new NotInTrustTreeException(trusterID, trusteeID);
			return value;
		}
		
		public synchronized void putTrust(final String trusterID, final String trusteeID, final byte value, String comment) {
			assert(identityExists(trusterID));
			assert(identityExists(trusteeID));
			
			final Byte oldTrust = mTrustCache.get(new TrustKey(trusterID, trusteeID));
			
			if(oldTrust == null || Integer.signum(oldTrust) != Integer.signum(value)) {
				final TrustCount trusterGivenCount = mGivenTrustCountCache.get(trusterID);
				final TrustCount trusteeReceivedTrustCount = mReceivedTrustCountCache.get(trusteeID);	
				
				if(oldTrust != null) {
					trusterGivenCount.remove(oldTrust);
					trusteeReceivedTrustCount.remove(oldTrust);
				}
				
				trusterGivenCount.add(value);
				trusteeReceivedTrustCount.add(value);
			}
			
			mTrustCache.put(new TrustKey(trusterID, trusteeID), value);
		}
		
		public synchronized void putScore(final String trusterID, final String trusteeID, final int value) {
			assert(ownIdentityExists(trusterID));
			assert(identityExists(trusteeID));
			
			final Integer oldScore = mScoreCache.get(new TrustKey(trusterID, trusteeID));
			
			if(oldScore == null || Integer.signum(oldScore) != Integer.signum(value)) {
				final TrustCount trusteeReceivedScoreCount = mReceivedScoreCountCache.get(trusteeID);	
				
				if(oldScore != null) {
					trusteeReceivedScoreCount.remove(oldScore);
				}
				
				trusteeReceivedScoreCount.add(value);
			}
			
			mScoreCache.put(new TrustKey(trusterID, trusteeID), value);
		}
		
		protected synchronized void clearTrustValues() {
			// No need to create a new table, we do not expect the WoT to suddenly shrink
			mTrustCache.clear();
		}
		
		protected synchronized void clearScoreValues() {
			// No need to create a new table, we do not expect the WoT to suddenly shrink
			mScoreCache.clear();
		}

	}

}
