/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.NoSuchElementException;

import plugins.FMSPlugin.FMSBoard;
import plugins.FMSPlugin.FMSMessageManager;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;
import freenet.support.UpdatableSortedLinkedListKilledException;

public class FTMessageManagerWoT extends FMSMessageManager {
	
	protected FMSIdentityManagerWoT mIdentityManager;

	public FTMessageManagerWoT(ObjectContainer myDB, FMSIdentityManagerWoT myIdentityManager) {
		super(myDB, myIdentityManager);
		mIdentityManager = myIdentityManager;
	}

	protected synchronized void deleteMessage(FreenetURI uri) throws NoSuchElementException {
		/* FIXME: implement */
	}

	private synchronized void onMessageReceived(String newMessageData) throws UpdatableSortedLinkedListKilledException { 
		FMSMessageWoT newMessage = new FMSMessageWoT(db, null, null, null, null, null, null, null, null, null);
		String boardName = "";
		/* FIXME: Store the description in FMSOwnIdentity. We cannot store in FMSBoard because we want to allow per-identity customization */

		String[] boardNames = new String[0];
		FMSBoard[] boards = new FMSBoard[boardNames.length];
		                                    
		for(int idx = 0; idx < boards.length; ++idx) {
			FMSBoard board = getBoardByName(boardNames[idx]);
			
			if(board == null)
				board = new FMSBoard(db, this, boardName);
			
			boards[idx] = board;
		}
		
		for(FMSBoard b : boards) {
			b.addMessage(newMessage);
		}
	}
}
