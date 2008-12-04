/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Iterator;
import java.util.LinkedList;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.FTOwnIdentity;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;

/**
 * @author xor
 *
 */
public class WoTOwnIdentity extends WoTIdentity implements FTOwnIdentity {
	
	/* Attributes, stored in the database. */

	private final LinkedList<Board> mSubscribedBoards = new LinkedList<Board>();

	private final FreenetURI mInsertURI;
	
	
	/* References to objects of the plugin, not stored in the database */
	
	private transient ObjectContainer db;
	
	public WoTOwnIdentity(String myUID, FreenetURI myRequestURI, FreenetURI myInsertURI, String myNickname) {
		super(myUID, myRequestURI, myNickname);
		if(myInsertURI == null)
			throw new IllegalArgumentException();
		mInsertURI = myInsertURI;
	}
	
	/**
	 * Has to be used after loading a FTOwnIdentityWoT object from the database to initialize the transient fields.
	 */
	public void initializeTransient(ObjectContainer myDB) {
		assert(myDB != null);
		db = myDB;
	}
	
	public FreenetURI getInsertURI() {
		return mInsertURI;
	}

	public synchronized void postMessage(Message message) {
		// TODO Auto-generated method stub
		
	}

	public synchronized void subscribeToBoard(Board board) {
		if(mSubscribedBoards.contains(board)) {
			assert(false); /* TODO: Add logging / check whether this should be allowed to happen */
			return;
		}
		mSubscribedBoards.add(board);
		
		store();
	}

	public synchronized void unsubscribeFromBoard(Board board) {
		mSubscribedBoards.remove(board);
		
		store();
	}
	
	public synchronized Iterator<Board> subscribedBoardsIterator() {
		return mSubscribedBoards.iterator();
	}

	public synchronized boolean wantsMessagesFrom(FTIdentity identity) {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	public final void exportXML(OutputStream out) throws IOException {
		Writer w = new BufferedWriter(new OutputStreamWriter(out));
		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		w.write("<Identity\n");
		w.write("\t<Name><![CDATA[");
		XMLUtils.writeEsc(w, getNickName());
		w.write("]]></Name>\n");
		
		w.write("\t<SingleUse>false</SingleUse>\n");
		w.write("\t<PublishTrustList>false</PublishTrustList>\n");
		w.write("\t<PublishBoardList>false</PublishBoardList>\n");

		w.write("<Identity\n");
		w.flush();
		w.close();
	}
	*/
}
