/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor
 */
public final class BoardsPage extends WebPageImpl {

	public BoardsPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
		// TODO Auto-generated constructor stub
	}

	public final void make() {
		if(mOwnIdentity != null)
			makeBoardsList();
		else
			addContentBox("No own identity received yet").addChild("#", "Freetalk has not downloaded your own identities yet from the WoT plugin. Please wait a few minutes.");
	}

	private void makeBoardsList() {
		HTMLNode boardsBox = addContentBox("Boards");
		
		HTMLNode newBoardForm = addFormChild(boardsBox, SELF_URI + "/NewBoard", "NewBoardPage");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "New board" });
		
		// Display the list of known identities
		HTMLNode boardsTable = boardsBox.addChild("table", "border", "0");
		HTMLNode row = boardsTable.addChild("tr");
		row.addChild("th", "Name");
		row.addChild("th", "Description");
		row.addChild("th", "Messages");
		
		/* FIXME: Currently we show all boards. We should rather show the boards which the identity has selected */
		synchronized(mFreetalk.getMessageManager()) {
		Iterator<Board> boards = mFreetalk.getMessageManager().boardIterator();
		while(boards.hasNext()) {
			Board board = boards.next();
			row = boardsTable.addChild("tr");
			
			HTMLNode nameCell = row.addChild("th", new String[] { "align" }, new String[] { "left" });
			nameCell.addChild(new HTMLNode("a", "href", SELF_URI + "/showBoard?identity=" + mOwnIdentity.getUID() + "&name=" + board.getName(), board.getName()));

			/* Description */
			row.addChild("td", new String[] { "align" }, new String[] { "center" }, "not implemented yet");
	
			/* Message count */
			row.addChild("td", new String[] { "align" }, new String[] { "center" }, Integer.toString(board.messageCount()));
		}
		}
	}

}
