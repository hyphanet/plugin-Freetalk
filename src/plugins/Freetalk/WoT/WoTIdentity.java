/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.IdentityManager;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;


/**
 * @author xor
 *
 */
public class WoTIdentity implements FTIdentity {
	
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
	

	/** The latest edition number of the identity's messagelist USK which we downloaded */
	private int mLatestMessagelist;
	
	/** The date which the latest downloaded messagelist edition fully covers. I.e. if the messagelist contains only messages of date X then
	 * this variable has to be set to X minus 1 because the next messagelist might still contain messages of date X */
	private Date mLatestMessagelistDate;
	
	/** The oldest edition number of the identity's messagelist USK which we downloaded. */
	private int mOldestMessagelist;
	
	/** The date which the oldest downloaded messagelist edition fully covers. I.e. if the messagelist contains only messages of date X then
	 * this variable has to be set to X plus 1 because the previous messagelist might still contain messages of date X */
	private Date mOldestMessagelistDate;
	
	
	/* References to objects of the plugin, not stored in the database. */
	
	protected transient ObjectContainer db;
	
	private transient static final Calendar mCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	
	/** Get a list of fields which the database should create an index on. */
	public static String[] getIndexedFields() {
		return new String[] { "mUID", "mRequestURI" };
	}

	public WoTIdentity(String myUID, FreenetURI myRequestURI, String myNickname) {
		if(myUID == null || myUID.length() == 0 || myRequestURI == null || myNickname == null || myNickname.length() == 0)
			throw new IllegalArgumentException();
		
		mUID = myUID;
		mRequestURI = myRequestURI;
		mLatestMessagelist = -1;
		mLatestMessagelistDate = null;
		mOldestMessagelist = -1;
		mOldestMessagelistDate = null;
		mNickname = myNickname;
		mLastReceivedFromWoT = System.currentTimeMillis();
		mIsNeeded = false;
	}
	
	/**
	 * Has to be used after loading a FTIdentityWoT object from the database to initialize the transient fields.
	 */
	public void initializeTransient(ObjectContainer myDB, IdentityManager myIdentityManager) {
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

	public synchronized int getLatestMessagelist() {
		return mLatestMessagelist;
	}
	
	public synchronized void setLatestMessagelist(int newLatestMessagelist, Date newLatestMessagelistDate) {
		mLatestMessagelist = newLatestMessagelist;
		mLatestMessagelistDate = newLatestMessagelistDate;
	}
	
	public synchronized Date getLatestMessagelistDate() {
		return mLatestMessagelistDate;
	}
	
	public synchronized int getOldestMessagelist() {
		return mOldestMessagelist;
	}
	
	public synchronized void setOldestMessagelist(int newOldestMessagelist, Date newOldestMessagelistDate) {
		mLatestMessagelist = newOldestMessagelist;
		mLatestMessagelistDate = newOldestMessagelistDate;
	}
	
	public synchronized Date getOldestMessagelistDate() {
		return mOldestMessagelistDate;
	}
	
	public void store() {
		/* FIXME: check for duplicates */
		db.store(this);
		db.commit();
	}
	
}
