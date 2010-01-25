/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardReplyLink;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
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
	
	public BoardPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) throws NoSuchBoardException {
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
				Message thread;
				String threadTitle;
				String authorText;
				String authorScore; 

                // mark thread read if requested ...
                if (mMarkAllThreadsAsRead) {
                    markThreadRead(threadReference);
                }

				try {
					thread = threadReference.getMessage();
					threadTitle = thread.getTitle();
					authorText = thread.getAuthor().getShortestUniqueName(30);
					
					try {
					// TODO: Get rid of the cast somehow, we should maybe call this WoTBoardPage :|
						authorScore = Integer.toString(((WoTOwnIdentity)mOwnIdentity).getScoreFor((WoTIdentity)thread.getAuthor()));
					}
					catch(NotInTrustTreeException e) {
						authorScore = "null"; // FIXME: Decide about this we should display something better
					}
					catch(Exception e) {
						Logger.error(this, "getScoreFor() failed", e);
						authorScore = "UNKNOWN";
					}
				}
				catch(MessageNotFetchedException e) {
					thread = null;
					threadTitle = "UNKNOWN";
					
	            	// FIXME: The author can be reconstructed from the thread id because it contains the id of the author. We just need to figure out
	            	// what the proper place for a function "getIdentityIDFromThreadID" is and whether I have already written one which can do that, and if
	            	// yes, where it is.
					authorText = "UNKNOWN";
					authorScore = "UNKNOWN";
					
					// The thread was not downloaded yet, we use the title of it's first reply as it's title.
					for(BoardReplyLink messageRef : mBoard.getAllThreadReplies(threadReference.getThreadID(), true)) {
						try {
							threadTitle = messageRef.getMessage().getTitle();
						} catch(MessageNotFetchedException e1) {
							throw new RuntimeException(e1); // Should not happen: BoardReplyLink objects are only created if a message was fetched already.
						}
						break;
					}
				}

				row = table.addChild("tr");
				threadTitle = maxLength(threadTitle, 70); // TODO: Adjust

				HTMLNode titleCell = row.addChild("td", new String[] { "align" }, new String[] { "left" });
				
				if(threadReference.wasThreadRead() == false)
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
				int unreadCount = getUnreadCount(threadReference);
				row.addChild((unreadCount>0) ? "th" : "td", new String[] { "align" }, new String[] { "center" }, Integer.toString(unreadCount));
			}
		}
	}
	
	private int getUnreadCount(BoardThreadLink threadReference) {
	    int unreadCount = 0;
        if (!threadReference.wasRead()) {
            unreadCount++;
        }
        
        // scan all replies
        for(BoardReplyLink reference : mBoard.getAllThreadReplies(threadReference.getThreadID(), true)) {
            if(!reference.wasRead()) {
                unreadCount++;
            }
        }
        return unreadCount;
	}

    /**
     * @param threadReference
     */
    private void markThreadRead(BoardThreadLink threadReference) {
        // maybe mark thread and top level message read
        boolean doStore = false;
        if (!threadReference.wasThreadRead()) {
            threadReference.markThreadAsRead();
            doStore = true;
        }
        if (!threadReference.wasRead()) {
            threadReference.markAsRead();
            doStore = true;
        }
        if (doStore) {
            threadReference.storeAndCommit();
        }
        
        // mark all thread replies as read
        for(BoardReplyLink reference : mBoard.getAllThreadReplies(threadReference.getThreadID(), true)) {
            if(!reference.wasRead()) {
                reference.markAsRead();
                reference.storeAndCommit();
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
