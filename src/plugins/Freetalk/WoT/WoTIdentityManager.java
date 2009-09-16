/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import plugins.Freetalk.DBUtil;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageManager;
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
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.support.Base64;
import freenet.support.CurrentTimeUTC;
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
public class WoTIdentityManager extends IdentityManager {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 5 * 60 * 1000;
	
	/** The amount of time between each attempt to connect to the WoT plugin */
	private static final int WOT_RECONNECT_DELAY = 5 * 1000; 
	
	/** The minimal amount of time between fetching identities */
	private static final int MINIMAL_IDENTITY_FETCH_DELAY = 1000;
	
	/** The minimal amount of time between fetching own identities */
	private static final int MINIMAL_OWN_IDENTITY_FETCH_DELAY = 1000;
	
	private long mLastIdentityFetchTime = 0;
	private long mLastOwnIdentityFetchTime = 0;
	
	/** If true, this identity manager is being use in a unit test - it will return 0 for any score / trust value then */
	private final boolean mIsUnitTest;
	
	private final Freetalk mFreetalk;

	private volatile boolean isRunning = false;
	private volatile boolean shutdownFinished = false;
	private Thread mThread = null;

	private PluginTalkerBlocking mTalker = null;

	public WoTIdentityManager(ExtObjectContainer myDB, Freetalk myFreetalk) {
		super(myDB, myFreetalk.getPluginRespirator().getNode().executor);
		mIsUnitTest = false;
		
		mFreetalk = myFreetalk;
		
		isRunning = true;
		
		// FIXME: You should avoid calling methods in constructors that might lead to the object 
		// being registered and then called back to before the fields have been written.
		mExecutor.execute(this, "FT Identity Manager");
	}
	
