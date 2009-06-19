/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Iterator;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.Message;
import plugins.Freetalk.PluginTalkerBlocking;
import plugins.Freetalk.exceptions.DuplicateIdentityException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.io.NativeThread;

/**
 * An identity manager which uses the identities from the WoT plugin.
 * 
 * @author xor
 *
 */
public class WoTIdentityManager extends IdentityManager {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 5 * 60 * 1000;
	
	/** The amount of time between each attempt to connect to the WoT plugin */
	private static final int WOT_RECONNECT_DELAY = 5 * 1000; 
	
	private final Freetalk mFreetalk;

	private volatile boolean isRunning = false;
	private volatile boolean shutdownFinished = false;
	private Thread mThread = null;

	private PluginTalkerBlocking mTalker = null;

	public WoTIdentityManager(ExtObjectContainer myDB, Freetalk myFreetalk) {
		super(myDB, myFreetalk.getPluginRespirator().getNode().executor);
		
		mFreetalk = myFreetalk;
		
		isRunning = true;
		mExecutor.execute(this, "FT Identity Manager");
	}
	
	/**
	 * For being used in JUnit tests to run without a node.
	 */
	public WoTIdentityManager(ExtObjectContainer myDB) {
		super(myDB);
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
	 * @throws Exception If the WoT plugin replied with an error message or not with the expected message.
	 */
	private PluginTalkerBlocking.Result sendFCPMessageBlocking(SimpleFieldSet params, Bucket data, String expectedReplyMessage) throws Exception {
		PluginTalkerBlocking.Result result = mTalker.sendBlocking(params, data);
		
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
			/* FIXME: Garbage collect own identities! */
		} 
		catch(PluginNotFoundException e) {} /* Ignore, return the ones which are in database now */
		
		return super.ownIdentityIterator();
	}

	@SuppressWarnings("unchecked")
	public synchronized WoTIdentity getIdentity(String uid) throws NoSuchIdentityException {
		Query q = db.query();
		q.constrain(WoTIdentity.class);
		q.descend("mUID").constrain(uid);
		ObjectSet<WoTIdentity> result = q.execute();
		
		switch(result.size()) {
			case 1:
				WoTIdentity identity = result.next();
				identity.initializeTransient(db, this);
				return identity;
			case 0:
				throw new NoSuchIdentityException(uid);
			default:
				throw new DuplicateIdentityException();
		}
	}
	
	public FTIdentity getIdentityByURI(FreenetURI uri) throws NoSuchIdentityException {
		return getIdentity(WoTIdentity.getUIDFromURI(uri));
	}
	
	@SuppressWarnings("unchecked")
	public synchronized WoTOwnIdentity getOwnIdentity(String uid) throws NoSuchIdentityException {
		Query q = db.query();
		q.constrain(WoTOwnIdentity.class);
		q.descend("mUID").constrain(uid);
		ObjectSet<WoTOwnIdentity> result = q.execute();
		
		switch(result.size()) {
			case 1:
				WoTOwnIdentity identity = result.next();
				identity.initializeTransient(db, this);
				return identity;
			case 0:
				throw new NoSuchIdentityException(uid);
			default:
				throw new DuplicateIdentityException();
		}
	}

	// FIXME: should throw exceptions instead of silently returning ""
	protected String getProperty(FTOwnIdentity treeOwner, FTIdentity target, String property) {
		if(mTalker == null)
			return "";

		SimpleFieldSet sfs = new SimpleFieldSet(true);
		SimpleFieldSet results;
		sfs.putOverwrite("Message", "GetIdentity");
		sfs.putOverwrite("TreeOwner", treeOwner.getUID());
		sfs.putOverwrite("Identity", target.getUID());
		try {
			results = mTalker.sendBlocking(sfs, null).params; /* Verify that the old connection is still alive */
		}
		catch(PluginNotFoundException e) {
			return "";
		}
		String result = results.get(property);
		return result;
	}

