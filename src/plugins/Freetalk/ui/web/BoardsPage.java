/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.MessageReference;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Shows the boards to which the logged-in {@link OwnIdentity} has subscribed to.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class BoardsPage extends WebPageImpl {

	public BoardsPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) {
		super(myWebInterface, viewer, request, _baseL10n);
	}

	public final void make() throws RedirectException {
		if(mOwnIdentity == null)
			throw new RedirectException(logIn);

		makeBreadcrumbs();
		makeBoardsList();
	}

	private void makeBoardsList() {
		HTMLNode boardsBox = addContentBox(l10n().getString("BoardsPage.BoardList.Header"));

		HTMLNode newBoardForm = addFormChild(boardsBox, Freetalk.PLUGIN_URI + "/NewBoard", "NewBoardPage");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("BoardsPage.NewBoardButton")});

		HTMLNode boardsTable = boardsBox.addChild("table", "border", "0");
		HTMLNode row = boardsTable.addChild("tr");
		row.addChild("th", l10n().getString("BoardsPage.BoardTableHeader.Language"));
		row.addChild("th", l10n().getString("BoardsPage.BoardTableHeader.Name"));
		row.addChild("th", l10n().getString("BoardsPage.BoardTableHeader.Description"));
		row.addChild("th", l10n().getString("BoardsPage.BoardTableHeader.Messages"));
        row.addChild("th", l10n().getString("BoardsPage.BoardTableHeader.UnreadMessages"));
		row.addChild("th", l10n().getString("BoardsPage.BoardTableHeader.LatestMessage"));
		
		final DateFormat dateFormat = DateFormat.getInstance();
		
		int boardCount = 0;
		
		synchronized(mFreetalk.getMessageManager()) {
			for(final SubscribedBoard board : mFreetalk.getMessageManager().subscribedBoardIteratorSortedByName(mOwnIdentity)) { 
				++boardCount;
				
				row = boardsTable.addChild("tr");
				
				/* Language */
				row.addChild("td", new String[] { "align" }, new String[] { "left" }, board.getLanguage().referenceName);

				/* Name */
				
				HTMLNode nameCell = row.addChild("th", new String[] { "align" }, new String[] { "left" });
				nameCell.addChild(new HTMLNode("a", "href", BoardPage.getURI(board), board.getName()));

				/* Description */
				row.addChild("td", new String[] { "align" }, new String[] { "center" },  board.getDescription());

				/* Message count */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, Integer.toString(board.messageCount()));
				
				/* Count unread messages + find latest message date */
				final int unreadMessageCount = board.getUnreadMessageCount();
				
				MessageReference latestMessage;
				String latestMessageDateString;
				
				try {
					latestMessage = board.getLatestMessage();
					latestMessageDateString = dateFormat.format(latestMessage.getMessageDate());
				} catch (NoSuchMessageException e) {
					latestMessage = null;
			        latestMessageDateString = "-";
				} 
				
				// TODO: This should always be a td, use a CSS class instead with font-weight:bold
				
			    /* Unread messages count, bold when there are unread messages */
	            row.addChild((unreadMessageCount == 0) ? "td" : "th", new String[] { "align" }, new String[] { "center" }, Integer.toString(unreadMessageCount));

			    /* Latest message date, bold when the latest message is unread */
				row.addChild((latestMessage == null || latestMessage.wasRead()) ? "td" : "th", new String[] { "align" }, new String[] { "center" },
						latestMessageDateString);
			}
		}

		final String[] l10nLinkSubstitutionInput = new String[] { "link" };
		// TODO: Create SelectBoardsPage.getURI with the code below... own identity can be removed, its stored in the session cookie
		final HTMLNode[] l10nLinkSubstitutionOutput = new HTMLNode[] { HTMLNode.link(Freetalk.PLUGIN_URI+"/SelectBoards?identity="+mOwnIdentity.getID()) };
		HTMLNode aChild = boardsBox.addChild("p");
		if(boardCount == 0) {
            l10n().addL10nSubstitution(aChild, "BoardsPage.NoBoardSubscribed", l10nLinkSubstitutionInput, l10nLinkSubstitutionOutput);
		} else {
            l10n().addL10nSubstitution(aChild, "BoardsPage.BoardSubscriptions", l10nLinkSubstitutionInput, l10nLinkSubstitutionOutput);
		}
	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail) {
		trail.addBreadcrumbInfo(trail.getL10n().getString("Breadcrumb.Boards"), Freetalk.PLUGIN_URI + "/SubscribedBoards");
	}
}
