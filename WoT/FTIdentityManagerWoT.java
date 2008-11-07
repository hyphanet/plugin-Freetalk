/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.List;

import plugins.Freetalk.FTIdentityManager;
import plugins.WoT.Identity;
import plugins.WoT.OwnIdentity;
import plugins.WoT.WoT;
import plugins.WoT.exceptions.InvalidParameterException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.support.Executor;
import freenet.support.Logger;

/**
 * An identity manager which uses the identities from the WoT plugin.
 * 
 * @author xor
 *
 */
public class FTIdentityManagerWoT extends FTIdentityManager {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 5 * 60 * 1000;
	
	private WoT mWoT;

	/**
	 * @param executor
	 */
	public FTIdentityManagerWoT(ObjectContainer myDB, Executor executor, WoT newWoT) {
		super(myDB, executor);
		mWoT = newWoT;
		Logger.debug(this, "Identity manager started.");
	}
	
	private void receiveIdentities() throws InvalidParameterException {
		long time = System.currentTimeMillis();
		
		ObjectSet<OwnIdentity> oids = mWoT.getAllOwnIdentities();
		for(OwnIdentity o : oids) {
			synchronized(this) { /* We lock here and not during the whole function to allow other threads to execute */
				Query q = db.query();
				q.constrain(FTOwnIdentityWoT.class);
				q.descend("mIdentity").equals(o);
				ObjectSet<FTOwnIdentityWoT> result = q.execute();
				
				if(result.size() == 0) {
					db.store(new FTOwnIdentityWoT(db, o));
					db.commit();
				} else {
					assert(result.size() == 1);
					result.next().setLastReceivedFromWoT(time);
				}
			}
		}
		
		List<Identity> ids = mWoT.getIdentitiesByScore(null, 30, "freetalk");	/* FIXME: the "30" has to be configurable */

		for(Identity i : ids) {
			synchronized(this) { /* We lock here and not during the whole function to allow other threads to execute */
				Query q = db.query();
				q.constrain(FTIdentityWoT.class);
				q.descend("mIdentity").equals(i);
				ObjectSet<FTIdentityWoT> result = q.execute();
	
				if(result.size() == 0) {
					db.store(new FTIdentityWoT(db, i));
					db.commit();
				} else {
					assert(result.size() == 1);
					result.next().setLastReceivedFromWoT(time);
				}
			}
		}
	}
	
	private synchronized void garbageCollectIdentities() {
		/* Executing the thread loop once will always take longer than THREAD_PERIOD. Therefore, if we set the limit to 3*THREAD_PERIOD,
		 * it will hit identities which were last received before more than 2*THREAD_LOOP, not exactly 3*THREAD_LOOP. */
		long lastAcceptTime = System.currentTimeMillis() - THREAD_PERIOD * 3;
		
		Query q = db.query();
		q.constrain(FTIdentityWoT.class);
		q.descend("isNeeded").equals(false);
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
		q.constrain(FTMessageWoT.class);
		q.descend("mAuthor").equals(i);
		return (q.execute().size() == 0);
	}
	

	@Override
	public void run() {
		Logger.debug(this, "Identity manager running.");
		
		try {
			Logger.debug(this, "Waiting for the node to start up...");
			Thread.sleep((long) (3*60*1000 * (0.5f + Math.random()))); /* Let the node start up */
		} catch (InterruptedException e) { }
		
		while(isRunning) {
			Logger.debug(this, "Identity manager loop running...");
			
			try {
				receiveIdentities();
			}

			catch(InvalidParameterException e) {
				Logger.error(this, "Exception in identity fetch loop", e);
			}
			
			garbageCollectIdentities();
			
			Logger.debug(this, "Identity manager loop finished.");

			try {
				Thread.sleep((long) (THREAD_PERIOD * (0.5f + Math.random())));
			} catch (InterruptedException e) { }
		}
	}
}
