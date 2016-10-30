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
import plugins.Freetalk.WoT.WoTIdentityManager;
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
    private final boolean mMarkAllThreadsAsUnread;
	
	public BoardPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) throws NoSuchBoardException {
		super(myWebInterface, viewer, request, _baseL10n);
		mBoard = mFreetalk.getMessageManager().getSubscription(viewer, request.getParam("name"));
		mMarkAllThreadsAsRead = mRequest.isPartSet("MarkAllThreadsAsRead");
		mMarkAllThreadsAsUnread = mRequest.isPartSet("MarkAllThreadsAsUnread");
	}

	@Override public final void make() {
		makeBreadcrumbs();

		HTMLNode threadsBox = addContentBox(l10n().getString("BoardPage.Threads.Header", "boardname" , mBoard.getName()));
		threadsBox = threadsBox.addChild("div", "class", "threads");
		
		// Button for creating a new thread
		HTMLNode buttonRow = threadsBox.addChild("div", "class", "button-row");
		HTMLNode newThreadButton = buttonRow.addChild("span", "class", "new-thread-button");
		HTMLNode newThreadForm = addFormChild(newThreadButton, Freetalk.PLUGIN_URI + "/NewThread", "NewThreadPage");
			newThreadForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "BoardName", mBoard.getName() });
			newThreadForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", l10n().getString("BoardPage.CreateNewThreadButton") });
			
        // Button to mark all threads read
		HTMLNode markAllAsReadButton = buttonRow.addChild("span", "class", "mark-all-read-button");
        HTMLNode markAllAsReadButtonForm = addFormChild(markAllAsReadButton, getURI(mBoard.getName()), "BoardPage");
        	markAllAsReadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "name", mBoard.getName()});
        	markAllAsReadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "MarkAllThreadsAsRead", "true"});
        	markAllAsReadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("BoardPage.MarkAllThreadsAsReadButton") });

        // Button to mark all threads read
        HTMLNode markAllAsUnreadButton = buttonRow.addChild("span", "class", "mark-all-unread-button");
        HTMLNode markAllAsUnreadButtonForm = addFormChild(markAllAsUnreadButton, getURI(mBoard.getName()), "BoardPage");
        	markAllAsUnreadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "name", mBoard.getName()});
        	markAllAsUnreadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "MarkAllThreadsAsUnread", "true"});
        	markAllAsUnreadButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("BoardPage.MarkAllThreadsAsUnreadButton") });
        	
        // Button to view not fetched messages
        HTMLNode showNotFetchedButton = buttonRow.addChild("span", "class", "show-not-fetched-button");
        HTMLNode showNotFetchedButtonForm = addFormChild(showNotFetchedButton, NotFetchedMessagesPage.getURI(mBoard), "NotFetchedMessagesPage");
	    	showNotFetchedButtonForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("BoardPage.ShowNotFetchedMessagesButton") });
    	
        // Clear margins after button row.
        // TODO: Move to freetalk.css
        threadsBox.addChild("div", "style", "clear: both;");

		// Threads table
		HTMLNode threadsTable = threadsBox.addChild("table", "class", "threads-table");
		
		// Tell the browser the table columns and their sizes. 
		HTMLNode colgroup = threadsTable.addChild("colgroup");
			colgroup.addChild("col", "width", "100%"); // Title, specifying the size only in CSS does not work.
			colgroup.addChild("col"); // Author
			colgroup.addChild("col"); // Trust
			colgroup.addChild("col"); // Date
			colgroup.addChild("col"); // Replies
			colgroup.addChild("col"); // Unread count
		
		HTMLNode row = threadsTable.addChild("thead").addChild("tr");
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Title"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Author"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Trust"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Date"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Replies"));
			row.addChild("th", l10n().getString("BoardPage.ThreadTableHeader.Unread"));
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		HTMLNode table = threadsTable.addChild("tbody");
		
		final WoTIdentityManager identityManager = mFreetalk.getIdentityManager();
		
		// TODO: We don't want to lock the identity manager here to make the displaying of the board FAST - it shouldn't wait until the lock
		// is available: Introduce a non-locking getIdentity() in the identity manager and use it - the locking one will cause deadlocks
		// if we do not lock the identity manager before the board.
		synchronized(identityManager) {
		synchronized(mBoard) {
		    boolean firstUnread = true;
		    
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
						author = identityManager.getIdentity(threadReference.getAuthorID());
						authorText = author.getShortestUniqueName();
						
						// TODO: Get rid of the cast somehow, we should maybe call this WoTBoardPage :|
						final int score = ((WoTOwnIdentity)mOwnIdentity).getScoreFor((WoTIdentity)author);
						if (score == Integer.MAX_VALUE)
							authorScore = l10n().getString("Common.WebOfTrust.Score.Infinite");
						else
							authorScore = Integer.toString(score);
					} catch(NoSuchIdentityException e) { 
					} catch(NotInTrustTreeException e) {
						authorScore = l10n().getString("Common.WebOfTrust.ScoreNull");
					} catch(Exception e) {
						Logger.error(this, "getScoreFor() failed", e);
					}
				}
				
				
                // mark thread read if requested ...
                if (mMarkAllThreadsAsRead) {
                    threadReference.markThreadAndRepliesAsReadAndCommit();
                } else if(mMarkAllThreadsAsUnread) {
                	threadReference.markThreadAndRepliesAsUnreadAndCommit();
                }

                final boolean threadWasRead = threadReference.wasThreadRead();
                final String threadTitle = threadReference.getMessageTitle();
                
				row = table.addChild("tr", "class", "thread-row");
				
				if(firstUnread && !threadReference.wasThreadRead()) {
					row.addAttribute("id", "FirstUnreadThread");
					firstUnread = false;
				}
				
				/* Unread count */
				int unreadCount = 0;
				if(!threadWasRead) {
					if(!threadReference.wasRead()) {
						unreadCount++;
					}
					
					unreadCount += mBoard.threadUnreadReplyCount(threadReference.getThreadID());
				}

				/* Title */
				HTMLNode titleCell = row.addChild("td", "class", threadWasRead ? "title-read" : "title-unread");
				
				if(unreadCount > 0) {
					titleCell.addChild(new HTMLNode("a", new String[]{"href", "title"}, new String[]{ThreadPage.getFirstUnreadURI(mBoard, threadReference), l10n().getString("BoardPage.ThreadTableHeader.GoToFirstUnreadMessage")}, "↪"));
					titleCell.addChild("#", " ");
				}

				titleCell.addChild(new HTMLNode("a", "href", ThreadPage.getURI(mBoard, threadReference), threadTitle));

				/* Author */
				row.addChild("td", "class", "author-name", authorText);

				/* Trust */
				row.addChild("td", "class", "author-score", authorScore);

				/* Date of last reply */
				row.addChild("td", "class", "date", dateFormat.format(threadReference.getLastReplyDate()));

				/* Reply count */
				row.addChild("td", "class", "reply-count",  Integer.toString(mBoard.threadReplyCount(threadReference.getThreadID())));

				if(unreadCount == 0) {
					row.addChild("td", "class", "unread-count-0", Integer.toString(unreadCount));
				} else {
					row.addChild("td", "class", "unread-count").addChild("a", "href", ThreadPage.getFirstUnreadURI(mBoard, threadReference), Integer.toString(unreadCount));
				}
			}
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
	
	public static String getFirstUnreadURI(String boardName) {
		return Freetalk.PLUGIN_URI + "/showBoard?name=" + boardName + "#FirstUnreadThread";
	}
}
