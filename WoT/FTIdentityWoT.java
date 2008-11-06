/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;
import plugins.Freetalk.FTIdentity;

import plugins.WoT.Identity;

/**
 * @author xor
 *
 */
public class FTIdentityWoT implements FTIdentity {
	
	protected final ObjectContainer db;
	
	protected final Identity mIdentity;
	
	/**
	 * Used for garbage collecting old identities which are not returned by the WoT plugin anymore.
	 * We delete them if they were not received for a certain time interval.
	 */
	private long mLastReceivedFromWoT;
	
	/**
	 * Set to true if the identity is referenced by any messages.
	 */
	private boolean mIsNeeded;

	public FTIdentityWoT(ObjectContainer myDB, Identity myIndentity) {
		db = myDB;
		mIdentity = myIndentity;
		mLastReceivedFromWoT = System.currentTimeMillis();
		mIsNeeded = false;
	}

	public synchronized boolean doesPublishTrustList() {
		return mIdentity.doesPublishTrustList();
	}

	public synchronized Date getLastChange() {
		return mIdentity.getLastChange();
	}

	public synchronized String getNickName() {
		return mIdentity.getNickName();
	}

	public synchronized FreenetURI getRequestURI() {
		return mIdentity.getRequestURI();
	}
	
	public synchronized long getLastReceivedFromWoT() {
		return mLastReceivedFromWoT;
	}
	
	public synchronized void setLastReceivedFromWoT(long time) {
		mLastReceivedFromWoT = time;
		store();
	}
	
	public synchronized boolean isNeeded() {
		return mIsNeeded;
	}
	
	public synchronized void setIsNeeded(boolean newValue) {
		mIsNeeded = newValue;
	}
	
	protected void store() {
		db.store(this);
		db.commit();
	}

}
