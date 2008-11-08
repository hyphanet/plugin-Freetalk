/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import com.db4o.ObjectContainer;

import plugins.Freetalk.FTBoard;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTMessage;
import plugins.Freetalk.FTOwnIdentity;
import plugins.WoT.OwnIdentity;
import freenet.keys.FreenetURI;

/**
 * @author xor
 *
 */
public class FTOwnIdentityWoT extends FTIdentityWoT implements FTOwnIdentity {

	private final LinkedList<FTBoard> mSubscribedBoards = new LinkedList<FTBoard>();

	private final FreenetURI mInsertURI;
	
	public FTOwnIdentityWoT(ObjectContainer myDB, String myUID, FreenetURI myRequestURI, FreenetURI myInsertURI, String myNickname) {
		super(myDB, myUID, myRequestURI, myNickname);
		if(myInsertURI == null)
			throw new IllegalArgumentException();
		mInsertURI = myInsertURI;
	}
	
	public FreenetURI getInsertURI() {
		return mInsertURI;
	}

	public synchronized void postMessage(FTMessage message) {
		// TODO Auto-generated method stub
		
	}

	public synchronized void subscribeToBoard(FTBoard board) {
		if(mSubscribedBoards.contains(board)) {
			assert(false); /* TODO: Add logging / check whether this should be allowed to happen */
			return;
		}
		mSubscribedBoards.add(board);
		
		store();
	}

	public synchronized void unsubscribeFromBoard(FTBoard board) {
		mSubscribedBoards.remove(board);
		store();
	}
	
	public synchronized Iterator<FTBoard> subscribedBoardsIterator() {
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
