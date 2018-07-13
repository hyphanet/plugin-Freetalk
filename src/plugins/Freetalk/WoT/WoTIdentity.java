/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.net.MalformedURLException;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.Persistent.IndexedClass;
import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.Logger;
import freenet.support.StringValidityChecker;
import freenet.support.codeshortification.IfNotEquals;
import freenet.support.codeshortification.IfNull;


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
@IndexedClass // TODO: Check whether we really need this index.
public class WoTIdentity extends Persistent implements Identity {
	
	/* Attributes, stored in the database. */
	
	@IndexedField
	private final String mID;
    
	/** The {@link FreenetURI} used to fetch this identity from Freenet.
	 *  Is a SSK request URI. */
	private final String mRequestURI;
	
	/** The nickname of this Identity */
	private final String mNickname;
	
	/**
	 * Used for garbage collecting old identities which are not returned by the WoT plugin anymore.
	 * We delete them if their last fetch ID is different to the fetch ID of the current fetch.
	 */
	private long mLastReceivedFromWoT;


	// TODO: Remove ID parameter and compute the ID from the URI
	public WoTIdentity(String myID, FreenetURI myRequestURI, String myNickname) {
		if(myID == null) throw new IllegalArgumentException("ID == null");
		if(myID.length() == 0) throw new IllegalArgumentException("ID.length() == 0");
		if(myRequestURI == null) throw new IllegalArgumentException("RequestURI == null");
		if(myNickname == null) throw new IllegalArgumentException("Nickname == null");
		
		try {
			validateNickname(myNickname);
		} catch (InvalidParameterException e) {
			throw new IllegalArgumentException(e);
		}
		
		mID = myID;
		mRequestURI = myRequestURI.toString();
		mNickname = myNickname;
		mLastReceivedFromWoT = 0;
		
		IfNotEquals.thenThrow(IdentityID.construct(mID), IdentityID.constructFromURI(myRequestURI), "myID");
	}
	
	@Override
	public void databaseIntegrityTest() throws Exception {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		
		IfNull.thenThrow(mID, "mID");
		IfNull.thenThrow(mRequestURI, "mRequestURI");
		IfNull.thenThrow(mNickname, "mNickname");
		
		// Throws MalformedURLException if invalid
		new FreenetURI(mRequestURI);
		
		IfNotEquals.thenThrow(IdentityID.construct(mID), IdentityID.constructFromURI(getRequestURI()), "mID");
		
		try {
			validateNickname(mNickname);
		} catch (InvalidParameterException e) {
			throw new IllegalStateException(e);
		}
		
		if(mLastReceivedFromWoT < 0)
			throw new IllegalStateException("mLastReceivedFromWoT == " + mLastReceivedFromWoT);
	}
	

	@Override public String getID() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
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
	// TODO: Replace with IdentityID.constructFromURI
	public static String getIDFromURI (FreenetURI uri) {
		/* WARNING: This is a copy of the code of plugins.WoT.Identity. Freetalk is not allowed to have its own custom IDs, you cannot change
		 * this code here. */
		return Base64.encode(uri.getRoutingKey());
	}

	@Override public FreenetURI getRequestURI() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		try {
			return new FreenetURI(mRequestURI);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public String getNickname() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		return mNickname;
	}

	protected String getNickname(int maxLength) {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		if(mNickname.length() > maxLength) {
			return mNickname.substring(0, maxLength) + "...";
		}
		return mNickname;
	}

	@Override public String getShortestUniqueName() {
		return mFreetalk.getIdentityManager().getShortestUniqueName(this);
	}

	@Override public String getFreetalkAddress() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		return mNickname + "@" + mID + "." + Freetalk.WOT_CONTEXT.toLowerCase();	
	}
	
	/**
	 * Returns a Freetalk-address with a maximal content length.
	 * The format will be "nickname@abbreviated_routing_key...", i.e. 3 dots will be appended if the length exceeds the maximal length.
	 * The "@" and "..." are not included in the length computation - therefore its called maximal <b>content</b> length, not maximal length.
	 * If the nickname does not fit in the maximal length it is NOT abbreviated, the full nickname is returned then.
	 * 
	 * The reason for this weird definition is to allow easy computation of nicknames which have a shortest unique length...
	 * See {@link WoTIdentityManager.updateShortestUniqueNicknameCache} for how this is used.
	 */
	protected String getFreetalkAddress(int maxContentLength) {
		final String address = getFreetalkAddress();
		
		if(getNickname().length() > maxContentLength)
			return getNickname();
		
		if(address.length() > maxContentLength) {
			return address.substring(0, maxContentLength+1) + "..."; // "+1" because the "@" does not count as length.
		}
		return address;
	}

	public synchronized long getLastReceivedFromWoT() {
		checkedActivate(1); // long is a db4o primitive type so 1 is enough
		return mLastReceivedFromWoT;
	}
	
	/**
	 * Set the ID of the identity fetch in which this identity was last received from the WoT plugin to the given unique ID.
	 */
	public synchronized void setLastReceivedFromWoT(long fetchID) {
		checkedActivate(1);
		mLastReceivedFromWoT = fetchID;
		storeWithoutCommit(); // TODO: Move store() calls outside of class identity
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
		|| !StringValidityChecker.containsNoInvalidFormatting(newNickname)
		|| newNickname.contains("@")) // Must not be allowed since we use it to generate "identity@public-key-hash" unique nicknames
			throw new InvalidParameterException("Nickname contains invalid characters"); /* TODO: Tell the user which ones are invalid!!! */
		
		if(newNickname.length() == 0) throw new InvalidParameterException("Blank nickname.");
		if(newNickname.length() > 30) throw new InvalidParameterException("Nickname is too long, the limit is 30 characters.");
	}

	@Override protected void checkedCommit(Object loggingObject) {
		super.checkedCommit(loggingObject);
	}
	
	/**
	 * You have to synchronize on this object before modifying the identity and calling storeAndCommit. 
	 */
	public void storeAndCommit() {
		synchronized(Persistent.transactionLock(mDB)) {
			storeWithoutCommit();
			checkedCommit(this);
		}
	}

	@Override protected void storeWithoutCommit() {
		try {		
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(1);

			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			// No need to manually store the String members: It is a db4o primitive type and as such
			// will be stored along with this object.
			
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	@Override protected void deleteWithoutCommit() {
		try {
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(this, 1);
			
			// No need to manually delete the String members: It is a db4o primitive type and as
			// such will be deleted along with this object.
			
			checkedDelete();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	@Override public String toString() {
		if(mDB != null)
			return getFreetalkAddress();
		
		// We do not throw a NPE because toString() is usually used in logging, we want the logging to be robust
		
		Logger.error(this, "toString() called before initializeTransient()!");
		
		return super.toString() + " (intializeTransient() not called!, identity ID may be null, here it is: " + mID + ")";
	}
	
}
