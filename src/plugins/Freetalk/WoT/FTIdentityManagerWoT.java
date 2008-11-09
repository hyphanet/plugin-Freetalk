/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.net.MalformedURLException;
import java.util.List;

import plugins.Freetalk.FTIdentityManager;
import plugins.Freetalk.FTMessage;
import plugins.Freetalk.Freetalk;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

/**
 * An identity manager which uses the identities from the WoT plugin.
 * 
 * @author xor
 *
 */
public class FTIdentityManagerWoT extends FTIdentityManager implements FredPluginTalker {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 1 * 60 * 1000;

	private boolean isRunning = true;
	private Thread mThread;

	private PluginTalker mTalker;

	/**
	 * @param executor
	 */
	public FTIdentityManagerWoT(ObjectContainer myDB, PluginRespirator pr) throws PluginNotFoundException {
		super(myDB, pr.getNode().executor);
		mTalker = pr.getPluginTalker(this, Freetalk.WOT_NAME, Freetalk.PLUGIN_TITLE);
		Logger.debug(this, "Identity manager created.");
	}
	
	private void addFreetalkContext(FTIdentityWoT oid) {
		SimpleFieldSet params = new SimpleFieldSet(true);
		params.putOverwrite("Message", "AddContext");
		params.putOverwrite("Identity", oid.getRequestURI().toString());
		params.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		mTalker.send(params, null);
	}
	
	public void onReply(String pluginname, String indentifier, SimpleFieldSet params, Bucket data) {
		long time = System.currentTimeMillis();
		
		boolean bOwnIdentities = params.get("Message").equals("OwnIdentities");
		if(params.get("Message").equals("Identities") || bOwnIdentities) {
			for(int idx = 1; ; idx++) {
				String uid = params.get("Identity"+idx);
				if(uid == null || uid.equals("")) /* FIXME: Figure out whether the second condition is necessary */
					break;
				String requestURI = params.get("RequestURI"+idx);
				String insertURI = bOwnIdentities ? params.get("InsertURI"+idx) : null;
				String nickname = params.get("Nickname"+idx);
				
				synchronized(this) { /* We lock here and not during the whole function to allow other threads to execute */
					Query q = db.query();
					q.constrain(FTIdentityWoT.class);
					q.descend("mUID").constrain(uid);
					ObjectSet<FTIdentityWoT> result = q.execute();
					FTIdentityWoT id = null; 
		
					if(result.size() == 0) {
						try {
							id = bOwnIdentities ?	new FTOwnIdentityWoT(uid, new FreenetURI(requestURI), new FreenetURI(insertURI), nickname) :
													new FTIdentityWoT(uid, new FreenetURI(requestURI), nickname);

							id.store(db);
						}
						catch(MalformedURLException e) {
							Logger.error(this, "Error in OnReply()", e);
						}
					} else {
						assert(result.size() == 1);
						id = result.next();
					}
					
					if(bOwnIdentities)
						addFreetalkContext(id);
					id.setLastReceivedFromWoT(db, time);
				}
			}
		}

		
		garbageCollectIdentities();
	}
	
	private void receiveIdentities() {
		SimpleFieldSet p1 = new SimpleFieldSet(true);
		p1.putOverwrite("Message", "GetIdentitiesByScore");
		p1.putOverwrite("Select", "+");
		p1.putOverwrite("Context", Freetalk.WOT_CONTEXT);
		mTalker.send(p1, null);
		
		SimpleFieldSet p2 = new SimpleFieldSet(true);
		p2.putOverwrite("Message","GetOwnIdentities");
		mTalker.send(p2, null);
	}
	
	private synchronized void garbageCollectIdentities() {
		/* Executing the thread loop once will always take longer than THREAD_PERIOD. Therefore, if we set the limit to 3*THREAD_PERIOD,
		 * it will hit identities which were last received before more than 2*THREAD_LOOP, not exactly 3*THREAD_LOOP. */
		long lastAcceptTime = System.currentTimeMillis() - THREAD_PERIOD * 3;
		
		Query q = db.query();
		q.constrain(FTIdentityWoT.class);
		q.descend("isNeeded").constrain(false);
		q.descend("lastReceivedFromWoT").constrain(new Long(lastAcceptTime)).smaller();
		ObjectSet<FTIdentityWoT> result = q.execute();
		
		while(result.hasNext()) {
			FTIdentityWoT i = result.next();
			assert(identityIsNotNeeded(i)); /* Check whether the isNeeded field of the identity was correct */
			db.delete(i);
		}
		
		db.commit();
	}
	
	/**
	 * Debug function for checking whether the isNeeded field of an identity is correct.
	 */
	private boolean identityIsNotNeeded(FTIdentityWoT i) {
		/* FIXME: This function does not lock, it should probably. But we cannot lock on the message manager because it locks on the identity
		 * manager and therefore this might cause deadlock. */
		Query q = db.query();
		q.constrain(FTMessage.class);
		q.descend("mAuthor").constrain(i);
		return (q.execute().size() == 0);
	}

	public void run() {
		Logger.debug(this, "Identity manager running.");
		mThread = Thread.currentThread();
		
		try {
			Logger.debug(this, "Waiting for the node to start up...");
			Thread.sleep((long) (1*60*1000 * (0.5f + Math.random()))); /* Let the node start up */
		}
		catch (InterruptedException e)
		{
			mThread.interrupt();
		}
		
		while(isRunning) {
			Logger.debug(this, "Identity manager loop running...");

			receiveIdentities();
			
			Logger.debug(this, "Identity manager loop finished.");

			try {
				Thread.sleep((long) (THREAD_PERIOD * (0.5f + Math.random())));
			}
			catch (InterruptedException e)
			{
				mThread.interrupt();
				Logger.debug(this, "Identity manager loop interrupted!");
			}
		}
		Logger.debug(this, "Identity manager thread exiting.");
	}
	
	public void terminate() {
		Logger.debug(this, "Stopping the identity manager...");
		isRunning = false;
		mThread.interrupt();
		try {
			mThread.join();
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		Logger.debug(this, "Stopped the indentity manager.");
	}

}
