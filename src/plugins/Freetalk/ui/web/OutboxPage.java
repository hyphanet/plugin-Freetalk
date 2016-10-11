/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnMessage;
import freenet.clients.http.RedirectException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Shows a list of OwnMessages which have not been inserted yet and allows the user to delete them.
 * TODO: Also allow deletion of messages which have been inserted but whose MessageList has not been inserted yet.
 * 		If you implement this, also update l10n of "OutboxPage.Outbox.Table.Empty"
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class OutboxPage extends WebPageImpl {

	public OutboxPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
	}
	
	public static String getURI() {
		return Freetalk.PLUGIN_URI + "/Outbox";
	}

	@Override
	public void make() throws RedirectException {
		maybeDeleteMessages();
		makeOutbox();
	}
	
	private void maybeDeleteMessages() throws RedirectException {
		if(!mRequest.isPartSet("DeleteMessage"))
			return;
		
		try {
			mFreetalk.getMessageManager().deleteUnsentMessage(mRequest.getPartAsStringFailsafe("MessageID", 256));
			
			final HTMLNode deletedBox = addContentBox(l10n().getString("OutboxPage.DeletedMessage.Header"));
			deletedBox.addChild("#", l10n().getString("OutboxPage.DeletedMessage.Text"));
		} catch(Exception e) {
			new ErrorPage(mWebInterface, mOwnIdentity, mRequest, l10n().getString("OutboxPage.DeleteMessage.Failed"), e, l10n())
				.addToPage(mContentNode);
		}
	}
	
	private void makeOutbox() {
		final HTMLNode outbox = addContentBox(l10n().getString("OutboxPage.Outbox.Header")).addChild("div", "class", "outbox");
		
		final HTMLNode threadsTable = new HTMLNode("table", "class", "outbox-table");
		
		// Tell the browser the table columns and their sizes. 
		final HTMLNode colgroup = threadsTable.addChild("colgroup");
			colgroup.addChild("col"); // Board
			colgroup.addChild("col", "width", "100%"); // Title, specifying the size only in CSS does not work.
			colgroup.addChild("col"); // Date
			colgroup.addChild("col"); // Delete
		
		HTMLNode row = threadsTable.addChild("thead").addChild("tr");
			row.addChild("th", l10n().getString("OutboxPage.Outbox.Table.Boards"));
			row.addChild("th", l10n().getString("OutboxPage.Outbox.Table.Title"));
			row.addChild("th", l10n().getString("OutboxPage.Outbox.Table.Date"));
			row.addChild("th", l10n().getString("OutboxPage.Outbox.Table.Delete"));

		final HTMLNode table = threadsTable.addChild("tbody");
		
		final DateFormat dateFormat = DateFormat.getInstance();

		final WoTMessageManager messageManager = mFreetalk.getMessageManager();
		
		boolean tableEmpty = true;
		
		synchronized(messageManager) {
			for(final WoTOwnMessage message : messageManager.getNotInsertedOwnMessages()) {
				tableEmpty = false;
				
				row = table.addChild("tr", "class", "message-row");
				
				final Board[] boards = message.getBoards();
				final StringBuilder boardsString = new StringBuilder(128);
				for(int i=0; i < boards.length;) {
					boardsString.append(boards[i].getName());
					if(++i < boards.length)
						boardsString.append(", ");
				}
				row.addChild("td", "class", "boards", boardsString.toString());

				// Title
				row.addChild("td", "class", "title", message.getTitle());
				
				// Date
				row.addChild("td", "class", "date", dateFormat.format(message.getDate()));

				// Delete button
				final HTMLNode form = addFormChild(row.addChild("td", "class", "delete"), getURI(), "DeleteMessage");
				form.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "MessageID", message.getID()});
				form.addChild("input", new String[] { "type", "name", "value" }, new String[] {"submit", "DeleteMessage", l10n().getString("OutboxPage.Outbox.Table.DeleteButton") });
			}
		}
		
		if(tableEmpty)
			outbox.addChild("#", l10n().getString("OutboxPage.Outbox.Table.Empty"));
		 else 
			outbox.addChild(threadsTable);
	}

}
