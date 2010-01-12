/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import plugins.Freetalk.DBUtil;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.exceptions.InvalidParameterException;

import com.db4o.ext.ExtObjectContainer;

import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;
import freenet.support.StringValidityChecker;


/**
 * 
 * Activation policy: WoTIdentity does automatic activation on its own.
 * This means that WoTIdentity can be activated to a depth of only 1 when querying them from the database.
 * All methods automatically activate the object to any needed higher depth.
 * 
 * TODO: Change all code which queries for identities to use the lowest possible activation depth to benefit from automatic activation.
 * 
 * @author xor (xor@freenetproject.org)
 */
public class WoTIdentity implements FTIdentity {
	
	/* Attributes, stored in the database. */
	
	private final String mID;
    
	/** The requestURI used to fetch this identity from Freenet */
	private final FreenetURI mRequestURI;
	
	/** The nickname of this Identity */
	private final String mNickname;
	
	/**
	 * Used for garbage collecting old identities which are not returned by the WoT plugin anymore.
	 * We delete them if they were not received for a certain time interval.
	 */
	private long mLastReceivedFromWoT;
	
	/* References to objects of the plugin, not stored in the database. */
	
	protected transient ExtObjectContainer db;
	
	protected transient WoTIdentityManager mIdentityManager;
	
	/** Get a list of fields which the database should create an index on. */
	public static String[] getIndexedFields() {
		return new String[] { "mID" };
	}

	public WoTIdentity(String myID, FreenetURI myRequestURI, String myNickname) {
		if(myID == null) throw new IllegalArgumentException("ID == null");
		if(myID.length() == 0) throw new IllegalArgumentException("ID.length() == 0");
		if(myRequestURI == null) throw new IllegalArgumentException("RequestURI == null");
		if(myNickname == null) throw new IllegalArgumentException("Nickname == null");
		if(myNickname.length() == 0) throw new IllegalArgumentException("Nickname.length() == 0");
		
		mID = myID;
		mRequestURI = myRequestURI;
		mNickname = myNickname;
		mLastReceivedFromWoT = CurrentTimeUTC.getInMillis();
	}
	
	/**
	 * Has to be used after loading a FTIdentityWoT object from the database to initialize the transient fields.
	 */
	public void initializeTransient(ExtObjectContainer myDB, IdentityManager myIdentityManager) {
		assert(myDB != null);
		db = myDB;
		mIdentityManager = (WoTIdentityManager)myIdentityManager;
	}
	
	public String getID() {
		db.activate(this, 2);
		return mID;
	}
	
	/**
	 * Generates a unique id from a {@link FreenetURI}. 
	 * It is simply a String representing it's routing key.
	 * We use this to identify identities and perform requests on the database. 
	 * 
	 * @param uri The requestURI of the Identity
	 * @return A string to uniquely identify an Identity
	 */
	public static String getIDFromURI (FreenetURI uri) {
		/* WARNING: This is a copy of the code of plugins.WoT.Identity. Freetalk is not allowed to have its own custom IDs, you cannot change
		 * this code here. */
		return Base64.encode(uri.getRoutingKey());
	}

	public FreenetURI getRequestURI() {
		// TODO: If String[] is no nested object to db4o we can decrease this to 3 and also in storeAndCommit / deleteWithoutCommit
		db.activate(this, 4);
		return mRequestURI;
	}

	public String getNickname() {
		db.activate(this, 2);
		return mNickname;
	}

	public String getNickname(int maxLength) {
		db.activate(this, 2);
		if(mNickname.length() > maxLength) {
			return mNickname.substring(0, maxLength-3) + "...";
		}
		return mNickname;
	}

	public String getShortestUniqueName(int maxLength) {
		return mIdentityManager.shortestUniqueName(this, maxLength);
	}

	public String getFreetalkAddress() {
		db.activate(this, 2);
		return mNickname + "@" + mID + "." + Freetalk.WOT_CONTEXT.toLowerCase();	
	}

	public synchronized long getLastReceivedFromWoT() {
		db.activate(this, 2);
		return mLastReceivedFromWoT;
	}
	
	/**
	 * Set the time this identity was last received from the WoT plugin to the given UTC time in milliseconds
	 * @param time
	 */
	public synchronized void setLastReceivedFromWoT(long time) {
		mLastReceivedFromWoT = time;
		storeWithoutCommit();
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
		if(!StringValidityChecker.containsNoIDNBlacklistCharacters(newNickname)
		|| !StringValidityChecker.containsNoInvalidCharacters(newNickname)
		|| !StringValidityChecker.containsNoLinebreaks(newNickname)
		|| !StringValidityChecker.containsNoControlCharacters(newNickname)
		|| !StringValidityChecker.containsNoInvalidFormatting(newNickname))
			throw new InvalidParameterException("Nickname contains invalid characters"); /* FIXME: Tell the user which ones are invalid!!! */
		
		if(newNickname.length() == 0) throw new InvalidParameterException("Blank nickname.");
		if(newNickname.length() > 50) throw new InvalidParameterException("Nickname is too long, the limit is 50 characters.");
	}

	/**
	 * You have to synchronize on this object before modifying the identity and calling storeAndCommit. 
	 */
	public void storeAndCommit() {
		synchronized(db.lock()) {
			storeWithoutCommit();
			db.commit(); Logger.debug(this, "COMMITED.");
		}
	}

	protected void storeWithoutCommit() {
		try {		
			// 4 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			DBUtil.checkedActivate(db, this, 4);

			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			db.store(mRequestURI);
			db.store(this);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(db, this, e);
		}
	}
	
	protected void deleteWithoutCommit() {
		try {
			// 4 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			DBUtil.checkedActivate(db, this, 4);
			
			DBUtil.checkedDelete(db, this);
			
			mRequestURI.removeFrom(db);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(db, this, e);
		}
	}

	public String toString() {
		return getFreetalkAddress();
	}
	
}
