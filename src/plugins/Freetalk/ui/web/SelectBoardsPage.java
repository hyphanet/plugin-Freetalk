package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.l10n.ISO639_3;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * Shows a list of all known boards and allows the user to subscribe to them or unsubscribe from subscribed boards. 
 * 
 * @author xor (xor@freenetproject.org)
 */
public class SelectBoardsPage extends WebPageImpl {

	public SelectBoardsPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) {
		super(myWebInterface, viewer, request, _baseL10n);
	}

	@Override public void make() throws RedirectException {
		if(mOwnIdentity == null) {
			throw new RedirectException(logIn);
		}

		makeBreadcrumbs();
		
		boolean subscribe = mRequest.isPartSet("Subscribe");
		boolean unsubscribe = mRequest.isPartSet("Unsubscribe");
		
		if((subscribe ^ unsubscribe) && mRequest.getMethod().equals("POST")) {
			String boardName = mRequest.getPartAsStringFailsafe("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH);
		
			try {
				MessageManager messageManager = mFreetalk.getMessageManager();
				
				if(subscribe) {
					SubscribedBoard board = messageManager.subscribeToBoard(mOwnIdentity, boardName);

					HTMLNode successBox = addContentBox(l10n().getString("SelectBoardsPage.SubscriptionSucceededBox.Header"));
	                l10n().addL10nSubstitution(
	                        successBox.addChild("div"), 
	                        "SelectBoardsPage.SubscriptionSucceededBox.Text",
	                        new String[] { "link", "boardname" }, 
	                        new HTMLNode[] { HTMLNode.link(BoardPage.getURI(board)), HTMLNode.text(board.getName()) });
				}
				else if(unsubscribe) {
					messageManager.unsubscribeFromBoard(mOwnIdentity, boardName);
					
                    HTMLNode successBox = addContentBox(l10n().getString("SelectBoardsPage.UnsubscriptionSucceededBox.Header"));
                    l10n().addL10nSubstitution(
                        successBox.addChild("div"), 
                        "SelectBoardsPage.UnsubscriptionSucceededBox.Text",
                        new String[] { "boardname" }, 
                        new HTMLNode[] { HTMLNode.text(boardName) });
				}	
			} catch(Exception e) {
				HTMLNode alertBox = addAlertBox(subscribe ? l10n().getString("SelectBoardsPage.SubscribeFailed") : l10n().getString("SelectBoardsPage.UnsubscribeFailed"));
				alertBox.addChild("div", e.getMessage());
				
				Logger.error(this, subscribe ? "subscribe failed" : "unsubscribe failed", e);
			}
		}
		
		makeBoardsList();
	}
	
	private void makeBoardsList() {
		HTMLNode boardsBox = addContentBox(l10n().getString("SelectBoardsPage.SelectBoardsBox.Header"));
		boardsBox = boardsBox.addChild("div", "class", "select-boards");
		
		boardsBox.addChild("p", l10n().getString("SelectBoardsPage.SelectBoardsBox.Text"));
		
		HTMLNode buttonRow = boardsBox.addChild("div", "class", "button-row");
		
		HTMLNode buttonDiv = buttonRow.addChild("div", "class", "button-row-button");
		HTMLNode newBoardForm = addFormChild(buttonDiv, Freetalk.PLUGIN_URI + "/NewBoard", "NewBoardPage");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("SelectBoardsPage.NewBoardButton") });
		
		buttonDiv = buttonRow.addChild("div", "class", "button-row-button");
		HTMLNode deleteEmptyBoardsForm = addFormChild(buttonDiv, Freetalk.PLUGIN_URI + "/DeleteEmptyBoards", "DeleteEmptyBoards");
		deleteEmptyBoardsForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		deleteEmptyBoardsForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("SelectBoardsPage.SelectBoardsBox.DeleteEmptyBoardsButton") });
		
		HTMLNode buttonDiv2 = buttonRow.addChild("div", "class", "button-row-button");
		HTMLNode languageFilterForm = addFormChild(buttonDiv2, Freetalk.PLUGIN_URI + "/SelectBoards", "SelectBoardsPage");
		languageFilterForm.addChild(NewBoardPage.getLanguageComboBox("mul"));
		languageFilterForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("SelectBoardsPage.FilterButton") });
		
        // Clear margins after button row. TODO: Refactoring: Move to CSS
        boardsBox.addChild("div", "style", "clear: both;");
		
		HTMLNode boardsTable = boardsBox.addChild("table", "class", "boards-table");
		HTMLNode row = boardsTable.addChild("tr");
		row.addChild("th", l10n().getString("SelectBoardsPage.BoardTableHeader.Language"));
		row.addChild("th", l10n().getString("SelectBoardsPage.BoardTableHeader.Name"));
		row.addChild("th", l10n().getString("SelectBoardsPage.BoardTableHeader.Description"));
		row.addChild("th", l10n().getString("SelectBoardsPage.BoardTableHeader.FirstSeen"));
		row.addChild("th", l10n().getString("SelectBoardsPage.BoardTableHeader.LatestMessage"));
		row.addChild("th", l10n().getString("SelectBoardsPage.BoardTableHeader.Messages"));
		row.addChild("th", l10n().getString("SelectBoardsPage.BoardTableHeader.Subscribe"));
		row.addChild("th", l10n().getString("SelectBoardsPage.BoardTableHeader.Unsubscribe"));
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		MessageManager messageManager = mFreetalk.getMessageManager(); 
		
		final boolean languageFiltered = mRequest.isPartSet("BoardLanguage");		
		final String languageFilter = mRequest.getPartAsStringFailsafe("BoardLanguage", Board.MAX_BOARDNAME_TEXT_LENGTH);
		final ISO639_3.LanguageCode languageFilterCode = Board.getAllowedLanguages().get(languageFilter);
		
		synchronized(messageManager) {
			for(final Board board : messageManager.boardIteratorSortedByName()) {
				if(languageFiltered && board.getLanguage() != languageFilterCode)
					continue;
				
				row = boardsTable.addChild("tr", "id", board.getName());

				// Language
				
				row.addChild("td", "class", "language-cell", board.getLanguage().referenceName);
				
				// Name
				
				HTMLNode nameCell = row.addChild("th", "class", "name-cell");
				
				//.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/SubscribeToBoard?identity=" + mOwnIdentity.getID() + "&name=" + board.getName(),
				//		board.getName()));

				// Description
				row.addChild("td", "class", "description-cell",  board.getDescription(mOwnIdentity));

				// First seen
				row.addChild("td", "class", "first-seen-cell", dateFormat.format(board.getFirstSeenDate()));
				
				// Latest message
				HTMLNode latestMessageCell = row.addChild("td", "class", "latest-message-cell");
				
				// Message count
				HTMLNode messageCountCell = row.addChild("td", "class", "message-count-cell");

				HTMLNode subscribeCell = row.addChild("td", new String[] { "align" }, new String[] { "center" });
				HTMLNode unsubscribeCell = row.addChild("td", new String[] { "align" }, new String[] { "center" });
				
				try {
					SubscribedBoard subscribedBoard = messageManager.getSubscription(mOwnIdentity, board.getName());
					
					// We are subscribed to that board so we can display some more information.
					
					nameCell.addChild(new HTMLNode("a", "href", BoardPage.getURI(board), board.getName()));
					
					try {
						latestMessageCell.addChild("#", dateFormat.format(subscribedBoard.getLatestMessage().getMessageDate()));
					} catch(NoSuchMessageException e) {
						latestMessageCell.addChild("#", "-");
					}
					
					messageCountCell.addChild("#", Integer.toString(subscribedBoard.messageCount()));
					
					HTMLNode unsubscribeForm = addFormChild(unsubscribeCell, Freetalk.PLUGIN_URI + "/SelectBoards" + "#" + board.getName(), "Unsubscribe");
					unsubscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "OwnIdentityID", mOwnIdentity.getID()});
					unsubscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "BoardName", board.getName()});
					if(languageFiltered) unsubscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "BoardLanguage", languageFilter}); 
					unsubscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "submit", "Unsubscribe", l10n().getString("SelectBoardsPage.BoardTable.UnsubscribeButton") });
				} catch(NoSuchBoardException e) {
					// We are not subscribed to that board so we cannot fill all cells with information.
					
					nameCell.addChild(new HTMLNode("a", "href", NotFetchedMessagesPage.getURI(board), board.getName()));
					
					latestMessageCell.addChild("#", "-");
					messageCountCell.addChild("#", l10n().getString("Common.EstimationPrefix") + " " + messageManager.getDownloadableMessageCount(board));
					
					HTMLNode subscribeForm = addFormChild(subscribeCell, Freetalk.PLUGIN_URI + "/SelectBoards" + "#" + board.getName(), "Subscribe");
					subscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "OwnIdentityID", mOwnIdentity.getID()});
					subscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "BoardName", board.getName()});
					if(languageFiltered) subscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "BoardLanguage", languageFilter});
					subscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "submit", "Subscribe", l10n().getString("SelectBoardsPage.BoardTable.SubscribeButton") });
				}
			}
		}
	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		SelectBoardsPage.addBreadcrumb(trail);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail) {
		trail.addBreadcrumbInfo(trail.getL10n().getString("Breadcrumb.SelectBoards"), Freetalk.PLUGIN_URI + "/SelectBoards");
	}
	
	public static String getURI() {
		return Freetalk.PLUGIN_URI + "/SelectBoards";
	}
}
