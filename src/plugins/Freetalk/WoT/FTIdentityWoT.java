/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import plugins.Freetalk.FTIdentity;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;


/**
 * @author xor
 *
 */
public class FTIdentityWoT implements FTIdentity {
	
	/* Attributes, stored in the database. */
	
	private final String mUID;
    /** The requestURI used to fetch this identity from Freenet */
	private final FreenetURI mRequestURI;
	/** The nickname of this Identity */
	private final String mNickname;
	
	/**
	 * Used for garbage collecting old identities which are not returned by the WoT plugin anymore.
	 * We delete them if they were not received for a certain time interval.
	 */
	private long mLastReceivedFromWoT;
	
	/**
	 * Set to true if the identity is referenced by any messages.
	 */
	private boolean mIsNeeded;
	
	
	/* References to objects of the plugin, not stored in the database. */
	
	private transient ObjectContainer db;
	

	public FTIdentityWoT(String myUID, FreenetURI myRequestURI, String myNickname) {
		if(myUID == null || myUID.length() == 0 || myRequestURI == null || myNickname == null || myNickname.length() == 0)
			throw new IllegalArgumentException();
		
		mUID = myUID;
		mRequestURI = myRequestURI;
		mNickname = myNickname;
		mLastReceivedFromWoT = System.currentTimeMillis();
		mIsNeeded = false;
	}
	
	/**
	 * Has to be used after loading a FTIdentityWoT object from the database to initialize the transient fields.
	 */
	public void initializeTransient(ObjectContainer myDB) {
		assert(myDB != null);
		db = myDB;
	}
	
	public String getUID() {
		return mUID;
	}

	public FreenetURI getRequestURI() {
		return mRequestURI;
	}

	public String getNickname() {
		return mNickname;
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
		store();
	}
	
	public void store() {
		/* FIXME: check for duplicates */
		db.store(this);
		db.commit();
	}
}
