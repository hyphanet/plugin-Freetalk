/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Displays the content messages of a board.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class BoardPage extends WebPageImpl {

	private final Board mBoard;
	
	public BoardPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchBoardException {
		super(myWebInterface, viewer, request);
		mBoard = mFreetalk.getMessageManager().getBoardByName(request.getParam("name"));
	}

	public final void make() {
		makeBreadcrumbs();

		HTMLNode threadsBox = addContentBox("Threads in '" + mBoard.getName() + "'");
		
		// Button for creating a new thread
		HTMLNode newThreadForm = addFormChild(threadsBox, Freetalk.PLUGIN_URI + "/NewThread", "NewThreadPage");
			newThreadForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "OwnIdentityID", mOwnIdentity.getUID() });
			newThreadForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "BoardName", mBoard.getName() });
			newThreadForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", "New thread" });
		
		// Threads table
		HTMLNode threadsTable = threadsBox.addChild("table", new String[] { "border", "width" }, new String[] { "0", "100%" });
		
		// Tell the browser the table columns and their size 
		HTMLNode colgroup = threadsTable.addChild("colgroup");
			colgroup.addChild("col", "width", "100%"); // Title, should use as much space as possible, the other columns should have minimal size
			colgroup.addChild("col"); // Author
			colgroup.addChild("col"); // Date
			colgroup.addChild("col"); // Replies
		
		HTMLNode row = threadsTable.addChild("thead");
			row.addChild("th", "Title");
			row.addChild("th", "Author");
			row.addChild("th", "Date");
			row.addChild("th", "Replies");
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		HTMLNode table = threadsTable.addChild("tbody");
		
		synchronized(mBoard) {
			for(MessageReference threadReference : mBoard.getThreads(mOwnIdentity)) {
				Message thread = threadReference.getMessage();

				row = table.addChild("tr");

				HTMLNode titleCell = row.addChild("td", new String[] { "align" }, new String[] { "left" });
				titleCell.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/showThread?identity=" + mOwnIdentity.getUID() + 
						"&board=" + mBoard.getName() + "&id=" + thread.getID(), maxLength(thread.getTitle(), 50)));

				/* Author */
				String authorText = thread.getAuthor().getShortestUniqueName(50);
				row.addChild("td", new String[] { "align" }, new String[] { "left" }, authorText);

				/* Date */
				row.addChild("td", new String[] { "align" , "style" }, new String[] { "center" , "white-space:nowrap;"}, dateFormat.format(thread.getDate()));

				/* Reply count */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, Integer.toString(mBoard.threadReplyCount(mOwnIdentity, thread)));
			}
		}
	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail();
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		BoardPage.addBreadcrumb(trail, mBoard);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail, Board board) {
		trail.addBreadcrumbInfo(board.getName(), Freetalk.PLUGIN_URI + "/showBoard?name=" + board.getName());
	}
}
