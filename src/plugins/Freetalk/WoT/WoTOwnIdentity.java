/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Map;
import java.util.TreeMap;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import plugins.Freetalk.exceptions.NotTrustedException;
import freenet.keys.FreenetURI;

/**
 * 
 * 
 * Activation policy: WoTOwnIdentity does automatic activation on its own.
 * This means that WoTOwnIdentity can be activated to a depth of only 1 when querying them from the database.
 * All methods automatically activate the object to any needed higher depth.
 * 
 * FIXME: The above is currently not valid. We do not activate the TreeMap mAssessed because trees have a varying depth by nature! The TreeMap MUST BE removed
 * ASAP and replaced with separately stored objects for rating messages. Storing growing lists of objects in a member variable is wrong design anyway.
 * 
 * TODO: Change all code which queries for identities to use the lowest possible activation depth to benefit from automatic activation.
 * 
 * @author xor (xor@freenetproject.org)
 */
public class WoTOwnIdentity extends WoTIdentity implements FTOwnIdentity {
	
	/* Attributes, stored in the database. */

	private final FreenetURI mInsertURI;

	private final Map<String, Boolean> mAssessed;

    /** If true then auto-subscribe to boards that were subscribed in the NNTP client */
    private boolean mNntpAutoSubscribeBoards;
    
	
	/** Get a list of fields which the database should create an index on. */
	static {
		/* FIXME: Figure out whether indexed fields are inherited from parent classes. Otherwise we would have to also list the indexed fields
		 * of WoTIdentity here. */
		registerIndexedFields(WoTOwnIdentity.class, new String[] { }); 
	}

	public WoTOwnIdentity(String myID, FreenetURI myRequestURI, FreenetURI myInsertURI, String myNickname) {
		super(myID, myRequestURI, myNickname);
		if(myInsertURI == null)
			throw new IllegalArgumentException();
		mInsertURI = myInsertURI;
		mAssessed = new TreeMap<String, Boolean>();
	}

	public FreenetURI getInsertURI() {
		checkedActivate(3); // String[] is no nested object to db4o so 3 is sufficient.
		return mInsertURI;
	}

	public void setAssessed(Message message, boolean assessed) {
		mAssessed.put(message.getID(), new Boolean(assessed) );
	}

	public boolean getAssessed(Message message) {
		if(!mAssessed.containsKey(message.getID())) {
			return false;
		}
		return mAssessed.get(message.getID()).booleanValue();
	}

	public boolean wantsMessagesFrom(FTIdentity identity) throws Exception {
		if(!(identity instanceof WoTIdentity))
			throw new IllegalArgumentException();
		
		try {
			return getScoreFor((WoTIdentity)identity) >= 0;	/* FIXME: this has to be configurable */
		}
		catch(NotInTrustTreeException e) {
			return false;
		}
	}

	public int getScoreFor(WoTIdentity identity) throws NotInTrustTreeException, Exception {
		return mFreetalk.getIdentityManager().getScore(this, identity);
	}

	public int getTrustIn(WoTIdentity identity) throws NotTrustedException, Exception {
		return mFreetalk.getIdentityManager().getTrust(this, identity);
	}

	public void setTrust(WoTIdentity identity, int trust, String comment) throws Exception {
		mFreetalk.getIdentityManager().setTrust(this, identity, trust, comment);
	}
	
    /**
     * Checks whether this Identity auto-subscribes to boards subscribed in NNTP client.
     * 
     * @return Whether this Identity auto-subscribes to boards subscribed in NNTP client or not.
     */
    public boolean nntpAutoSubscribeBoards() {
        return mNntpAutoSubscribeBoards;
    }
    
    /**
     * Sets if this Identity auto-subscribes to boards subscribed in NNTP client. 
     */
    public void setNntpAutoSubscribeBoards(boolean nntpAutoSubscribeBoards) {
        mNntpAutoSubscribeBoards = nntpAutoSubscribeBoards;
    }
    
	public void storeWithoutCommit() {
		try {
			// 3 is the maximal depth of all getter functions. You have to adjust this when changing the set of member variables.
			checkedActivate(3);
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !

			checkedStore(mInsertURI);
			checkedStore(mAssessed);
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	protected void deleteWithoutCommit() {	
		try {
			// super.deleteWithoutCommit() does the following already so there is no need to do it here
			// 3 is the maximal depth of all getter functions. You have to adjust this when changing the set of member variables.
			// DBUtil.checkedActivate(db, this, 3);
			
			super.deleteWithoutCommit();
			
			checkedDelete(mAssessed);
			mInsertURI.removeFrom(mDB);
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
}
