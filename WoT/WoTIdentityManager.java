/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.net.MalformedURLException;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.Message;
import plugins.Freetalk.exceptions.DuplicateIdentityException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
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
public class WoTIdentityManager extends IdentityManager implements FredPluginTalker {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 10 * 60 * 1000;

	private volatile boolean isRunning = false;
	private volatile boolean shutdownFinished = false;
	private Thread mThread = null;

	private PluginTalker mTalker;
	
	private final Object sfsLock = new Object();
	private SimpleFieldSet sfsIdentities = null;
	private SimpleFieldSet sfsOwnIdentities = null;

	/**
	 * @param executor
	 */
	public WoTIdentityManager(ObjectContainer myDB, PluginRespirator pr) throws PluginNotFoundException {
		super(myDB, pr.getNode().executor);
		mTalker = pr.getPluginTalker(this, Freetalk.WOT_NAME, Freetalk.PLUGIN_TITLE);
		isRunning = true;
		mExecutor.execute(this, "FT Identity Manager");
		Logger.debug(this, "Identity manager started.");
	}
	
	public WoTIdentityManager() {
		super();
	}

	public synchronized WoTIdentity getIdentity(String uid) throws NoSuchIdentityException {
		Query q = db.query();
		q.constrain(WoTIdentity.class);
		q.descend("mUID").constrain(uid);
		ObjectSet<WoTOwnIdentity> result = q.execute();
		
		if(result.size() > 1)
			throw new DuplicateIdentityException();
	
		if(result.size() == 0)
			throw new NoSuchIdentityException();
		
		return result.next();
	}
	
	public FTIdentity getIdentityByURI(FreenetURI uri) throws NoSuchIdentityException {
		return getIdentity(WoTIdentity.getUIDFromURI(uri));
	}
	
	public synchronized WoTOwnIdentity getOwnIdentity(String uid) throws NoSuchIdentityException {
		Query q = db.query();
		q.constrain(WoTOwnIdentity.class);
		q.descend("mUID").constrain(uid);
		ObjectSet<WoTOwnIdentity> result = q.execute();
		
		if(result.size() > 1)
			throw new DuplicateIdentityException();
	
		if(result.size() == 0)
			throw new NoSuchIdentityException();
		
		return result.next();
	}

	public int getScore(WoTOwnIdentity treeOwner, FTIdentity target) {
		// FIXME: implement
		return 0;
	}

	private void addFreetalkContext(WoTIdentity oid) {
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "AddContext");
		params.putOverwrite("Identity", oid.getRequestURI().toString());
		params.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		mTalker.send(params, null);
	}
	
	/**
	 * Called by the PluginTalker, it is run in a different Thread. Therefore, we store the result and let our own thread process it so we
	 * can run the processing at minimal thread priority, which is necessary because the amount of identities might become large.
	 */
	public void onReply(String pluginname, String indentifier, SimpleFieldSet params, Bucket data) {
		String message = params.get("Message");
		
		boolean identitiesWereReceived = false;
		
		synchronized(sfsLock) {
			if(message.equals("Identities")) {
				sfsIdentities = params;
				identitiesWereReceived = true;
			}
			else if(message.equals("OwnIdentities")) {
				sfsOwnIdentities = params;
				identitiesWereReceived = true;
			}
		}
		
		if(identitiesWereReceived)
			mThread.interrupt();
	}
	
	private void requestIdentities() {
		SimpleFieldSet p1 = new SimpleFieldSet(true);
		p1.putOverwrite("Message", "GetIdentitiesByScore");
		p1.putOverwrite("Select", "+");
		p1.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		mTalker.send(p1, null);
		
		SimpleFieldSet p2 = new SimpleFieldSet(true);
		p2.putOverwrite("Message","GetOwnIdentities");
		mTalker.send(p2, null);
	}
	
	private void parseIdentities(SimpleFieldSet params, boolean bOwnIdentities) {
		long time = System.currentTimeMillis();
	
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
					try {
						Logger.debug(this, "Importing identity from WoT: " + requestURI);
						id = bOwnIdentities ?	new WoTOwnIdentity(uid, new FreenetURI(requestURI), new FreenetURI(insertURI), nickname) :
							new WoTIdentity(uid, new FreenetURI(requestURI), nickname);

						id.initializeTransient(db, this);
						id.store();
					}
					catch(Exception e) {
						Logger.error(this, "Error in parseIdentities", e);
					}
				} else {
					Logger.debug(this, "Not importing already existing identity " + requestURI);
					assert(result.size() == 1);
					id = result.next();
					id.initializeTransient(db, this);
				}

				if(bOwnIdentities)	/* FIXME: Only add the context if the user actually uses the identity with Freetalk */
					addFreetalkContext(id);
				id.setLastReceivedFromWoT(time);
			}
			Thread.yield();
		}
	}
	
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
			WoTIdentity i = result.next();
			assert(identityIsNotNeeded(i)); /* Check whether the isNeeded field of the identity was correct */
			Logger.debug(this, "Garbage collecting identity " + i.getRequestURI());
			db.delete(i);
		}
		
		db.commit();
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

	public void run() {
		Logger.debug(this, "Identity manager running.");
		mThread = Thread.currentThread();
		
		try {
			Logger.debug(this, "Waiting for the node to start up...");
			Thread.sleep((long) (0.5f*60*1000 * (0.5f + Math.random()))); /* Let the node start up */
		}
		catch (InterruptedException e)
		{
			mThread.interrupt();
		}
		
		long nextIdentityRequestTime = 0;
		
		try {
		while(isRunning) {
			Thread.interrupted();
			Logger.debug(this, "Identity manager loop running...");

			boolean identitiesWereReceived;
			
			synchronized(sfsLock) {
				identitiesWereReceived = sfsIdentities != null || sfsOwnIdentities != null;
				if(sfsIdentities != null) {
					parseIdentities(sfsIdentities, false);
					sfsIdentities = null;
				}
				if(sfsOwnIdentities != null) {
					parseIdentities(sfsOwnIdentities, true);
					sfsOwnIdentities = null;
				}
				
				if(identitiesWereReceived)
					garbageCollectIdentities();
			}

			long currentTime = System.currentTimeMillis();
			long sleepTime = (long) (THREAD_PERIOD * (0.5f + Math.random()));
			if(currentTime >= nextIdentityRequestTime) {
				requestIdentities();
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
