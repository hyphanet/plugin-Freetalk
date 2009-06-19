/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Iterator;
import java.util.LinkedList;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

/**
 * @author xor
 *
 */
public class WoTOwnIdentity extends WoTIdentity implements FTOwnIdentity {
	
	/* Attributes, stored in the database. */

	private final LinkedList<Board> mSubscribedBoards = new LinkedList<Board>();

	private final FreenetURI mInsertURI;

	/** Get a list of fields which the database should create an index on. */
	public static String[] getIndexedFields() {
		/* FIXME: Figure out whether indexed fields are inherited from parent classes. Otherwise we would have to also list the indexed fields
		 * of WoTIdentity here. */
		/* FIXME: Figure out whether we really need lookups by insert URI. If not, remove the index */
		return new String[] { "mInsertURI" }; 
	}

	public WoTOwnIdentity(String myUID, FreenetURI myRequestURI, FreenetURI myInsertURI, String myNickname) {
		super(myUID, myRequestURI, myNickname);
		if(myInsertURI == null)
			throw new IllegalArgumentException();
		mInsertURI = myInsertURI;
	}

	public FreenetURI getInsertURI() {
		return mInsertURI;
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

	public synchronized boolean wantsMessagesFrom(FTIdentity identity) {
		return getScoreFor(identity) >= 0;	/* FIXME: this has to be configurable */
	}

	public int getScoreFor(FTIdentity identity) {
		return mIdentityManager.getScore(this, identity);
	}
	
	public void storeWithoutCommit() {
		/* FIXME: check for duplicates */

			try {
				if(db.ext().isStored(this) && !db.ext().isActive(this))
					throw new RuntimeException("Trying to store a non-active WoTOwnIdentity object");

				db.store(mSubscribedBoards); // FIXME: Is this correct??
				db.store(mInsertURI);
				super.storeWithoutCommit();
			}
			catch(RuntimeException e) {
				db.rollback(); Logger.error(this, "ROLLED BACK!", e);
				throw e;
			}
	}

	protected void deleteWithoutCommit() {
		if(db.ext().isStored(this) && !db.ext().isActive(this))
			throw new RuntimeException("Trying to delete a non-active WoTIdentity object");
		
		/* FIXME: We also need to check whether the member objects are active here!!! */
		
		try {
			mInsertURI.removeFrom(db);
			db.delete(mSubscribedBoards); // FIXME: Is this correct??
			super.deleteWithoutCommit();
		}
		catch(RuntimeException e) {
			db.rollback(); Logger.debug(this, "ROLLED BACK!");
			throw e;
		}
	}
}
