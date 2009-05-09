/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import plugins.Freetalk.CurrentTimeUTC;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.exceptions.InvalidParameterException;

import com.db4o.ext.ExtObjectContainer;

import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.Logger;
import freenet.support.StringValidityChecker;


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
	
	/* References to objects of the plugin, not stored in the database. */
	
	protected transient ExtObjectContainer db;
	
	protected transient WoTIdentityManager mIdentityManager;
	
	/** Get a list of fields which the database should create an index on. */
	public static String[] getIndexedFields() {
		return new String[] { "mUID", "mRequestURI" };
	}

	public WoTIdentity(String myUID, FreenetURI myRequestURI, String myNickname) {
		if(myUID == null) throw new IllegalArgumentException("UID == null");
		if(myUID.length() == 0) throw new IllegalArgumentException("UID.length() == 0");
		if(myRequestURI == null) throw new IllegalArgumentException("RequestURI == null");
		if(myNickname == null) throw new IllegalArgumentException("Nickname == null");
		if(myNickname.length() == 0) throw new IllegalArgumentException("Nickname.length() == 0");
		
		mUID = myUID;
		mRequestURI = myRequestURI;
		mNickname = myNickname;
		mLastReceivedFromWoT = CurrentTimeUTC.getInMillis();
		mIsNeeded = false;
	}
	
	/**
	 * Has to be used after loading a FTIdentityWoT object from the database to initialize the transient fields.
	 */
	public void initializeTransient(ExtObjectContainer myDB, IdentityManager myIdentityManager) {
		assert(myDB != null);
		db = myDB;
		mIdentityManager = (WoTIdentityManager)myIdentityManager;
	}
	
	public String getUID() {
		return mUID;
	}
	
	/**
	 * Generates a unique id from a {@link FreenetURI}. 
	 * It is simply a String representing it's routing key.
	 * We use this to identify identities and perform requests on the database. 
	 * 
	 * @param uri The requestURI of the Identity
	 * @return A string to uniquely identify an Identity
	 */
	public static String getUIDFromURI (FreenetURI uri) {
		/* WARNING: This is a copy of the code of plugins.WoT.Identity. Freetalk is not allowed to have its own custom IDs, you cannot change
		 * this code here. */
		return Base64.encode(uri.getRoutingKey());
	}

	public FreenetURI getRequestURI() {
		return mRequestURI;
	}

	public String getNickname() {
		return mNickname;
	}
	
	public String getFreetalkAddress() {
		return mNickname + "@" + mUID + "." + Freetalk.WOT_CONTEXT.toLowerCase();	
	}

	public synchronized long getLastReceivedFromWoT() {
		return mLastReceivedFromWoT;
	}
	
	/**
	 * Set the time this identity was last received from the WoT plugin to the given UTC time in milliseconds
	 * @param time
	 */
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
	

	/**
	 * Validates the nickname. If it is valid, nothing happens. If it is invalid, an exception is thrown which exactly describes what is
	 * wrong about the nickname.
	 * 
	 * @throws InvalidParameterException If the nickname is invalid, the exception contains a description of the problem as message. 
	 */
	/* IMPORTANT: This code is duplicated in plugins.WoT.Identity.isNicknameValid().
	 * Please also modify it there if you modify it here */
	public static void validateNickname(String newNickname) throws InvalidParameterException {
		if(!StringValidityChecker.containsNoIDNBlacklistCharacters(newNickname))
			throw new InvalidParameterException("Nickname contains invalid characters"); /* FIXME: Tell the user which ones are invalid!!! */
		
		if(newNickname.length() == 0) throw new InvalidParameterException("Blank nickname.");
		if(newNickname.length() > 50) throw new InvalidParameterException("Nickname is too long, the limit is 50 characters.");
	}

	public void store() {
		/* FIXME: check for duplicates */

		synchronized(db.lock()) {
		if(db.ext().isStored(this) && !db.ext().isActive(this))
			throw new RuntimeException("Trying to store a non-active WoTIdentity object");
		
		db.store(mRequestURI);
		db.store(this);
		db.commit(); Logger.debug(this, "COMMITED.");
		}
	}
	
}
