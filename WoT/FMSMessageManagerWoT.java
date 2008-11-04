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
	
	protected synchronized boolean shouldDownloadMessage(FreenetURI uri) {
		Query query = db.query();
		query.constrain(FMSMessage.class);
		query.descend("mURI").constrain(uri);
		ObjectSet<FMSMessage> result = query.execute();
		
		if(result.size() > 1) { /* Duplicate messages */
			assert(false);
			/* FIXME: Add logging!*/
			deleteMessage(uri);
			return true;
		}
		
		return (result.size() == 0);
	}
	
	protected synchronized void deleteMessage(FreenetURI uri) throws NoSuchElementException {
		/* FIXME: implement */
	}
	
	private synchronized void onMessageReceived(String newMessageData) throws UpdatableSortedLinkedListKilledException { 
		FMSMessageWoT newMessage = new FMSMessageWoT(null, null, null, null, null, null, null, null, null);
		String boardName = "";
		String boardDescription = "";
		FMSBoard board = getBoardByName(boardName);
		if(board == null) {
			board = new FMSBoard(this, boardName, boardDescription);
			mBoards.add(board);
		}
		
		db.store(newMessage);
		db.commit();
		
		board.addMessage(newMessage);
		db.store(board);
		db.commit();
	}
}
