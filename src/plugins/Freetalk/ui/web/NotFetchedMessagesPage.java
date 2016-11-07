package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageList.MessageFetchFailedMarker;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchFetchFailedMarkerException;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class NotFetchedMessagesPage extends WebPageImpl {

	private final Board mBoard;
	private final boolean mBoardIsSubscribed;
	
	public NotFetchedMessagesPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) throws NoSuchBoardException {
		super(myWebInterface, viewer, request, _baseL10n);
		mBoard = mFreetalk.getMessageManager().getBoardByName(request.getParam("BoardName"));
		
		boolean boardIsSubscribed = false;
		try {
			mFreetalk.getMessageManager().getSubscription(mOwnIdentity, mBoard.getName());
			boardIsSubscribed = true;
		} catch(NoSuchBoardException e) { }
		
		mBoardIsSubscribed = boardIsSubscribed;
	}

	@Override public final void make() {
		makeBreadcrumbs();

		HTMLNode messagesBox = addContentBox(l10n().getString("NotFetchedMessagesPage.Messages.Header", "boardname" , mBoard.getName()));
		messagesBox = messagesBox.addChild("div", "class", "messages");
		
		// Messages table
		HTMLNode messagesTable = messagesBox.addChild("table", "class", "messages-table");
		
		// TODO: The CSS file does not contain any code for this page yet. Implement it (copypaste from BoardPage CSS)
		
		// Tell the browser the table columns and their sizes. 
		HTMLNode colgroup = messagesTable.addChild("colgroup");
			colgroup.addChild("col", "width", "100%"); // URI, specifying the size only in CSS does not work.
			colgroup.addChild("col"); // Author
			colgroup.addChild("col"); // Trust
			colgroup.addChild("col"); // Date
			colgroup.addChild("col"); // Failed fetches
			colgroup.addChild("col"); // Latest failure reason
		
		HTMLNode row = messagesTable.addChild("thead").addChild("tr");
			row.addChild("th", l10n().getString("NotFetchedMessagesPage.MessageTableHeader.URI"));
			row.addChild("th", l10n().getString("NotFetchedMessagesPage.MessageTableHeader.Author"));
			row.addChild("th", l10n().getString("NotFetchedMessagesPage.MessageTableHeader.Trust"));
			row.addChild("th", l10n().getString("NotFetchedMessagesPage.MessageTableHeader.Date"));
			row.addChild("th", l10n().getString("NotFetchedMessagesPage.MessageTableHeader.FailedFetches"));
			row.addChild("th", l10n().getString("NotFetchedMessagesPage.MessageTableHeader.LatestFailureReason"));
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		HTMLNode table = messagesTable.addChild("tbody");
		
		final WoTMessageManager messageManager = mFreetalk.getMessageManager();
		
		synchronized(messageManager) {
			for(final MessageList.MessageReference ref : messageManager.getDownloadableMessagesSortedByDate(mBoard)) {
				MessageFetchFailedMarker failureMarker = null;
				int failedFetches;
				
				try {
					failureMarker = messageManager.getMessageFetchFailedMarker(ref);
					failedFetches = failureMarker.getNumberOfRetries();
				} catch(NoSuchFetchFailedMarkerException e) {
					if(ref.wasMessageDownloaded())
						continue; // We are the unfetched messages page, don't display fetched ones
					
					failedFetches = 0;
				}
				
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
						author = ref.getMessageList().getAuthor();
						authorText = author.getShortestUniqueName();
						
						// TODO: Get rid of the cast somehow, we should maybe call this WoTBoardPage :|
						final int score = ((WoTOwnIdentity)mOwnIdentity).getScoreFor((WoTIdentity)author);
						if (score == Integer.MAX_VALUE)
							authorScore = "-"; // TODO: l10n
						else
							authorScore = Integer.toString(score);
					} catch(NotInTrustTreeException e) {
						authorScore = l10n().getString("Common.WebOfTrust.ScoreNull");
					} catch(Exception e) {
						Logger.error(this, "getScoreFor() failed", e);
					}
				}
                
				row = table.addChild("tr", "class", "message-row");

				/* URI */
				row.addChild("td", "class", "uri", ref.getURI().toString());

				/* Author */
				row.addChild("td", "class", "author-name", authorText);

				/* Trust */
				row.addChild("td", "class", "author-score", authorScore);

				/* Date */
				row.addChild("td", "class", "date", dateFormat.format(ref.getCreationDate()));
				
				/* Fetched fail count */
				row.addChild("td", "class", "failed-fetches", Integer.toString(failedFetches));
				
				/* Last fail reason. TODO: l10n */
				row.addChild("td", "class", "failure-reason", failureMarker == null ? "-" : failureMarker.getReason().toString());
			}
		}
	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		
		if(mBoardIsSubscribed) 
			BoardPage.addBreadcrumb(trail, mBoard);
		else
			SelectBoardsPage.addBreadcrumb(trail);
		
		addBreadcrumb(trail, mBoard);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail, Board board) {
		addBreadcrumb(trail, board.getName());
	}
	
	public static void addBreadcrumb(BreadcrumbTrail trail, String boardName) {
		trail.addBreadcrumbInfo(trail.getL10n().getString("NotFetchedMessagesPage.Messages.Header", "boardname" , boardName), getURI(boardName));
	}
	
	public static String getURI(Board board) {
		return getURI(board.getName());
	}
	
	public static String getURI(String boardName) {
		return Freetalk.PLUGIN_URI + "/showNotFetchedMessages?BoardName=" + boardName;
	}

}
