/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import plugins.FMSPlugin.FMSBoard;
import plugins.FMSPlugin.FMSIdentity;
import plugins.FMSPlugin.FMSMessage;
import plugins.FMSPlugin.FMSOwnIdentity;
import plugins.WoT.OwnIdentity;
import freenet.keys.FreenetURI;

/**
 * @author xor
 *
 */
public class FTOwnIdentityWoT extends FMSIdentityWoT implements FMSOwnIdentity {
	
	private final LinkedList<FMSBoard> mSubscribedBoards = new LinkedList<FMSBoard>();

	public FTOwnIdentityWoT(OwnIdentity newIndentity) {
		super(newIndentity);
	}
	
	protected OwnIdentity getOwnIdentity() {
		return (OwnIdentity)mIdentity;
	}

	public FreenetURI getInsertURI() {
		return getOwnIdentity().getInsertURI();
	}

	public synchronized Date getLastInsert() {
		return getOwnIdentity().getLastInsert();
	}

	public synchronized void postMessage(FMSMessage message) {
		// TODO Auto-generated method stub
		
	}

	public synchronized void subscribeToBoard(FMSBoard board) {
		if(mSubscribedBoards.contains(board)) {
			assert(false); /* TODO: Add logging / check whether this should be allowed to happen */
			return;
		}
		mSubscribedBoards.add(board);
	}

	public synchronized void unsubscribeFromBoard(FMSBoard board) {
		mSubscribedBoards.remove(board);
	}
	
	public synchronized Iterator<FMSBoard> subscribedBoardsIterator() {
		return mSubscribedBoards.iterator();
	}

	public synchronized boolean wantsMessagesFrom(FMSIdentity identity) {
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
