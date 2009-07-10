/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.BoardMessageLink;
import plugins.Freetalk.Board.BoardThreadLink;
import plugins.Freetalk.WoT.WoTOwnIdentity;
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
			row.addChild("th", "Trust");
			row.addChild("th", "Date");
			row.addChild("th", "Replies");
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		HTMLNode table = threadsTable.addChild("tbody");
		
		synchronized(mBoard) {
			for(BoardThreadLink threadReference : mBoard.getThreads(mOwnIdentity)) {
				Message thread = threadReference.getMessage();

				row = table.addChild("tr");
				
				String threadTitle = "UNKNOWN";
				if(thread != null)
					threadTitle = thread.getTitle();
				else {
					// The thread was not downloaded yet, we use the title of it's first reply as it's title.
					for(BoardMessageLink messageRef : mBoard.getAllThreadReplies(threadReference.getThreadID(), true)) {
						threadTitle = messageRef.getMessage().getTitle();
						break;
					}
				}
				threadTitle = maxLength(threadTitle, 40); // TODO: Adjust

				HTMLNode titleCell = row.addChild("td", new String[] { "align" }, new String[] { "left" });
				titleCell.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/showThread?identity=" + mOwnIdentity.getUID() + 
						"&board=" + mBoard.getName() + "&id=" + threadReference.getThreadID(), threadTitle));

				/* Author */
            	// FIXME: The author can be reconstructed from the thread id because it contains the id of the author. We just need to figure out
            	// what the proper place for a function "getIdentityIDFromThreadID" is and whether I have already written one which can do that, and if
            	// yes, where it is.
				String authorText = thread != null ? thread.getAuthor().getShortestUniqueName(30) : "UNKNOWN";
				row.addChild("td", new String[] { "align" }, new String[] { "left" }, authorText);

				/* Trust */
				int score; 
				try {
					if(thread != null)
						// TODO: Get rid of the cast somehow, we should maybe call this WoTBoardPage :|
						score = ((WoTOwnIdentity)mOwnIdentity).getScoreFor(thread.getAuthor());
					else
						score = -1;
				}
				catch(NumberFormatException e) { // FIXME: this is a bug...
					score = -1;
				}
				row.addChild("td", new String[] { "align" }, new String[] { "left" }, 
						score > 0 ? Integer.toString(score) : "UNKNOWN");

				/* Date of last reply */
				row.addChild("td", new String[] { "align" , "style" }, new String[] { "center" , "white-space:nowrap;"}, 
						dateFormat.format(threadReference.getLastReplyDate()));

				/* Reply count */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, 
						Integer.toString(mBoard.threadReplyCount(mOwnIdentity, threadReference.getThreadID())));
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
