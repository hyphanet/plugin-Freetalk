package plugins.Freetalk.ui.web;

import java.text.DateFormat;
import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.clients.http.RedirectException;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * Shows a list of all known boards and allows the user to subscribe to them or unsubscribe from subscribed boards. 
 * 
 * @author xor (xor@freenetproject.org)
 */
public class SelectBoardsPage extends WebPageImpl {

	public SelectBoardsPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
		// TODO Auto-generated constructor stub
	}

	public void make() throws RedirectException {
		if(mOwnIdentity == null)
			throw new RedirectException(logIn);

		makeBreadcrumbs();
		
		boolean subscribe = mRequest.isPartSet("Subscribe");
		boolean unsubscribe = mRequest.isPartSet("Unsubscribe");
		
		if(subscribe ^ unsubscribe) {
			String boardName = mRequest.getPartAsString("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH);
		
			try {
				MessageManager messageManager = mFreetalk.getMessageManager();
				
				if(subscribe) {
					SubscribedBoard board = messageManager.subscribeToBoard(mOwnIdentity, boardName);

					HTMLNode successBox = addContentBox("Subscription succeeded");
					successBox.addChild("div", "You are now subscribed to the board ")
						.addChild("u").addChild(
							new HTMLNode("a", "href", 
									Freetalk.PLUGIN_URI + "/showBoard?identity=" + mOwnIdentity.getID() + "&name=" + board.getName(), board.getName()));
				}
				else if(unsubscribe) {
					messageManager.unsubscribeFromBoard(mOwnIdentity, boardName);
					addContentBox("Unsubscription succeeded")
						.addChild("div", "You will no longer receive messages from the board " + boardName);
				}	
			} catch(Exception e) {
				HTMLNode alertBox = addAlertBox(subscribe ? "Subscribing to board failed" : "Unsubscribing from board failed");
				alertBox.addChild("div", e.getMessage());
				
				Logger.error(this, subscribe ? "subscribe failed" : "unsubscribe failed", e);
			}
		}
		
		makeBoardsList();
	}
	
	private void makeBoardsList() {
		HTMLNode boardsBox = addContentBox("Select your boards");
		
		boardsBox.addChild("p", "Notice that it may take some time until Freetalk has discovered all boards!");

		HTMLNode newBoardForm = addFormChild(boardsBox, Freetalk.PLUGIN_URI + "/NewBoard", "NewBoardPage");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "New board" });
		
		HTMLNode boardsTable = boardsBox.addChild("table", "border", "0");
		HTMLNode row = boardsTable.addChild("tr");
		row.addChild("th", "Name");
		row.addChild("th", "Description");
		row.addChild("th", "First seen");
		row.addChild("th", "Latest message");
		row.addChild("th", "Messages");
		row.addChild("th", "Subscribe");
		row.addChild("th", "Unsubscribe");
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		MessageManager messageManager = mFreetalk.getMessageManager(); 
		
		synchronized(messageManager) {
			Iterator<Board> boards = messageManager.boardIterator();
			
			while(boards.hasNext()) {
				Board board = boards.next();
				row = boardsTable.addChild("tr", "id", board.getName());

				// Name
				
				HTMLNode nameCell = row.addChild("th", new String[] { "align" }, new String[] { "left" });
				
				//.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/SubscribeToBoard?identity=" + mOwnIdentity.getID() + "&name=" + board.getName(),
				//		board.getName()));

				// Description
				row.addChild("td", new String[] { "align" }, new String[] { "center" },  board.getDescription(mOwnIdentity));

				// First seen
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, dateFormat.format(board.getFirstSeenDate()));
				
				// Latest message
				HTMLNode latestMessageCell = row.addChild("td", new String[] { "align" }, new String[] { "center" });
				
				// Message count
				HTMLNode messageCountCell = row.addChild("td", new String[] { "align" }, new String[] { "center" });

				HTMLNode subscribeCell = row.addChild("td", new String[] { "align" }, new String[] { "center" });
				HTMLNode unsubscribeCell = row.addChild("td", new String[] { "align" }, new String[] { "center" });
				
				try {
					SubscribedBoard subscribedBoard = messageManager.getSubscription(mOwnIdentity, board.getName());
					
					// We are subscribed to that board so we can display some more information.
					
					nameCell.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/showBoard?identity=" + mOwnIdentity.getID() + "&name=" + board.getName(),
							board.getName()));
					
					try {
						latestMessageCell.addChild("#", dateFormat.format(subscribedBoard.getLatestMessage().getMessageDate()));
					} catch(NoSuchMessageException e) {
						latestMessageCell.addChild("#", "-");
					}
					
					messageCountCell.addChild("#", Integer.toString(subscribedBoard.messageCount()));
					
					HTMLNode unsubscribeForm = addFormChild(unsubscribeCell, Freetalk.PLUGIN_URI + "/SelectBoards" + "#" + board.getName(), "Unsubscribe");
					unsubscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "OwnIdentityID", mOwnIdentity.getID()});
					unsubscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "BoardName", board.getName()});
					unsubscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "submit", "Unsubscribe", "Unsubscribe" });
				} catch(NoSuchBoardException e) {
					// We are not subscribed to that board so we cannot fill all cells with information.
					
					nameCell.addChild("#", board.getName());
					latestMessageCell.addChild("#", "-");
					messageCountCell.addChild("#", "-");
					
					HTMLNode subscribeForm = addFormChild(subscribeCell, Freetalk.PLUGIN_URI + "/SelectBoards" + "#" + board.getName(), "Subscribe");
					subscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "OwnIdentityID", mOwnIdentity.getID()});
					subscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "hidden", "BoardName", board.getName()});
					subscribeForm.addChild("input", new String[] {"type", "name", "value"}, new String[] { "submit", "Subscribe", "Subscribe" });
				}
			}
		}

	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail();
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		SelectBoardsPage.addBreadcrumb(trail);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail) {
		trail.addBreadcrumbInfo("Select your boards", Freetalk.PLUGIN_URI + "/SelectBoards");
	}

}
