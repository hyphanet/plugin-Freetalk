package plugins.Freetalk.ui.web;

import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class BoardsPage extends WebPageImpl {

	public BoardsPage(Freetalk ft, HTTPRequest request) {
		super(ft, request);
		// TODO Auto-generated constructor stub
	}

	public void make() {
		makeBoardsList();
	}

	private void makeBoardsList() {
		HTMLNode boardsBox = getContentBox("Boards");
		
		// Display the list of known identities
		HTMLNode boardsTable = boardsBox.addChild("table", "border", "0");
		HTMLNode row = boardsTable.addChild("tr");
		row.addChild("th", "Name");
		row.addChild("th", "Description");
		row.addChild("th", "Messages");
		
		Iterator<Board> boards = mFreetalk.getMessageManager().boardIterator();
		while(boards.hasNext()) {
			Board board = boards.next();
			row = boardsTable.addChild("tr");
			
			HTMLNode nameCell = row.addChild("th", new String[] { "align" }, new String[] { "left" });
			nameCell.addChild(new HTMLNode("a", "href", SELF_URI + "?showBoard&name=" + board.getName(), board.getName()));

			/* Description */
			row.addChild("td", new String[] { "align" }, new String[] { "center" }, "not implemented yet");
	
			/* Message count */
			row.addChild("td", new String[] { "align" }, new String[] { "center" }, Integer.toString(board.messageCount()));
		}
	}

}
