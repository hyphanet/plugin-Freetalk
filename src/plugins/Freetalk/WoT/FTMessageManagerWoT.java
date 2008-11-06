/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.NoSuchElementException;

import plugins.Freetalk.FTBoard;
import plugins.Freetalk.FTMessageManager;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;
import freenet.support.Executor;
import freenet.support.UpdatableSortedLinkedListKilledException;

public class FTMessageManagerWoT extends FTMessageManager {
	
	protected FTIdentityManagerWoT mIdentityManager;

	public FTMessageManagerWoT(ObjectContainer myDB, Executor myExecutor, FTIdentityManagerWoT myIdentityManager) {
		super(myDB, myExecutor, myIdentityManager);
		mIdentityManager = myIdentityManager;
	}

	protected synchronized void deleteMessage(FreenetURI uri) throws NoSuchElementException {
		/* FIXME: implement */
	}

	private synchronized void onMessageReceived(String newMessageData) throws UpdatableSortedLinkedListKilledException { 
		FTMessageWoT newMessage = new FTMessageWoT(db, null, null, null, null, null, null, null, null, null);
		String boardName = "";
		/* FIXME: Store the description in FTOwnIdentity. We cannot store in FTBoard because we want to allow per-identity customization */

		String[] boardNames = new String[0];
		FTBoard[] boards = new FTBoard[boardNames.length];
		                                    
		for(int idx = 0; idx < boards.length; ++idx) {
			FTBoard board = getBoardByName(boardNames[idx]);
			
			if(board == null)
				board = new FTBoard(db, this, boardName);
			
			boards[idx] = board;
		}
		
		for(FTBoard b : boards) {
			b.addMessage(newMessage);
		}
	}
	
	public void run() {
		
	}
}