	/**
	 * For being used in JUnit tests to run without a node.
	 */
	WoTIdentityManager(ExtObjectContainer myDB) {
		super(myDB);
		mIsUnitTest = true;
		mFreetalk = null;
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
		
		if(result.params.get("Message").equals("Error"))
			throw new Exception("FCP message " + result.params.get("OriginalMessage") + " failed: " + result.params.get("Description"));
		
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
				identity.storeWithoutCommit();
				db.commit(); Logger.debug(this, "COMMITED.");
			}
			catch(RuntimeException e) {
				db.rollback();
				Logger.error(this, "ROLLED BACK: Error while creating OwnIdentity", e);
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
		params.putOverwrite("PublishIntroductionPuzzles", publishesIntroductionPuzzles ? "true" : "false");
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
				identity.storeWithoutCommit();
				db.commit(); Logger.debug(this, "COMMITED.");
			}
			catch(RuntimeException e) {
				db.rollback();
				Logger.error(this, "ROLLED BACK: Error while creating OwnIdentity", e);
			}
		}
		
		return identity;
	}
	
	@SuppressWarnings("unchecked")
	public synchronized Iterable<WoTIdentity> getAllIdentities() {
		return new Iterable<WoTIdentity>() {
			public Iterator<WoTIdentity> iterator() {
				return new Iterator<WoTIdentity> () {
					Iterator<WoTIdentity> iter;

					{
						Query q = db.query();
						q.constrain(WoTIdentity.class);
						iter = q.execute().iterator();
					}

					public boolean hasNext() {
						return iter.hasNext();
					}

					public WoTIdentity next() {
						WoTIdentity i = iter.next();
						i.initializeTransient(db, WoTIdentityManager.this);
						return i;
					}

					public void remove() {
						throw new UnsupportedOperationException("Cannot delete identities.");
					}
				};
			}
		};
	}
	
	public synchronized Iterator<FTOwnIdentity> ownIdentityIterator() {
		try {
			fetchOwnIdentities();
			garbageCollectIdentities();
		} 
		catch(Exception e) {} /* Ignore, return the ones which are in database now */
		
		return super.ownIdentityIterator();
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
				identity.initializeTransient(db, this);
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
				identity.initializeTransient(db, this);
				return identity;
			case 0:
				throw new NoSuchIdentityException(id);
			default:
				throw new DuplicateIdentityException(id);
		}
	}

	protected synchronized String getProperty(FTOwnIdentity treeOwner, FTIdentity target, String property) throws Exception {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Message", "GetIdentity");
		sfs.putOverwrite("TreeOwner", treeOwner.getID());
		sfs.putOverwrite("Identity", target.getID());

		return sendFCPMessageBlocking(sfs, null, "Identity").params.get(property);
	}

	public int getScore(WoTOwnIdentity treeOwner, WoTIdentity target) throws NotInTrustTreeException, Exception {
		if(mIsUnitTest)
			return 0;
		
		String score = getProperty(treeOwner, target, "Score");
		
		if(score.equals("null"))
			throw new NotInTrustTreeException(treeOwner, target);
		
		return Integer.parseInt(score);
	}

	public int getTrust(WoTOwnIdentity treeOwner, WoTIdentity target) throws NotTrustedException, Exception {
		if(mIsUnitTest)
			return 0;
		
		String trust = getProperty(treeOwner, target, "Trust");
		
		if(trust.equals("null"))
			throw new NotTrustedException(treeOwner, target);
		
		return Integer.parseInt(trust);
	}

	public synchronized void setTrust(FTOwnIdentity treeOwner, FTIdentity identity, int trust, String comment) throws Exception {
		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "SetTrust");
		request.putOverwrite("Truster", treeOwner.getID());
		request.putOverwrite("Trustee", identity.getID());
		request.putOverwrite("Value", Integer.toString(trust));
		request.putOverwrite("Comment", comment);

		sendFCPMessageBlocking(request, null, "TrustSet");
	}

	public synchronized List<WoTTrust> getReceivedTrusts(FTIdentity trustee) throws Exception {
		List<WoTTrust> result = new ArrayList<WoTTrust>();
		if(mTalker == null)
			throw new WoTDisconnectedException();

		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "GetTrusters");
		request.putOverwrite("Context", "");
		request.putOverwrite("Identity", trustee.getID());
		try {
			SimpleFieldSet answer = sendFCPMessageBlocking(request, null, "Identities").params;
			for(int idx = 1; ; idx++) {
				String id = answer.get("Identity"+idx);
				if(id == null || id.equals("")) /* FIXME: Figure out whether the second condition is necessary */
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
	
	public synchronized int getReceivedTrustsCount(FTIdentity trustee) throws Exception {
		if(mTalker == null)
			throw new WoTDisconnectedException();
		
		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "GetTrustersCount");
		request.putOverwrite("Identity", trustee.getID());
		request.putOverwrite("Context", "");
		
		try {
			SimpleFieldSet answer = sendFCPMessageBlocking(request, null, "TrustersCount").params;
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
		if(mTalker == null)
			throw new WoTDisconnectedException();
		
		ArrayList<String> puzzleIDs = new ArrayList<String>(amount + 1);
		
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "GetIntroductionPuzzles");
		params.putOverwrite("Identity", ownIdentity.getID());
		params.putOverwrite("Type", "Captcha"); // TODO: Don't hardcode the String
		params.put("Amount", amount);
		
		try {
			SimpleFieldSet result = sendFCPMessageBlocking(params, null, "IntroductionPuzzles").params;
			
			for(int idx = 1; ; idx++) {
				String id = result.get("Puzzle" + idx);
				
				if(id == null || id.equals("")) /* FIXME: Figure out whether the second condition is necessary */
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
		if(mTalker == null)
			throw new WoTDisconnectedException();
		
		
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
		if(mTalker == null)
			throw new WoTDisconnectedException();
		
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
	private synchronized void fetchIdentities() throws Exception {
		if(mTalker == null)
			throw new PluginNotFoundException();
		
		long now = CurrentTimeUTC.getInMillis();
		if((now - mLastIdentityFetchTime) < MINIMAL_IDENTITY_FETCH_DELAY)
			return;
		
		mLastIdentityFetchTime = now;
		
		Logger.debug(this, "Requesting identities with positive score from WoT ...");
		SimpleFieldSet p1 = new SimpleFieldSet(true);
		p1.putOverwrite("Message", "GetIdentitiesByScore");
		p1.putOverwrite("Selection", "+");
		p1.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		parseIdentities(sendFCPMessageBlocking(p1, null, "Identities").params, false);
	}
	
	/**
	 * Fetches the own identities with positive score from WoT and stores them in the database.
	 * @throws Exception 
	 */
	private synchronized void fetchOwnIdentities() throws Exception {
		long now = CurrentTimeUTC.getInMillis();
		if((now - mLastOwnIdentityFetchTime) < MINIMAL_OWN_IDENTITY_FETCH_DELAY)
			return;
		
		mLastOwnIdentityFetchTime = now;
		
		Logger.debug(this, "Requesting own identities from WoT ...");
		SimpleFieldSet p2 = new SimpleFieldSet(true);
		p2.putOverwrite("Message","GetOwnIdentities");
		parseIdentities(sendFCPMessageBlocking(p2, null, "OwnIdentities").params, true);
	}
	
	/**
	 * Called by this WoTIdentityManager after a new WoTIdentity has been stored to the database and before committing the transaction.
	 * 
	 * 
	 * You have to lock this WoTIdentityManager, the PersistentTaskManager and the database before calling this function.
	 * 
	 * @param newIdentity
	 * @throws Exception If adding the Freetalk context to the identity in WoT failed.
	 */
	private void onIdentityCreated(WoTIdentity newIdentity) throws Exception {
		if(newIdentity instanceof WoTOwnIdentity) {
			/* FIXME: Only add the context if the user actually uses the identity with Freetalk */
			addFreetalkContext(newIdentity);
			
			PersistentTask introductionTask = new IntroduceIdentityTask((WoTOwnIdentity)newIdentity);
			introductionTask.initializeTransient(db, mFreetalk);
			introductionTask.storeWithoutCommit();
		}
	}
	
	@SuppressWarnings("unchecked")
	private void parseIdentities(SimpleFieldSet params, boolean bOwnIdentities) {
		if(bOwnIdentities)
			Logger.debug(this, "Parsing received own identities...");
		else
			Logger.debug(this, "Parsing received identities...");
		
		long time = CurrentTimeUTC.getInMillis();
		
		final PersistentTaskManager taskManager = mFreetalk.getTaskManager();
	
		for(int idx = 1; ; idx++) {
			String identityID = params.get("Identity"+idx);
			if(identityID == null || identityID.equals("")) /* FIXME: Figure out whether the second condition is necessary */
				break;
			String requestURI = params.get("RequestURI"+idx);
			String insertURI = bOwnIdentities ? params.get("InsertURI"+idx) : null;
			String nickname = params.get("Nickname"+idx);

			synchronized(taskManager) {
			synchronized(this) { /* We lock here and not during the whole function to allow other threads to execute */
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

							id.initializeTransient(db, this);
							id.storeWithoutCommit();
							
							onIdentityCreated(id);
							
							db.commit(); Logger.debug(this, "COMMITED.");
						}
						catch(Exception e) {
							db.rollback();
							Logger.error(this, "ROLLED BACK: Error in parseIdentities", e);
						}
					}
				} else {
					Logger.debug(this, "Not importing already existing identity " + requestURI);
					assert(result.size() == 1);
					id = result.next();
					id.initializeTransient(db, this);
					
					synchronized(db.lock()) {
						try {
							id.setLastReceivedFromWoT(time);
							db.commit(); Logger.debug(this, "COMMITED.");
						}
						catch(RuntimeException e) {
							db.rollback();
							Logger.error(this, "ROLLED BACK: Error in parseIdentities", e);
						}
					}
				}
			}
			}
			Thread.yield();
		}
	}
	
	@SuppressWarnings("unchecked")
	private void garbageCollectIdentities() {
		/* Executing the thread loop once will always take longer than THREAD_PERIOD. Therefore, if we set the limit to 3*THREAD_PERIOD,
		 * it will hit identities which were last received before more than 2*THREAD_LOOP, not exactly 3*THREAD_LOOP. */
		long lastAcceptTime = CurrentTimeUTC.getInMillis() - THREAD_PERIOD * 3;
		
		MessageManager messageManager = mFreetalk.getMessageManager();
		PersistentTaskManager taskManager = mFreetalk.getTaskManager();
		
		synchronized(this) {
		Query q = db.query();
		q.constrain(WoTIdentity.class);
		q.descend("mLastReceivedFromWoT").constrain(lastAcceptTime).smaller();
		ObjectSet<WoTIdentity> result = q.execute();
		
		for(WoTIdentity identity : result) {
			Logger.debug(this, "Garbage collecting identity " + identity);
			deleteIdentity(identity, messageManager, taskManager);
		}
		}
	}
	
	private void deleteIdentity(WoTIdentity identity, MessageManager messageManager, PersistentTaskManager taskManager) {
		
		messageManager.onIdentityDeletion(identity);

		if(identity instanceof WoTOwnIdentity)
			taskManager.onOwnIdentityDeletion((WoTOwnIdentity)identity);
		
		synchronized(db.lock()) {
			try {
				identity.initializeTransient(db, this);
				identity.deleteWithoutCommit();
				
				Logger.debug(this, "Identity deleted: " + identity);
				db.commit(); Logger.debug(this, "COMMITED.");
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(db, this, e);
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

	public void run() { 
		Logger.debug(this, "Identity manager started.");
		mThread = Thread.currentThread();
		
		long nextIdentityRequestTime = 0;
		
		Random random = mFreetalk.getPluginRespirator().getNode().fastWeakRandom;
		
		try {
		while(isRunning) {
			Thread.interrupted();
			Logger.debug(this, "Identity manager loop running...");
			
			boolean connected = connectToWoT();

			long currentTime = System.currentTimeMillis();
			
			long sleepTime = connected ? (THREAD_PERIOD/2 + random.nextInt(THREAD_PERIOD)) : WOT_RECONNECT_DELAY;
			
			if(currentTime >= nextIdentityRequestTime) {
				try {
					fetchIdentities();
					fetchOwnIdentities();
					garbageCollectIdentities();
				} catch (Exception e) {
					Logger.debug(this, "Fetching identities failed.", e);
				}
				
				nextIdentityRequestTime = currentTime + sleepTime;
			}
			
			Logger.debug(this, "Identity manager loop finished.");

			try {
				Thread.sleep(sleepTime); // TODO: Maybe use a Ticker implementation instead?
			}
			catch (InterruptedException e)
			{
				mThread.interrupt();
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
		Logger.debug(this, "Stopping the identity manager...");
		isRunning = false;
		mThread.interrupt();
		synchronized(this) {
			while(!shutdownFinished) {
				try {
					wait();
				}
				catch (InterruptedException e) {
					Thread.interrupted();
				}
			}
		}
		Logger.debug(this, "Stopped the indentity manager.");
	}

}
