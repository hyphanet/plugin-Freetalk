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

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.PluginTalkerBlocking;
import plugins.Freetalk.exceptions.DuplicateIdentityException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
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
import freenet.pluginmanager.PluginNotFoundException;
import freenet.support.Base64;
import freenet.support.CurrentTimeUTC;
import freenet.support.Executor;
import freenet.support.IllegalBase64Exception;
import freenet.support.LRUCache;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.TrivialTicker;
import freenet.support.api.Bucket;
import freenet.support.io.NativeThread;

/**
 * An identity manager which uses the identities from the WoT plugin.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class WoTIdentityManager extends IdentityManager implements PrioRunnable {
	
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (3 * 60 * 1000) : (5 * 60 * 1000);
	
	/** The amount of time between each attempt to connect to the WoT plugin */
	private static final int WOT_RECONNECT_DELAY = 5 * 1000; 
	
	/** The minimal amount of time between fetching identities */
	private static final int MINIMAL_IDENTITY_FETCH_DELAY = 60 * 1000;
	
	/** The minimal amount of time between fetching own identities */
	private static final int MINIMAL_OWN_IDENTITY_FETCH_DELAY = 1000;
	
	private boolean mConnectedToWoT = false;
	
	private boolean mIdentityFetchInProgress = false;
	private boolean mOwnIdentityFetchInProgress = false;
	private long mLastIdentityFetchTime = 0;
	private long mLastOwnIdentityFetchTime = 0;
	
	private long mLastIdentityFetchID = 0;
	private long mLastOwnIdentityFetchID = 0;
	
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
	
	private WebOfTrustCache mWoTCache = new WebOfTrustCache();
	
	
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
	}
	
	/**
	 * For being used in JUnit tests to run without a node.
	 */
	public WoTIdentityManager(Freetalk myFreetalk) {
		super(myFreetalk);
		mIsUnitTest = true;
		mTicker = null;
		mRandom = null;
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

	@Override public synchronized WoTOwnIdentity createOwnIdentity(String newNickname,
			boolean publishesTrustList, boolean publishesIntroductionPuzzles,
			boolean autoSubscribeToNewBoards, boolean displayImages) throws Exception  {
		
		Logger.normal(this, "Creating new own identity via FCP, nickname: " + newNickname);
		
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "CreateIdentity");
		params.putOverwrite("Nickname", newNickname);
		params.putOverwrite("PublishTrustList", publishesTrustList ? "true" : "false");
		params.putOverwrite("PublishIntroductionPuzzles", publishesIntroductionPuzzles ? "true" : "false");
		params.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		PluginTalkerBlocking.Result result = sendFCPMessageBlocking(params, null, "IdentityCreated");
		
		WoTOwnIdentity identity = new WoTOwnIdentity(result.params.get("ID"),
				new FreenetURI(result.params.get("RequestURI")),
				new FreenetURI(result.params.get("InsertURI")),
				newNickname, autoSubscribeToNewBoards, displayImages);
		
		identity.initializeTransient(mFreetalk);
		identity.setLastReceivedFromWoT(mLastOwnIdentityFetchID);
		
		Logger.normal(this, "Created WoTOwnidentity via FCP, now storing... " + identity);
		
		synchronized(mFreetalk.getTaskManager()) { // Required by onNewOwnidentityAdded
		synchronized(Persistent.transactionLock(db)) {
			try {
				identity.initializeTransient(mFreetalk);
				identity.storeWithoutCommit();
				onNewOwnIdentityAdded(identity);
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

	@Override public synchronized WoTOwnIdentity createOwnIdentity(String newNickname,
			boolean publishesTrustList, boolean publishesIntroductionPuzzles,
			boolean autoSubscribeToNewBoards, boolean displayImages, FreenetURI newRequestURI,
			FreenetURI newInsertURI) throws Exception {
		
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
		
		/* We take the URIs which were returned by the WoT plugin instead of the requested ones because this allows the identity to work
		 * even if the WoT plugin ignores our requested URIs: If we just stored the URIs we requested, we would store an identity with
		 * wrong URIs which would result in the identity not being useable. */
		WoTOwnIdentity identity = new WoTOwnIdentity(result.params.get("ID"),
				new FreenetURI(result.params.get("RequestURI")),
				new FreenetURI(result.params.get("InsertURI")),
				newNickname, autoSubscribeToNewBoards, displayImages);
		
		identity.initializeTransient(mFreetalk);
		identity.setLastReceivedFromWoT(mLastOwnIdentityFetchID);
		
		Logger.normal(this, "Created WoTOwnidentity via FCP, now storing... " + identity);
		
		synchronized(mFreetalk.getTaskManager()) {
		synchronized(Persistent.transactionLock(db)) {
			try {
				identity.storeWithoutCommit();
				onNewOwnIdentityAdded(identity);
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

	@Override public ObjectSet<WoTIdentity> getAllIdentities() {
		Query q = db.query();
		q.constrain(WoTIdentity.class);
		return new Persistent.InitializingObjectSet<WoTIdentity>(mFreetalk, q);
	}

	@Override public synchronized ObjectSet<WoTOwnIdentity> ownIdentityIterator() {
		try {
			fetchOwnIdentities();
			garbageCollectIdentities();
		} 
		catch(Exception e) {} /* Ignore, return the ones which are in database now */
		
		final Query q = db.query();
		q.constrain(WoTOwnIdentity.class);
		return new Persistent.InitializingObjectSet<WoTOwnIdentity>(mFreetalk, q);

	}

	@SuppressWarnings("unchecked")
	@Override public synchronized WoTIdentity getIdentity(String id)
			throws NoSuchIdentityException {
		
		Query q = db.query();
		q.constrain(WoTIdentity.class);
		q.descend("mID").constrain(id);
		ObjectSet<WoTIdentity> result = q.execute();
		
		switch(result.size()) {
			case 1:
				WoTIdentity identity = result.next();
				identity.initializeTransient(mFreetalk);
				return identity;
			case 0:
				throw new NoSuchIdentityException(id);
			default:
				throw new DuplicateIdentityException(id);
		}
	}
	
	public Identity getIdentityByURI(FreenetURI uri) throws NoSuchIdentityException {
		return getIdentity(WoTIdentity.getIDFromURI(uri));
	}

	@SuppressWarnings("unchecked")
	@Override public synchronized WoTOwnIdentity getOwnIdentity(String id)
			throws NoSuchIdentityException {
		
		Query q = db.query();
		q.constrain(WoTOwnIdentity.class);
		q.descend("mID").constrain(id);
		ObjectSet<WoTOwnIdentity> result = q.execute();
		
		switch(result.size()) {
			case 1:
				WoTOwnIdentity identity = result.next();
				identity.initializeTransient(mFreetalk);
				return identity;
			case 0:
				throw new NoSuchIdentityException(id);
			default:
				throw new DuplicateIdentityException(id);
		}
	}

	/**
	 * Not synchronized, the involved identities might be deleted during the query - which is not really a problem.
	 */
	private String getProperty(OwnIdentity truster, Identity target, String property) throws Exception {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Message", "GetIdentity");
		sfs.putOverwrite("Truster", truster.getID());
		sfs.putOverwrite("Identity", target.getID());

		return sendFCPMessageBlocking(sfs, null, "Identity").params.get(property);
	}

	/**
	 * Not synchronized, the involved identities might be deleted during the query - which is not really a problem.
	 */
	public int getScore(final WoTOwnIdentity truster, final WoTIdentity trustee) throws NotInTrustTreeException, Exception {
		if(mIsUnitTest)
			return 0;
		
		final String score = getProperty(truster, trustee, "Score");
		
		if(score.equals("null"))
			throw new NotInTrustTreeException(truster, trustee);
		
		final int value = Integer.parseInt(score);
		mWoTCache.putScore(truster, trustee, value);
		return value;
	}

	/**
	 * Not synchronized, the involved identities might be deleted during the query - which is not really a problem.
	 */
	public byte getTrust(final WoTOwnIdentity truster, final WoTIdentity trustee) throws NotTrustedException, Exception {
		if(mIsUnitTest)
			return 0;
		
		final String trust = getProperty(truster, trustee, "Trust");
		
		if(trust.equals("null"))
			throw new NotTrustedException(truster, trustee);
		
		final byte value = Byte.parseByte(trust);
		mWoTCache.putTrust(truster, trustee, value);
		return value;
	}

	/**
	 * Not synchronized, the involved identities might be deleted during the query or some other WoT client might modify the trust value
	 * during the query - which is not really a problem, you should not be modifying trust values of your own identity with multiple clients simultaneously.
	 */
	public void setTrust(OwnIdentity truster, Identity trustee, byte trust, String comment) throws Exception {
		WoTOwnIdentity wotTruster = (WoTOwnIdentity)truster;
		WoTIdentity wotTrustee = (WoTIdentity)trustee;
		
		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "SetTrust");
		request.putOverwrite("Truster", wotTruster.getID());
		request.putOverwrite("Trustee", wotTrustee.getID());
		request.putOverwrite("Value", Integer.toString(trust));
		request.putOverwrite("Comment", comment);

		sendFCPMessageBlocking(request, null, "TrustSet");
		mWoTCache.putTrust(wotTruster, wotTrustee, trust);
	}

	/**
	 * Not synchronized, the involved identity might be deleted during the query - which is not really a problem.
	 */
	public List<WoTTrust> getReceivedTrusts(Identity trustee) throws Exception {
		List<WoTTrust> result = new ArrayList<WoTTrust>();

		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "GetTrusters");
		request.putOverwrite("Context", "");
		request.putOverwrite("Identity", trustee.getID());
		try {
			SimpleFieldSet answer = sendFCPMessageBlocking(request, null, "Identities").params;
			for(int idx = 0; ; idx++) {
				String id = answer.get("Identity"+idx);
				if(id == null || id.equals("")) /* TODO: Figure out whether the second condition is necessary */
					break;
				try {
					final WoTTrust trust = new WoTTrust(getIdentity(id), trustee, (byte)Integer.parseInt(answer.get("Value"+idx)), 
							answer.get("Comment"+idx));
					mWoTCache.putTrust(trust);
					result.add(trust);
				} catch (NoSuchIdentityException e) {
				} catch (InvalidParameterException e) {
				}
			}
		}
		catch(PluginNotFoundException e) {
			throw new WoTDisconnectedException();
		}
		return result;
	}
	
	/**
	 * Not synchronized, the involved identity might be deleted during the query - which is not really a problem.
	 */
	public int getReceivedTrustsCount(Identity trustee) throws Exception {
		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "GetTrustersCount");
		request.putOverwrite("Identity", trustee.getID());
		request.putOverwrite("Context", Freetalk.WOT_CONTEXT);		
		
		try {
			SimpleFieldSet answer = sendFCPMessageBlocking(request, null, "TrustersCount").params;
			return Integer.parseInt(answer.get("Value"));
		}
		catch(PluginNotFoundException e) {
			throw new WoTDisconnectedException();
		}

	}
	
	/**
	 * Get the number of trust values for a given identity with the ability to select which values should be counted.
	 * 
	 * Not synchronized, the involved identity might be deleted during the query - which is not really a problem.
	 * 
	 * @param selection Use 1 for counting trust values greater than or equal to zero, 0 for counting trust values exactly equal to 0 and -1 for counting trust
	 * 		values less than zero.
	 */
	public int getReceivedTrustsCount(Identity trustee, int selection) throws Exception {
		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "GetTrustersCount");
		request.putOverwrite("Identity", trustee.getID());
		request.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		
		if(selection > 0)
			request.putOverwrite("Selection", "+");
		else if(selection == 0)
			request.putOverwrite("Selection", "0");
		else
			request.putOverwrite("Selection", "-");
		
		try {
			SimpleFieldSet answer = sendFCPMessageBlocking(request, null, "TrustersCount").params;
			return Integer.parseInt(answer.get("Value"));
		}
		catch(PluginNotFoundException e) {
			throw new WoTDisconnectedException();
		}
	}
	
	public int getGivenTrustsCount(Identity trustee, int selection) throws Exception {
		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "GetTrusteesCount");
		request.putOverwrite("Identity", trustee.getID());
		request.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		
		if(selection > 0)
			request.putOverwrite("Selection", "+");
		else if(selection == 0)
			request.putOverwrite("Selection", "0");
		else
			request.putOverwrite("Selection", "-");
		
		try {
			SimpleFieldSet answer = sendFCPMessageBlocking(request, null, "TrusteesCount").params;
			return Integer.parseInt(answer.get("Value"));
		}
		catch(PluginNotFoundException e) {
			throw new WoTDisconnectedException();
		}
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
	
	/**
	 * Fetches the identities with positive score from WoT and stores them in the database.
	 * @throws Exception 
	 */
	private void fetchIdentities() throws Exception {		
		// parseIdentities() acquires and frees the WoTIdentityManager-lock for each identity to allow other threads to access the identity manager while the
		// parsing is in progress. Therefore, we do not take the lock for the whole execution of this function.
		synchronized(this) {
			if(mIdentityFetchInProgress)
				return;
			
			long now = CurrentTimeUTC.getInMillis();
			if((now - mLastIdentityFetchTime) < MINIMAL_IDENTITY_FETCH_DELAY)
				return;
			
			mIdentityFetchInProgress = true;
		}
		
		try {
			Logger.normal(this, "Requesting identities with positive score from WoT ...");
			SimpleFieldSet p1 = new SimpleFieldSet(true);
			p1.putOverwrite("Message", "GetIdentitiesByScore");
			p1.putOverwrite("Selection", "+");
			p1.putOverwrite("Context", Freetalk.WOT_CONTEXT);
			parseIdentities(sendFCPMessageBlocking(p1, null, "Identities").params, false);
		}
		finally {
			synchronized(this) {
				mIdentityFetchInProgress = false;
				// Disable garbage collection for the next iteration since importing failed
				mLastIdentityFetchID = 0;
			}
		}
	
			
		// We usually call garbageCollectIdentities() after calling this function, it updates the cache already...
		// if(mShortestUniqueNicknameCacheNeedsUpdate)
		//	updateShortestUniqueNicknameCache();
	}
	
	/**
	 * Fetches the own identities with positive score from WoT and stores them in the database.
	 * @throws Exception 
	 */
	private void fetchOwnIdentities() throws Exception {
		// parseIdentities() acquires and frees the WoTIdentityManager-lock for each identity to allow other threads to access the identity manager while the
		// parsing is in progress. Therefore, we do not take the lock for the whole execution of this function.
		synchronized(this) {
			if(mOwnIdentityFetchInProgress)
				return;

			long now = CurrentTimeUTC.getInMillis();
			if((now - mLastOwnIdentityFetchTime) < MINIMAL_OWN_IDENTITY_FETCH_DELAY)
				return;
			
			mOwnIdentityFetchInProgress = true;
		}
		
		try {
			Logger.normal(this, "Requesting own identities from WoT ...");
			SimpleFieldSet p2 = new SimpleFieldSet(true);
			p2.putOverwrite("Message","GetOwnIdentities");
			parseIdentities(sendFCPMessageBlocking(p2, null, "OwnIdentities").params, true);
		}
		finally {
			synchronized(this) {
				mOwnIdentityFetchInProgress = false;
				// Disable garbage collection for the next iteration since importing failed
				mLastOwnIdentityFetchID = 0;
			}
		}
		
		// We usually call garbageCollectIdentities() after calling this function, it updates the cache already...
		// if(mShortestUniqueNicknameCacheNeedsUpdate)
		//	updateShortestUniqueNicknameCache();
	}
	

	private void onNewIdentityAdded(Identity identity) {
		Logger.normal(this, "onNewIdentityAdded " + identity);
		
		mShortestUniqueNicknameCacheNeedsUpdate = true;
		
		doNewIdentityCallbacks(identity);
		
		if(!(identity instanceof OwnIdentity))
			onShouldFetchStateChanged(identity, false, true);
	}

	/**
	 * Called by this WoTIdentityManager after a new WoTIdentity has been stored to the database and before committing the transaction.
	 * 
	 * You have to lock this WoTIdentityManager, the PersistentTaskManager and the database before calling this function.
	 * 
	 * @param newIdentity
	 * @throws Exception If adding the Freetalk context to the identity in WoT failed.
	 */
	private void onNewOwnIdentityAdded(OwnIdentity identity) {
		Logger.normal(this, "onNewOwnIdentityAdded " + identity);
		
		WoTOwnIdentity newIdentity = (WoTOwnIdentity)identity;
		
		// TODO: Do after an own message is posted. I have not decided at which place to do this :|
		try {
			addFreetalkContext(newIdentity);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
			
		PersistentTask introductionTask = new IntroduceIdentityTask((WoTOwnIdentity)newIdentity);
		mFreetalk.getTaskManager().storeTaskWithoutCommit(introductionTask);
		
		doNewOwnIdentityCallbacks(newIdentity);
		onShouldFetchStateChanged(newIdentity, false, true);
	}
	
	private void beforeIdentityDeletion(Identity identity) {
		Logger.normal(this, "beforeIdentityDeletion " + identity);
		
		doIdentityDeletedCallbacks(identity);
		
		if(!(identity instanceof OwnIdentity)) // Don't call it twice
			onShouldFetchStateChanged(identity, true, false);
	}
	
	private void beforeOwnIdentityDeletion(OwnIdentity identity) {
		Logger.normal(this, "beforeOwnIdentityDeletion " + identity);
		
		doOwnIdentityDeletedCallbacks(identity);
		onShouldFetchStateChanged(identity, true, false);
	}
	
	private void onShouldFetchStateChanged(Identity author, boolean oldShouldFetch, boolean newShouldFetch) {
		Logger.normal(this, "onShouldFetchStateChanged " + author);
		doShouldFetchStateChangedCallbacks(author, oldShouldFetch, newShouldFetch);
	}
	
	private void importIdentity(boolean ownIdentity, String identityID, String requestURI, String insertURI, String nickname, long fetchID) {
		synchronized(mFreetalk.getTaskManager()) {
		synchronized(Persistent.transactionLock(db)) {
			try {
				Logger.normal(this, "Importing identity from WoT: " + requestURI);
				final WoTIdentity id = ownIdentity ? new WoTOwnIdentity(identityID, new FreenetURI(requestURI), new FreenetURI(insertURI), nickname) :
					new WoTIdentity(identityID, new FreenetURI(requestURI), nickname);

				id.initializeTransient(mFreetalk);
				id.setLastReceivedFromWoT(fetchID);
				id.storeWithoutCommit();

				onNewIdentityAdded(id);

				if(ownIdentity)
					onNewOwnIdentityAdded((WoTOwnIdentity)id);

				id.checkedCommit(this);
			}
			catch(Exception e) {
				Persistent.checkedRollbackAndThrow(db, this, new RuntimeException(e));
			}
		}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void parseIdentities(SimpleFieldSet params, boolean bOwnIdentities) {
		if(bOwnIdentities)
			Logger.normal(this, "Parsing received own identities...");
		else
			Logger.normal(this, "Parsing received identities...");
	
		int idx;
		int ignoredCount = 0;
		int newCount = 0;
		
		long fetchID = mRandom.nextLong();
		
		for(idx = 0; ; idx++) {
			String identityID = params.get("Identity"+idx);
			if(identityID == null || identityID.equals("")) /* TODO: Figure out whether the second condition is necessary */
				break;
			String requestURI = params.get("RequestURI"+idx);
			String insertURI = bOwnIdentities ? params.get("InsertURI"+idx) : null;
			String nickname = params.get("Nickname"+idx);
			
			if(nickname == null || nickname.length() == 0) {
				// If an identity publishes an invalid nickname in one of its first WoT inserts then WoT will return an empty
				// nickname for that identity until a new XML was published with a valid nickname. We ignore the identity until
				// then to prevent confusing error logs.
				// TODO: Maybe handle this in WoT. Would require checks in many places though.
				continue;
			}
			
			synchronized(this) { /* We lock here and not during the whole function to allow other threads to execute */
				Query q = db.query(); // TODO: Encapsulate the query in a function...
				q.constrain(WoTIdentity.class);
				q.descend("mID").constrain(identityID);
				ObjectSet<WoTIdentity> result = q.execute();
				WoTIdentity id = null; 
				
				if(result.size() == 0) {
					try {
						importIdentity(bOwnIdentities, identityID, requestURI, insertURI, nickname, fetchID);
						++newCount;
					}
					catch(Exception e) {
						Logger.error(this, "Importing a new identity failed.", e);
					}
				} else {
					if(logDEBUG) Logger.debug(this, "Not importing already existing identity " + requestURI);
					++ignoredCount;
					
					assert(result.size() == 1);
					id = result.next();
					id.initializeTransient(mFreetalk);
					
					if(bOwnIdentities != (id instanceof WoTOwnIdentity)) {
						// The type of the identity changed so we need to delete and re-import it.
						
						try {
							Logger.normal(this, "Identity type changed, replacing it: " + id);
							// We MUST NOT take the following locks because deleteIdentity does other locks (MessageManager/TaskManager) which must happen before...
							// synchronized(id)
							// synchronized(Persistent.transactionLock(db)) 
							deleteIdentity(id, mFreetalk.getMessageManager(), mFreetalk.getTaskManager());
							importIdentity(bOwnIdentities, identityID, requestURI, insertURI, nickname, fetchID);
						}
						catch(Exception e) {
							Logger.error(this, "Replacing a WoTIdentity with WoTOwnIdentity failed.", e);
						}
						
					} else { // Normal case: Update the last received time of the idefnt
						synchronized(id) {
						synchronized(Persistent.transactionLock(db)) {
							try {
								id.setLastReceivedFromWoT(fetchID);
								id.checkedCommit(this);
							}
							catch(Exception e) {
								Persistent.checkedRollback(db, this, e);
							}
						}
						}
					}
				}
			}
			
			Thread.yield();
		}
		
		try {
			final int expectedIdentityAmount = params.getInt("Amount");
			if(idx == expectedIdentityAmount) {
				// We must update the fetch-ID after the parsing and only if the parsing succeeded:
				// If we updated before the parsing and parsing failed then the garbage collector would delete identities.
				if(bOwnIdentities)
					mLastOwnIdentityFetchID = fetchID;
				else
					mLastIdentityFetchID = fetchID;
			} else { // Importing failed - we received a corrupted data set
				Logger.error(this, "Parsed identity count does not match expected amount: expected: " + expectedIdentityAmount + "; actual amount: " + idx);
				
				// Disable garbage collection for the next iteration since importing failed
				if(bOwnIdentities)
					mLastOwnIdentityFetchID = 0;
				else
					mLastIdentityFetchID = 0;
			}
		} catch(FSParseException e) {
			Logger.error(this, "GetIdentitiesByScore did not specify Amount of identities! Maybe WOT older than build0012?", e);
		}
		
		Logger.normal(this, "parseIdentities(bOwnIdentities==" + bOwnIdentities + " received " + idx + " identities. Ignored " + ignoredCount + "; New: " + newCount );
	}

	private void garbageCollectIdentities() {
		garbageCollectIdentities(true);
		garbageCollectIdentities(false);
	}
	
	/**
	 * Deletes all identities whose mLastReceivedFromWoT field does not match the ID of the last fetch (which is stored in mLast(Own)IdentityFetchID)
	 */
	@SuppressWarnings("unchecked")
	private void garbageCollectIdentities(boolean ownIdentities) {
		final MessageManager messageManager = mFreetalk.getMessageManager();
		final PersistentTaskManager taskManager = mFreetalk.getTaskManager();
		
		synchronized(this) {
			// We must abort garbage collection if an identity fetch is in progress since the mLast(Own)IdentityFetchID which we use to delete
			// identities is updated AFTER the fetch has succeeded but the IDs which are stored in the identities are updated sequentially
			// in single transactions. So if we GC'ed while a fetch was in progress, we would delete lots of identities because their fetch ID 
			// would mismatch mLast(Own)IdentityFetchID
			if(mIdentityFetchInProgress || mOwnIdentityFetchInProgress)
				return;
			
			if((ownIdentities && mLastOwnIdentityFetchID == 0) || (!ownIdentities && mLastIdentityFetchID == 0))
				return;

			long acceptID = ownIdentities ? mLastOwnIdentityFetchID : mLastIdentityFetchID;

			Query q = db.query();
			if(ownIdentities)
				q.constrain(WoTOwnIdentity.class);
			else {
				q.constrain(WoTIdentity.class);
				q.constrain(WoTOwnIdentity.class).not();
			}
			
			q.descend("mLastReceivedFromWoT").constrain(acceptID).not();
			ObjectSet<WoTIdentity> result = q.execute();

			for(WoTIdentity identity : result) {
				identity.initializeTransient(mFreetalk);
				if(logDEBUG) Logger.debug(this, "Garbage collecting identity " + identity);
				deleteIdentity(identity, messageManager, taskManager);
			}

			if(mShortestUniqueNicknameCacheNeedsUpdate)
				updateShortestUniqueNicknameCache();
		}
	}
	
	private synchronized void deleteIdentity(WoTIdentity identity, MessageManager messageManager, PersistentTaskManager taskManager) {
		identity.initializeTransient(mFreetalk);
		
		beforeIdentityDeletion(identity);

		if(identity instanceof WoTOwnIdentity)
 			beforeOwnIdentityDeletion((WoTOwnIdentity)identity);
		
		synchronized(identity) {
		synchronized(Persistent.transactionLock(db)) {
			try {
				identity.deleteWithoutCommit();
				
				Logger.normal(this, "Identity deleted: " + identity);
				identity.checkedCommit(this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
		}
		
		mShortestUniqueNicknameCacheNeedsUpdate = true;
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

	@Override public void run() { 
		if(logDEBUG) Logger.debug(this, "Main loop running...");
		 
		try {
			mConnectedToWoT = connectToWoT();
		
			if(mConnectedToWoT) {
				try {
					fetchOwnIdentities(); // Fetch the own identities first to prevent own-identities from being imported as normal identity...
					fetchIdentities();
					garbageCollectIdentities();
				} catch (Exception e) {
					Logger.error(this, "Fetching identities failed.", e);
				}
			}
		} finally {
			final long sleepTime =  mConnectedToWoT ? (THREAD_PERIOD/2 + mRandom.nextInt(THREAD_PERIOD)) : WOT_RECONNECT_DELAY;
			if(logDEBUG) Logger.debug(this, "Sleeping for " + (sleepTime / (60*1000)) + " minutes.");
			mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), sleepTime, false, true);
		}
		
		if(logDEBUG) Logger.debug(this, "Main loop finished.");
	}

	@Override public int getPriority() {
		return NativeThread.MIN_PRIORITY;
	}

	@Override public void start() {
		if(logDEBUG) Logger.debug(this, "Starting...");
		
		if(logDEBUG)
			deleteDuplicateIdentities();
		
		mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), 0, false, true);
		
		// TODO: Queue this as a job aswell.
		try {
			updateShortestUniqueNicknameCache();
		} catch(Exception e) {
			Logger.error(this, "Initializing shortest unique nickname cache failed", e);
		}
		
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
							deleteIdentity(duplicate, messageManager, taskManager);
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

	@Override public void terminate() {
		if(logDEBUG) Logger.debug(this, "Terminating ...");
		mTicker.shutdown();
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

			@Override public int compare(WoTIdentity i1, WoTIdentity i2) {
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
	
	private final class WebOfTrustCache {
		public static final long EXPIRATION_DELAY = 5 * 60 * 1000;
		
		private final class TrustKey implements Comparable<TrustKey> {
			public final String mTrusterID;
			public final String mTrusteeID;
			
			public TrustKey(final WoTTrust trust) {
				mTrusterID = trust.getTruster().getID();
				mTrusteeID = trust.getTrustee().getID();
			}
			
			public TrustKey(final WoTIdentity truster, final WoTIdentity trustee) {
				mTrusterID = truster.getID();
				mTrusteeID = trustee.getID();
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

			@Override
			public int compareTo(TrustKey o) {
				int cmp = mTrusterID.compareTo(o.mTrusterID);
				if(cmp != 0) return cmp;
				return mTrusteeID.compareTo(o.mTrusteeID);
			}
		}
		
		private final LRUCache<TrustKey, Byte> mTrustCache = new LRUCache<TrustKey, Byte>(256, EXPIRATION_DELAY);
		private final LRUCache<TrustKey, Integer> mScoreCache = new LRUCache<TrustKey, Integer>(256, EXPIRATION_DELAY);
		
		// TODO: Create getter methods in WoTIdentityManager which actually use this caching function...
		public synchronized byte getTrust(final WoTOwnIdentity truster, final WoTIdentity trustee) throws NotTrustedException, Exception {
			{
				final Byte cachedValue = mTrustCache.get(new TrustKey(truster, trustee));
				if(cachedValue != null)
					return cachedValue;
			}
			
			return WoTIdentityManager.this.getTrust(truster, trustee);	// This will update the cache
		}

		// TODO: Create getter methods in WoTIdentityManager which actually use this caching function...
		public synchronized int getScore(final WoTOwnIdentity truster, final WoTIdentity trustee) throws NotInTrustTreeException, Exception {
			{
				final Integer cachedValue = mScoreCache.get(new TrustKey(truster, trustee));
				if(cachedValue != null)
					return cachedValue;
			}
			
			return WoTIdentityManager.this.getScore(truster, trustee);	// This will update the cache
		}
		
		public synchronized void putTrust(final WoTIdentity truster, final WoTIdentity trustee, final byte value) {
			mTrustCache.put(new TrustKey(truster, trustee), value);
		}
		
		public synchronized void putTrust(final WoTTrust trust) {
			mTrustCache.put(new TrustKey(trust), trust.getValue());
		}
		
		public synchronized void putScore(final WoTOwnIdentity truster, final WoTIdentity trustee, final int value) {
			mScoreCache.put(new TrustKey(truster, trustee), value);
		}

	}

}
