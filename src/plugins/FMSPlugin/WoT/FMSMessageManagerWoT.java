/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.WoT;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.UpdatableSortedLinkedList;
import freenet.support.UpdatableSortedLinkedListKilledException;
import freenet.support.UpdatableSortedLinkedListWithForeignIndex;

import plugins.FMSPlugin.FMSBoard;
import plugins.FMSPlugin.FMSIdentityManager;
import plugins.FMSPlugin.FMSMessage;
import plugins.FMSPlugin.FMSMessageManager;
import plugins.FMSPlugin.FMSOwnIdentity;
import plugins.WoT.Identity;
import plugins.WoT.exceptions.DuplicateIdentityException;
import plugins.WoT.exceptions.UnknownIdentityException;

public class FMSMessageManagerWoT extends FMSMessageManager {
	
	protected FMSIdentityManagerWoT mIdentityManager;

	public FMSMessageManagerWoT(ObjectContainer myDB, FMSIdentityManagerWoT myIdentityManager) {
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
