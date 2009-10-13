/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;
import java.util.Iterator;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.clients.http.RedirectException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Shows the boards to which the logged-in {@link FTOwnIdentity} has subscribed to.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class BoardsPage extends WebPageImpl {

	public BoardsPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
	}

	public final void make() throws RedirectException {
		if(mOwnIdentity == null)
			throw new RedirectException(logIn);

		makeBreadcrumbs();
		makeBoardsList();
	}

	private void makeBoardsList() {
		HTMLNode boardsBox = addContentBox("Your boards");

		HTMLNode newBoardForm = addFormChild(boardsBox, Freetalk.PLUGIN_URI + "/NewBoard", "NewBoardPage");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "New board" });
		

		HTMLNode boardsTable = boardsBox.addChild("table", "border", "0");
		HTMLNode row = boardsTable.addChild("tr");
		row.addChild("th", "Name");
		row.addChild("th", "Description");
		row.addChild("th", "Messages");
		row.addChild("th", "Latest message");
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		int boardCount = 0;
		
		synchronized(mFreetalk.getMessageManager()) {
			Iterator<SubscribedBoard> boards = mFreetalk.getMessageManager().subscribedBoardIterator(mOwnIdentity);
			while(boards.hasNext()) {
				++boardCount;
				
				SubscribedBoard board = boards.next();
				row = boardsTable.addChild("tr");

				HTMLNode nameCell = row.addChild("th", new String[] { "align" }, new String[] { "left" });
				nameCell.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/showBoard?identity=" + mOwnIdentity.getID() + "&name=" + board.getName(),
						board.getName()));

				/* Description */
				row.addChild("td", new String[] { "align" }, new String[] { "center" },  board.getDescription());

				/* Message count */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, Integer.toString(board.messageCount()));
				
				/* Date of latest message */
				String latestMessageDate;
				try {
					latestMessageDate = dateFormat.format(board.getLatestMessageDate());
				} catch(NoSuchMessageException e) {
					latestMessageDate = "-";
				}
				
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, latestMessageDate);
			}
		}

		if(boardCount == 0) {
			boardsBox.addChild("p", "You are not subscribed to any boards. Please ")
				.addChild("a", "href", Freetalk.PLUGIN_URI + "/SelectBoards?identity=" + mOwnIdentity.getID())
				.addChild("#", "select which ones you want to read.");
		} else {
			boardsBox.addChild("p", "You can subscribe to more boards ")
				.addChild("a", "href", Freetalk.PLUGIN_URI + "/SelectBoards?identity=" + mOwnIdentity.getID())
				.addChild("#", "here.");			
		}
	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail();
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail) {
		trail.addBreadcrumbInfo("Boards", Freetalk.PLUGIN_URI + "/SubscribedBoards");
	}
}
