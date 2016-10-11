/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Board;
import plugins.Freetalk.IdentityStatistics;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import freenet.clients.http.RedirectException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Shows various statistics about Freetalk.
 * The computations of those statistics are heavy and allowed to be heavy as this page is mostly intended for developer use.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class StatisticsPage extends WebPageImpl {

	public StatisticsPage(WebInterface myWebInterface, OwnIdentity viewer,
			HTTPRequest request) {
		super(myWebInterface, viewer, request);
	}

	@Override
	public void make() throws RedirectException {
		final WoTIdentityManager identityManager = mFreetalk.getIdentityManager();
		final WoTMessageManager messageManager = mFreetalk.getMessageManager();
		
		synchronized(identityManager)
		{
			HTMLNode statsbox = addContentBox(l10n().getString("StatisticsPage.IdentityStatistics.Title"));
			statsbox.addChild("p", l10n().getString("StatisticsPage.IdentityStatistics.NonOwnIdentities") + " " + identityManager.countKnownIdentities());
			statsbox.addChild("p", l10n().getString("StatisticsPage.IdentityStatistics.OwnIdentities") + " " + identityManager.ownIdentityIterator().size());
		}

		synchronized(messageManager) {
		{
			HTMLNode statsbox = addContentBox(l10n().getString("StatisticsPage.MessageListStatistics.Title"));
			statsbox.addChild("p", l10n().getString("StatisticsPage.MessageListStatistics.MessageListEditionSum") + " " + computeMessageListEditionSum());
			statsbox.addChild("p", l10n().getString("StatisticsPage.MessageListStatistics.FetchedMessageListCount") + " " + messageManager.countNonOwnMessageLists());
			statsbox.addChild("p", l10n().getString("StatisticsPage.MessageListStatistics.KnownMessageCount") + " " + messageManager.countNonOwnMessageListMessageReferences());
			statsbox.addChild("p", l10n().getString("StatisticsPage.MessageListStatistics.MessageFetchQueueSize") + " " + messageManager.notDownloadedMessageIterator().size());
		
		}
		
		{
			HTMLNode statsbox = addContentBox(l10n().getString("StatisticsPage.MessageStatistics.Title"));
			final int ownMessageCount = messageManager.countOwnMessages();
			statsbox.addChild("p", l10n().getString("StatisticsPage.MessageStatistics.NonOwnMessageCount") + " " + (messageManager.countMessages() - ownMessageCount));
			statsbox.addChild("p", l10n().getString("StatisticsPage.MessageStatistics.OwnMessageCount") + " " + ownMessageCount);

		}
		
		{
			HTMLNode statsbox = addContentBox(l10n().getString("StatisticsPage.BoardStatistics.Title"));
			statsbox.addChild("p", l10n().getString("StatisticsPage.BoardStatistics.BoardCount") + " " + messageManager.boardIteratorSortedByName().size());
			statsbox.addChild("p", l10n().getString("StatisticsPage.BoardStatistics.SubscribedBoardCount") + " " + messageManager.subscribedBoardIterator().size());
			statsbox.addChild("p", l10n().getString("StatisticsPage.BoardStatistics.BoardMessageCount") + " " + computeBoardMessageCount());
			statsbox.addChild("p", l10n().getString("StatisticsPage.BoardStatistics.SubscribedBoardMessageCount") + " " + computeSubscribedBoardMessageCount());
		}
		}
		
		{
			HTMLNode statsbox = addContentBox(l10n().getString("StatisticsPage.TrafficStatistics.Title"));
			statsbox.addChild("p", l10n().getString("StatisticsPage.TrafficStatistics.RunningMessageListSubscriptions") + mFreetalk.getNewMessageListFetcher().getRunningFetchCount());
			statsbox.addChild("p", l10n().getString("StatisticsPage.TrafficStatistics.RunningMessageListFetches") + mFreetalk.getOldMessageListFetcher().getRunningFetchCount());
			statsbox.addChild("p", l10n().getString("StatisticsPage.TrafficStatistics.RunningMessageFetches") + mFreetalk.getMessageFetcher().getRunningFetchCount());
		}

	}
	
	private long computeMessageListEditionSum() {
		long sum = 0;
		
		for(IdentityStatistics stats : mFreetalk.getMessageManager().getAllIdentityStatistics()) {
			try {
				sum += stats.getIndexOfLatestAvailableMessageList();
			} catch (NoSuchMessageListException e) { }
		}
		
		return sum;
	}
	
	private long computeBoardMessageCount() {
		long messageCount = 0;
		
		for(Board board : mFreetalk.getMessageManager().boardIteratorSortedByName()) {
			messageCount += board.getDownloadedMessageCount();
		}
		
		return messageCount;
	}
	
	private long computeSubscribedBoardMessageCount() {
		long messageCount = 0;
		
		for(SubscribedBoard board : mFreetalk.getMessageManager().subscribedBoardIterator()) {
			messageCount += board.messageCount();
		}
		
		return messageCount;
	}
	
}
