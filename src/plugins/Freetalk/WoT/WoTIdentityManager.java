/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageManager;
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
import freenet.pluginmanager.PluginNotFoundException;
import freenet.support.Base64;
import freenet.support.CurrentTimeUTC;
import freenet.support.Executor;
import freenet.support.IllegalBase64Exception;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.io.NativeThread;

/**
 * An identity manager which uses the identities from the WoT plugin.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class WoTIdentityManager extends IdentityManager {
	
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (30 * 1000) : (5 * 60 * 1000);
	
	/** The amount of time between each attempt to connect to the WoT plugin */
	private static final int WOT_RECONNECT_DELAY = 5 * 1000; 
	
	/** The minimal amount of time between fetching identities */
	private static final int MINIMAL_IDENTITY_FETCH_DELAY = 60 * 1000;
	
	/** The minimal amount of time between fetching own identities */
	private static final int MINIMAL_OWN_IDENTITY_FETCH_DELAY = 1000;
	
	private boolean mIdentityFetchInProgress = false;
	private boolean mOwnIdentityFetchInProgress = false;
	private long mLastIdentityFetchTime = 0;
	private long mLastOwnIdentityFetchTime = 0;
	
	/** If true, this identity manager is being use in a unit test - it will return 0 for any score / trust value then */
	private final boolean mIsUnitTest;

	private volatile boolean isRunning = false;
	private volatile boolean shutdownFinished = false;
	private Thread mThread = null;

	private PluginTalkerBlocking mTalker = null;


	/**
	 * Caches the shortest unique nickname for each identity. Key = Identity it, Value = Shortest nickname.
	 */
	private volatile Hashtable<String, String> mShortestUniqueNicknameCache = new Hashtable<String, String>();
	
	private boolean mShortestUniqueNicknameCacheNeedsUpdate = true;
	

	public WoTIdentityManager(Freetalk myFreetalk, Executor myExecutor) {
		super(myFreetalk, myExecutor);
		mIsUnitTest = false;
	}
	
	/**
	 * For being used in JUnit tests to run without a node.
	 */
	public WoTIdentityManager(Freetalk myFreetalk) {
		super(myFreetalk);
		mIsUnitTest = true;
	}
	
	
	/**
	 * Sends a blocking FCP message to the WoT plugin, checks whether the reply is really the expected reply message and throws an exception
	 * if not. Also checks whether the reply is an error message and throws an exception if it is. Therefore, the purpose of this function
	 * is that the callee can assume that no errors occurred if no exception is thrown.
	 * 
	 * @param params The params of the FCP message.
	 * @param expectedReplyMessage The excepted content of the "Message" field of the SimpleFieldSet of the reply message.
	 * @return The unmodified Result object which was returned by the PluginTalker.
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
	
	public synchronized WoTOwnIdentity createOwnIdentity(String newNickname, boolean publishesTrustList, boolean publishesIntroductionPuzzles) throws Exception  {
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
				newNickname);
		
		synchronized(db.lock()) {
			try {
				identity.initializeTransient(mFreetalk);
				identity.storeWithoutCommit();
				onNewOwnIdentityAdded(identity);
				db.commit(); Logger.debug(this, "COMMITED.");
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(db, this, e);
			}
		}
		
		return identity;
	}
	
	public synchronized WoTOwnIdentity createOwnIdentity(String newNickname, boolean publishesTrustList, boolean publishesIntroductionPuzzles,
			FreenetURI newRequestURI, FreenetURI newInsertURI) throws Exception {
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
				newNickname);
		
		synchronized(db.lock()) {
			try {
				identity.initializeTransient(mFreetalk);
				identity.storeWithoutCommit();
				onNewOwnIdentityAdded(identity);
				db.commit(); Logger.debug(this, "COMMITED.");
			}
			catch(RuntimeException e) {
				Persistent.checkedRollback(db, this, e);
			}
		}
		
		return identity;
	}
	
	public ObjectSet<WoTIdentity> getAllIdentities() {
		Query q = db.query();
		q.constrain(WoTIdentity.class);
		return new Persistent.InitializingObjectSet<WoTIdentity>(mFreetalk, q);
	}
	
	public synchronized ObjectSet<WoTOwnIdentity> ownIdentityIterator() {
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
	public synchronized WoTIdentity getIdentity(String id) throws NoSuchIdentityException {
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
	
	public FTIdentity getIdentityByURI(FreenetURI uri) throws NoSuchIdentityException {
		return getIdentity(WoTIdentity.getIDFromURI(uri));
	}
	
	@SuppressWarnings("unchecked")
	public synchronized WoTOwnIdentity getOwnIdentity(String id) throws NoSuchIdentityException {
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
	private String getProperty(FTOwnIdentity treeOwner, FTIdentity target, String property) throws Exception {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Message", "GetIdentity");
		sfs.putOverwrite("TreeOwner", treeOwner.getID());
		sfs.putOverwrite("Identity", target.getID());

		return sendFCPMessageBlocking(sfs, null, "Identity").params.get(property);
	}

	/**
	 * Not synchronized, the involved identities might be deleted during the query - which is not really a problem.
	 */
	public int getScore(WoTOwnIdentity treeOwner, WoTIdentity target) throws NotInTrustTreeException, Exception {
		if(mIsUnitTest)
			return 0;
		
		String score = getProperty(treeOwner, target, "Score");
		
		if(score.equals("null"))
			throw new NotInTrustTreeException(treeOwner, target);
		
		return Integer.parseInt(score);
	}

	/**
	 * Not synchronized, the involved identities might be deleted during the query - which is not really a problem.
	 */
	public byte getTrust(WoTOwnIdentity treeOwner, WoTIdentity target) throws NotTrustedException, Exception {
		if(mIsUnitTest)
			return 0;
		
		String trust = getProperty(treeOwner, target, "Trust");
		
		if(trust.equals("null"))
			throw new NotTrustedException(treeOwner, target);
		
		return Byte.parseByte(trust);
	}

	/**
	 * Not synchronized, the involved identities might be deleted during the query or some other WoT client might modify the trust value
	 * during the query - which is not really a problem, you should not be modifying trust values of your own identity with multiple clients simultaneously.
	 */
	public void setTrust(FTOwnIdentity treeOwner, FTIdentity identity, int trust, String comment) throws Exception {
		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "SetTrust");
		request.putOverwrite("Truster", treeOwner.getID());
		request.putOverwrite("Trustee", identity.getID());
		request.putOverwrite("Value", Integer.toString(trust));
		request.putOverwrite("Comment", comment);

		sendFCPMessageBlocking(request, null, "TrustSet");
	}

	/**
	 * Not synchronized, the involved identity might be deleted during the query - which is not really a problem.
	 */
	public List<WoTTrust> getReceivedTrusts(FTIdentity trustee) throws Exception {
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
					result.add(new WoTTrust(getIdentity(id), trustee, (byte)Integer.parseInt(answer.get("Value"+idx)), answer.get("Comment"+idx)));
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
	public int getReceivedTrustsCount(FTIdentity trustee) throws Exception {
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
	public int getReceivedTrustsCount(FTIdentity trustee, int selection) throws Exception {
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
	
	public int getGivenTrustsCount(FTIdentity trustee, int selection) throws Exception {
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
			Logger.debug(this, "Requesting identities with positive score from WoT ...");
			SimpleFieldSet p1 = new SimpleFieldSet(true);
			p1.putOverwrite("Message", "GetIdentitiesByScore");
			p1.putOverwrite("Selection", "+");
			p1.putOverwrite("Context", Freetalk.WOT_CONTEXT);
			parseIdentities(sendFCPMessageBlocking(p1, null, "Identities").params, false);
			
			synchronized(this) {
				// We must update the fetch-time after the parsing and only if the parsing succeeded:
				// If we updated before the parsing and parsing failed or took ages (the thread sometimes takes 2 hours to execute, don't ask me why)
				// then the garbage collector would delete identities.
				mLastIdentityFetchTime = CurrentTimeUTC.getInMillis();
			}
		}
		finally {
			synchronized(this) {
				mIdentityFetchInProgress = false;
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
			Logger.debug(this, "Requesting own identities from WoT ...");
			SimpleFieldSet p2 = new SimpleFieldSet(true);
			p2.putOverwrite("Message","GetOwnIdentities");
			parseIdentities(sendFCPMessageBlocking(p2, null, "OwnIdentities").params, true);
			
			synchronized(this) {
				// We must update the fetch-time after the parsing and only if the parsing succeeded:
				// If we updated before the parsing and parsing failed or took ages (the thread sometimes takes 2 hours to execute, don't ask me why)
				// then the garbage collector would delete identities.
				mLastOwnIdentityFetchTime = CurrentTimeUTC.getInMillis();
			}
		}
		finally {
			synchronized(this) {
				mOwnIdentityFetchInProgress = false;
			}
		}
		
		// We usually call garbageCollectIdentities() after calling this function, it updates the cache already...
		// if(mShortestUniqueNicknameCacheNeedsUpdate)
		//	updateShortestUniqueNicknameCache();
	}
	

	private void onNewIdentityAdded(FTIdentity identity) {
		mShortestUniqueNicknameCacheNeedsUpdate = true;
		
		doNewIdentityCallbacks(identity);
		
		if(!(identity instanceof FTOwnIdentity))
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
	private void onNewOwnIdentityAdded(FTOwnIdentity identity) {
		WoTOwnIdentity newIdentity = (WoTOwnIdentity)identity;
		
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
	
	private void beforeIdentityDeletion(FTIdentity identity) {
		doIdentityDeletedCallbacks(identity);
		
		if(!(identity instanceof FTOwnIdentity)) // Don't call it twice
			onShouldFetchStateChanged(identity, true, false);
	}
	
	private void beforeOwnIdentityDeletion(FTOwnIdentity identity) {
		doOwnIdentityDeletedCallbacks(identity);
		onShouldFetchStateChanged(identity, true, false);
	}
	
	private void onShouldFetchStateChanged(FTIdentity author, boolean oldShouldFetch, boolean newShouldFetch) {
		doShouldFetchStateChangedCallbacks(author, oldShouldFetch, newShouldFetch);
	}
	
	@SuppressWarnings("unchecked")
	private void parseIdentities(SimpleFieldSet params, boolean bOwnIdentities) {
		if(bOwnIdentities)
			Logger.debug(this, "Parsing received own identities...");
		else
			Logger.debug(this, "Parsing received identities...");
		
		final PersistentTaskManager taskManager = mFreetalk.getTaskManager();
	
		int idx;
		
		for(idx = 0; ; idx++) {
			String identityID = params.get("Identity"+idx);
			if(identityID == null || identityID.equals("")) /* TODO: Figure out whether the second condition is necessary */
				break;
			String requestURI = params.get("RequestURI"+idx);
			String insertURI = bOwnIdentities ? params.get("InsertURI"+idx) : null;
			String nickname = params.get("Nickname"+idx);
			
			if(nickname.length() == 0) {
				// If an identity publishes an invalid nickname in one of its first WoT inserts then WoT will return an empty
				// nickname for that identity until a new XML was published with a valid nickname. We ignore the identity until
				// then to prevent confusing error logs.
				// TODO: Maybe handle this in WoT. Would require checks in many places though.
				continue;
			}
			
			synchronized(this) { /* We lock here and not during the whole function to allow other threads to execute */
			synchronized(taskManager) {
				Query q = db.query();
				q.constrain(WoTIdentity.class);
				q.descend("mID").constrain(identityID);
				ObjectSet<WoTIdentity> result = q.execute();
				WoTIdentity id = null; 

				if(result.size() == 0) {
					synchronized(db.lock()) {
						try {
							Logger.debug(this, "Importing identity from WoT: " + requestURI);
							id = bOwnIdentities ?	new WoTOwnIdentity(identityID, new FreenetURI(requestURI), new FreenetURI(insertURI), nickname) :
								new WoTIdentity(identityID, new FreenetURI(requestURI), nickname);

							id.initializeTransient(mFreetalk);
							id.storeWithoutCommit();
							
							onNewIdentityAdded(id);
							
							if(bOwnIdentities)
								onNewOwnIdentityAdded((WoTOwnIdentity)id);
							
							db.commit(); Logger.debug(this, "COMMITED.");
						}
						catch(Exception e) {
							Persistent.checkedRollback(db, this, e);
						}
					}
				} else {
					Logger.debug(this, "Not importing already existing identity " + requestURI);
					assert(result.size() == 1);
					id = result.next();
					id.initializeTransient(mFreetalk);
					
					synchronized(id) {
					synchronized(db.lock()) {
						try {
							// TODO: The thread sometimes takes hours to parse the identities and I don't know why.
							// So right now its better to re-query the time for each identity.
							id.setLastReceivedFromWoT(CurrentTimeUTC.getInMillis());
							id.checkedCommit(this);
						}
						catch(RuntimeException e) {
							Persistent.checkedRollback(db, this, e);
						}
					}
					}
				}
			}
			}
			Thread.yield();
		}
		
		Logger.debug(this, "parseIdentities received " + idx + " identities. bOwnIdentities==" + bOwnIdentities);
	}
	
	@SuppressWarnings("unchecked")
	private void garbageCollectIdentities() {
		final MessageManager messageManager = mFreetalk.getMessageManager();
		final PersistentTaskManager taskManager = mFreetalk.getTaskManager();
		
		synchronized(this) {
			if(mIdentityFetchInProgress || mOwnIdentityFetchInProgress || mLastIdentityFetchTime == 0 || mLastOwnIdentityFetchTime == 0)
				return;
			
		/* Executing the thread loop once will always take longer than THREAD_PERIOD. Therefore, if we set the limit to 3*THREAD_PERIOD,
		 * it will hit identities which were last received before more than 2*THREAD_LOOP, not exactly 3*THREAD_LOOP. */
		long lastAcceptTime = Math.min(mLastIdentityFetchTime, mLastOwnIdentityFetchTime) - THREAD_PERIOD * 3;
		lastAcceptTime = Math.max(lastAcceptTime, 0); // This is not really needed but a time less than 0 does not make sense.;
		
		Query q = db.query();
		q.constrain(WoTIdentity.class);
		q.descend("mLastReceivedFromWoT").constrain(lastAcceptTime).smaller();
		ObjectSet<WoTIdentity> result = q.execute();
		
		for(WoTIdentity identity : result) {
			identity.initializeTransient(mFreetalk);
			Logger.debug(this, "Garbage collecting identity " + identity);
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
		synchronized(db.lock()) {
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

	public void run() { 
		Logger.debug(this, "Identity manager started.");
		mThread = Thread.currentThread();
		isRunning = true;
		 
		long nextIdentityRequestTime = 0;
		
		Random random = mFreetalk.getPluginRespirator().getNode().fastWeakRandom;
		
		try {
			updateShortestUniqueNicknameCache();
		} catch(Exception e) {
			Logger.error(this, "Initializing shortest unique nickname cache failed", e);
		}
		
		try {
		while(isRunning) {
			Thread.interrupted();
			Logger.debug(this, "Identity manager loop running...");
			
			boolean connected = connectToWoT();

			long currentTime = System.currentTimeMillis();
			
			long sleepTime = connected ? (THREAD_PERIOD/2 + random.nextInt(THREAD_PERIOD)) : WOT_RECONNECT_DELAY;
			
			if(connected && currentTime >= nextIdentityRequestTime) {
				try {
					fetchIdentities();
					fetchOwnIdentities();
					garbageCollectIdentities();
				} catch (Exception e) {
					Logger.error(this, "Fetching identities failed.", e);
				}
				
				nextIdentityRequestTime = currentTime + sleepTime;
			}
			
			Logger.debug(this, "Identity manager loop finished. Sleeping for " + (sleepTime / (60*1000)) + " minutes.");

			try {
				synchronized(this) {
					// sleep/interrupt protocol seems unreliable...
					wait(sleepTime);
				}
			}
			catch (InterruptedException e)
			{
				Logger.debug(this, "Identity manager loop interrupted. isRunning="+isRunning);
			}
		}
		}
		
		finally {
			synchronized (this) {
				shutdownFinished = true;
				Logger.debug(this, "Identity manager thread exiting.");
				notify();
			}
		}
	}
	
	public int getPriority() {
		return NativeThread.MIN_PRIORITY;
	}
	
	public void terminate() {
		Logger.debug(this, "Stopping ...");
		isRunning = false;
		synchronized(this) {
			while(!shutdownFinished) {
				notifyAll();
				try {
					wait();
				}
				catch (InterruptedException e) {
					Thread.interrupted();
				}
			}
		}
		Logger.debug(this, "Stopped.");
	}

	
	// TODO: This function should be a feature of WoT.
	@SuppressWarnings("unchecked")
	private synchronized void updateShortestUniqueNicknameCache() {
		Logger.debug(this, "Updating shortest unique nickname cache...");
		
		// We don't use getAllIdentities() because we do not need to have intializeTransient() called on each identity, we only query strings anyway.
		final Query q = db.query();
		q.constrain(WoTIdentity.class);
		ObjectSet<WoTIdentity> result = q.execute();
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
				
				while((firstDuplicate-1) > 0 && nicknames[firstDuplicate-1].equalsIgnoreCase(nicknames[i])) {
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
		
		final Hashtable<String,String> newCache = new Hashtable<String, String>(identities.length * 2);
		
		for(int i = 0; i < identities.length; ++i)
			newCache.put(identities[i].getID(), nicknames[i]);
		
		mShortestUniqueNicknameCache = newCache;
		mShortestUniqueNicknameCacheNeedsUpdate = false;
		
		Logger.debug(this, "Finished updating shortest unique nickname cache.");
	}

	@Override
	public String getShortestUniqueName(FTIdentity identity) {
		// We must not synchronize anything according to the specification of this function (to prevent deadlocks)
		String nickname = mShortestUniqueNicknameCache.get(identity.getID());
		
		if(nickname == null)
			nickname = identity.getFreetalkAddress();
		
		return nickname;
	}

}
