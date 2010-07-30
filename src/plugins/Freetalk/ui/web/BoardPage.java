/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * Displays the content messages of a subscribed board.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class BoardPage extends WebPageImpl {

	private final SubscribedBoard mBoard;
    private final boolean mMarkAllThreadsAsRead;
	
	public BoardPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) throws NoSuchBoardException {
		super(myWebInterface, viewer, request, _baseL10n);
		mBoard = mFreetalk.getMessageManager().getSubscription(viewer, request.getParam("name"));
		mMarkAllThreadsAsRead = mRequest.isPartSet("MarkAllThreadsAsRead");
	}

	public final void make() {
		makeBreadcrumbs();

		HTMLNode threadsBox = addContentBox(l10n().getString("BoardPage.Threads.Header", "boardname" , mBoard.getName()));
		
		// Button for creating a new thread
		HTMLNode buttonRow = threadsBox.addChild("div", "class", "button-row");
		HTMLNode newThreadButton = buttonRow.addChild("span", "style", "float: left;");
		HTMLNode newThreadForm = addFormChild(newThreadButton, Freetalk.PLUGIN_URI + "/NewThread", "NewThreadPage");
			newThreadForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "OwnIdentityID", mOwnIdentity.getID() });
			newThreadForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "BoardName", mBoard.getName() });
			newThreadForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", l10n().getString("BoardPage.CreateNewThreadButton") });
			
        // Button to mark all threads read
		HTMLNode markAllAsReadButton = buttonRow.addChild("span", "style", "float: right;");
        HTMLNode markAllAsReadButtonForm = addFormChild(markAllAsReadButton, getURI(mBoard.getName()), "BoardPage");
        	markAllAsReadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
        	markAllAsReadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "name", mBoard.getName()});
        	markAllAsReadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "MarkAllThreadsAsRead", "true"});
        	markAllAsReadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("BoardPage.MarkAllThreadsAsReadButton") });

        // Clear margins after button row.
        threadsBox.addChild("div", "style", "clear: both;");

		// Threads table
		HTMLNode threadsTable = threadsBox.addChild("table", new String[] { "border", "width" }, new String[] { "0", "100%" });
		
		// Tell the browser the table columns and their size 
		HTMLNode colgroup = threadsTable.addChild("colgroup");
			colgroup.addChild("col", "width", "100%"); // Title, should use as much space as possible, the other columns should have minimal size
			colgroup.addChild("col"); // Author
			colgroup.addChild("col"); // Trust
			colgroup.addChild("col"); // Date
			colgroup.addChild("col"); // Replies
		
		HTMLNode row = threadsTable.addChild("thead");
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Title"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Author"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Trust"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Date"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Replies"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Unread"));
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		HTMLNode table = threadsTable.addChild("tbody");
		
		synchronized(mBoard) {
		    
			for(BoardThreadLink threadReference : mBoard.getThreads()) {
				// TODO: The author in the threadReference is guessed from the ID if the thread was not downloaded...
				// we should display a warning that the fact "the original thread was written by X" might not be true because 
				// thread-IDs can be spoofed - dunno how to do that in the table, maybe with colors? 
				
				String authorText;
				String authorScore;
				
				// Author related stuff
				{
					Identity author = null;
					
					// TODO: Use a colored "unknown" if the author/score is unknown
					// TODO: Use a special color if author == yourself
					authorText = "?"; // TODO: l10n 
					authorScore = "?"; 
					
					try {
						author = mFreetalk.getIdentityManager().getIdentity(threadReference.getAuthorID());
						authorText = author.getShortestUniqueName();
						
						// TODO: Get rid of the cast somehow, we should maybe call this WoTBoardPage :|
						final int score = ((WoTOwnIdentity)mOwnIdentity).getScoreFor((WoTIdentity)author);
						if (score == Integer.MAX_VALUE)
							authorScore = "-"; // TODO: l10n
						else
							authorScore = Integer.toString(score);
					} catch(NoSuchIdentityException e) { 
					} catch(NotInTrustTreeException e) {
						authorScore = "none"; // FIXME: l10n
					} catch(Exception e) {
						Logger.error(this, "getScoreFor() failed", e);
					}
				}
				
				String threadTitle = threadReference.getMessageTitle();
				
                // mark thread read if requested ...
                if (mMarkAllThreadsAsRead) {
                    threadReference.markThreadAndRepliesAsReadAndCommit();
                }
                

				row = table.addChild("tr");
				 // FIXME: Use the HTML trick in the bugtracker which tells the browser to only display as much as there is room in the table
				threadTitle = maxLength(threadTitle, 70);

				HTMLNode titleCell = row.addChild("td", new String[] { "align" }, new String[] { "left" });
				
				final boolean threadWasRead = threadReference.wasThreadRead();
				if(threadWasRead== false)
					titleCell = titleCell.addChild("b");
				
				titleCell.addChild(new HTMLNode("a", "href", ThreadPage.getURI(mBoard, threadReference), threadTitle));

				/* Author */
				row.addChild("td", new String[] { "align" }, new String[] { "left" }, authorText);

				/* Trust */
				row.addChild("td", new String[] { "align" }, new String[] { "left" }, authorScore);

				/* Date of last reply */
				row.addChild("td", new String[] { "align" , "style" }, new String[] { "center" , "white-space:nowrap;"}, 
						dateFormat.format(threadReference.getLastReplyDate()));

				/* Reply count */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, 
						Integer.toString(mBoard.threadReplyCount(threadReference.getThreadID())));
				
				/* Unread count */
				int unreadCount = 0;
				if(!threadWasRead) {
					if(!threadReference.wasRead()) {
						unreadCount++;
					}
					
					unreadCount += mBoard.threadUnreadReplyCount(threadReference.getThreadID());
				}
				
				row.addChild(threadWasRead ? "td" : "th", new String[] { "align" }, new String[] { "center" }, Integer.toString(unreadCount));
			}
		}
	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		BoardPage.addBreadcrumb(trail, mBoard);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail, Board board) {
		trail.addBreadcrumbInfo(board.getName(), getURI(board));
	}
	
	public static void addBreadcrumb(BreadcrumbTrail trail, String boardName) {
		trail.addBreadcrumbInfo(boardName, getURI(boardName));
	}
	
	public static String getURI(Board board) {
		return getURI(board.getName());
	}
	
	public static String getURI(String boardName) {
		return Freetalk.PLUGIN_URI + "/showBoard?name=" + boardName;
	}
}
