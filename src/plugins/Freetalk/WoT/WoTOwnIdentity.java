/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import plugins.Freetalk.Board;
import plugins.Freetalk.DBUtil;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import plugins.Freetalk.exceptions.NotTrustedException;
import freenet.keys.FreenetURI;

/**
 * @author xor
 *
 */
public class WoTOwnIdentity extends WoTIdentity implements FTOwnIdentity {
	
	/* Attributes, stored in the database. */

	private final LinkedList<Board> mSubscribedBoards = new LinkedList<Board>();

	private final FreenetURI mInsertURI;

	private final Map<String, Boolean> mAssessed;

	/** Get a list of fields which the database should create an index on. */
	public static String[] getIndexedFields() {
		/* FIXME: Figure out whether indexed fields are inherited from parent classes. Otherwise we would have to also list the indexed fields
		 * of WoTIdentity here. */
		return new String[] {  }; 
	}

	public WoTOwnIdentity(String myID, FreenetURI myRequestURI, FreenetURI myInsertURI, String myNickname) {
		super(myID, myRequestURI, myNickname);
		if(myInsertURI == null)
			throw new IllegalArgumentException();
		mInsertURI = myInsertURI;
		mAssessed = new TreeMap<String, Boolean>();
	}

	public FreenetURI getInsertURI() {
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

	public synchronized void subscribeToBoard(Board board) {
		if(mSubscribedBoards.contains(board)) {
			assert(false); /* TODO: Add logging / check whether this should be allowed to happen */
			return;
		}
		mSubscribedBoards.add(board);
		
		storeWithoutCommit();
	}

	public synchronized void unsubscribeFromBoard(Board board) {
		mSubscribedBoards.remove(board);
		
		storeWithoutCommit();
	}

	public synchronized Iterator<Board> subscribedBoardsIterator() {
		return mSubscribedBoards.iterator();
	}

	public boolean wantsMessagesFrom(FTIdentity identity) {
		if(!(identity instanceof WoTIdentity))
			throw new IllegalArgumentException();
		
		try {
			return getScoreFor((WoTIdentity)identity) >= 0;	/* FIXME: this has to be configurable */
		}
		catch(Exception e) {
			return false;
		}
	}

	public int getScoreFor(WoTIdentity identity) throws NotInTrustTreeException, Exception {
		return mIdentityManager.getScore(this, identity);
	}

	public int getTrustIn(WoTIdentity identity) throws NotTrustedException, Exception {
		return mIdentityManager.getTrust(this, identity);
	}

	public void setTrust(WoTIdentity identity, int trust, String comment) throws Exception {
		mIdentityManager.setTrust(this, identity, trust, comment);
	}
	
	public void storeWithoutCommit() {
		try {
			DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !

			// TODO: As soon as we have a unit test which checks whether the content of subscribed boards gets stored, specify a depth here. Probably 1
			db.store(mSubscribedBoards);
			db.store(mInsertURI);
			db.store(mAssessed);
			super.storeWithoutCommit();
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(db, this, e);
		}
	}

	protected void deleteWithoutCommit() {	
		try {
			// super.deleteWithoutCommit() does the following already so there is no need to do it here
			// DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
			
			super.deleteWithoutCommit();
			
			DBUtil.checkedDelete(db, mAssessed);
			mInsertURI.removeFrom(db);
			DBUtil.checkedDelete(db, mSubscribedBoards);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(db, this, e);
		}
	}
}
