/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.WoT;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;

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

public class FMSMessageManagerWoT implements FMSMessageManager {
	
	private ObjectContainer db;
	
	/**
	 * Contains all boards which where found in a message. References to all messages of a board are stored in
	 * the board. Adding a newly downloaded message therefore is done by searching its board and calling 
	 * <code>addMessage()</code> on that board. Further, the message is also added to mMessages, see below.
	 */
	private UpdatableSortedLinkedListWithForeignIndex mBoards = new UpdatableSortedLinkedListWithForeignIndex();

	private ArrayList<FMSOwnIdentityWoT> mOwnIdentites = new ArrayList<FMSOwnIdentityWoT>();
	
	public synchronized FMSMessage get(FreenetURI uri) {
		Query query = db.query();
		query.constrain(FMSMessage.class);
		query.descend("mURI").constrain(uri);
		ObjectSet<FMSMessage> result = query.execute();
		
		return (result.size() == 0) ? null : result.next();
	}

	public synchronized FMSBoard getBoardByName(String name) {
		return (FMSBoard)mBoards.get(name);
	}
	
	public synchronized Iterator<FMSBoard> boardIterator(FMSOwnIdentity identity) {
		return (Iterator<FMSBoard>)mBoards.iterator();
	}
	
	private synchronized boolean shouldDownloadMessage(FreenetURI uri) {
		Query query = db.query();
		query.constrain(FMSMessage.class);
		query.descend("mURI").constrain(uri);
		ObjectSet<FMSMessage> result = query.execute();
		
		assert (result.size() <= 1); /* Duplicate messages */
		
		return (result.size() == 0);
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