	public int getScore(FTOwnIdentity treeOwner, FTIdentity target) {
		try {
			return Integer.parseInt(getProperty(treeOwner, target, "Score"));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public int getTrust(FTOwnIdentity treeOwner, FTIdentity target) {
		try {
			return Integer.parseInt(getProperty(treeOwner, target, "Trust"));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public void setTrust(FTOwnIdentity treeOwner, FTIdentity identity, int trust, String comment) {
		if(mTalker == null)
			return; // FIXME: throw something?

		SimpleFieldSet request = new SimpleFieldSet(true);
		request.putOverwrite("Message", "SetTrust");
		request.putOverwrite("Truster", treeOwner.getUID());
		request.putOverwrite("Trustee", identity.getUID());
		request.putOverwrite("Value", String.valueOf(trust));
		request.putOverwrite("Comment", comment);
		try {
			mTalker.sendBlocking(request, null);
		}
		catch(PluginNotFoundException e) {
			// ignore silently
		}
	}

	private synchronized void addFreetalkContext(WoTIdentity oid){
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "AddContext");
		params.putOverwrite("Identity", oid.getUID());
		params.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		try {
			mTalker.sendBlocking(params, null);
		} catch(PluginNotFoundException e) {
			/* We do not throw because that would make parseIdentities() too complicated */
			Logger.error(this, "Adding Freetalk context failed.", e);
		}
	}
	
	/**
	 * Fetches the identities with positive score from WoT and stores them in the database.
	 * @throws PluginNotFoundException If the connection to WoT has been lost.
	 */
	private synchronized void fetchIdentities() throws PluginNotFoundException {
		if(mTalker == null)
			throw new PluginNotFoundException();
		
		Logger.debug(this, "Requesting identities with positive score from WoT ...");
		SimpleFieldSet p1 = new SimpleFieldSet(true);
		p1.putOverwrite("Message", "GetIdentitiesByScore");
		p1.putOverwrite("Selection", "+");
		p1.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		parseIdentities(mTalker.sendBlocking(p1, null).params, false);
	}
	
	/**
	 * Fetches the own identities with positive score from WoT and stores them in the database.
	 * @throws PluginNotFoundException If the connection to WoT has been lost.
	 */
	private synchronized void fetchOwnIdentities() throws PluginNotFoundException {
		if(mTalker == null)
			throw new PluginNotFoundException();
		
		Logger.debug(this, "Requesting own identities from WoT ...");
		SimpleFieldSet p2 = new SimpleFieldSet(true);
		p2.putOverwrite("Message","GetOwnIdentities");
		parseIdentities(mTalker.sendBlocking(p2, null).params, true);
	}
	
	@SuppressWarnings("unchecked")
	private void parseIdentities(SimpleFieldSet params, boolean bOwnIdentities) {
		if(bOwnIdentities)
			Logger.debug(this, "Parsing received own identities...");
		else
			Logger.debug(this, "Parsing received identities...");
		
		long time = CurrentTimeUTC.getInMillis();
	
		for(int idx = 1; ; idx++) {
			String uid = params.get("Identity"+idx);
			if(uid == null || uid.equals("")) /* FIXME: Figure out whether the second condition is necessary */
				break;
			String requestURI = params.get("RequestURI"+idx);
			String insertURI = bOwnIdentities ? params.get("InsertURI"+idx) : null;
			String nickname = params.get("Nickname"+idx);

			synchronized(this) { /* We lock here and not during the whole function to allow other threads to execute */
				Query q = db.query();
				q.constrain(WoTIdentity.class);
				q.descend("mUID").constrain(uid);
				ObjectSet<WoTIdentity> result = q.execute();
				WoTIdentity id = null; 

				if(result.size() == 0) {
					synchronized(db.lock()) {
						try {
							Logger.debug(this, "Importing identity from WoT: " + requestURI);
							id = bOwnIdentities ?	new WoTOwnIdentity(uid, new FreenetURI(requestURI), new FreenetURI(insertURI), nickname) :
								new WoTIdentity(uid, new FreenetURI(requestURI), nickname);

							id.initializeTransient(db, this);
							id.storeWithoutCommit();
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
				}

				if(id != null) {
					if(bOwnIdentities)	/* FIXME: Only add the context if the user actually uses the identity with Freetalk */
						addFreetalkContext(id);
					id.setLastReceivedFromWoT(time);
				}
			}
			Thread.yield();
		}
	}
	
	@SuppressWarnings("unchecked")
	private synchronized void garbageCollectIdentities() {
		/* Executing the thread loop once will always take longer than THREAD_PERIOD. Therefore, if we set the limit to 3*THREAD_PERIOD,
		 * it will hit identities which were last received before more than 2*THREAD_LOOP, not exactly 3*THREAD_LOOP. */
		long lastAcceptTime = System.currentTimeMillis() - THREAD_PERIOD * 3; /* FIXME: Use UTC */
		
		Query q = db.query();
		q.constrain(WoTIdentity.class);
		q.descend("isNeeded").constrain(false);
		q.descend("lastReceivedFromWoT").constrain(new Long(lastAcceptTime)).smaller();
		ObjectSet<WoTIdentity> result = q.execute();
		
		while(result.hasNext()) {
			synchronized(db.lock()) {
				try {
					WoTIdentity i = result.next();
					assert(identityIsNotNeeded(i)); /* Check whether the isNeeded field of the identity was correct */
					Logger.debug(this, "Garbage collecting identity " + i.getRequestURI());
					i.deleteWithoutCommit();
					db.commit(); Logger.debug(this, "COMMITED.");
				}
				catch(RuntimeException e) {
					db.rollback();
					Logger.error(this, "ROLLED BACK: Error in garbageCollectIdentities", e);
				}
			}
		}
		
	}
	
	/**
	 * Debug function for checking whether the isNeeded field of an identity is correct.
	 */
	private boolean identityIsNotNeeded(WoTIdentity i) {
		/* FIXME: This function does not lock, it should probably. But we cannot lock on the message manager because it locks on the identity
		 * manager and therefore this might cause deadlock. */
		Query q = db.query();
		q.constrain(Message.class);
		q.descend("mAuthor").constrain(i);
		return (q.execute().size() == 0);
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
		
		try {
		while(isRunning) {
			Thread.interrupted();
			Logger.debug(this, "Identity manager loop running...");
			
			boolean connected = connectToWoT();

			long currentTime = System.currentTimeMillis();
			long sleepTime = connected ? (long) (THREAD_PERIOD * (0.5f + Math.random())) : WOT_RECONNECT_DELAY;
			
			if(currentTime >= nextIdentityRequestTime) {
				try {
					fetchIdentities();
					fetchOwnIdentities();
					garbageCollectIdentities();
				} catch (PluginNotFoundException e) {
					Logger.debug(this, "Connection to WoT lost while requesting identities.");
				}
				
				nextIdentityRequestTime = currentTime + sleepTime;
			}
			
			Logger.debug(this, "Identity manager loop finished.");

			try {
				Thread.sleep(sleepTime);
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
