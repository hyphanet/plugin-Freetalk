/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor
 *
 */
public final class BoardPage extends WebPageImpl {

	private final Board mBoard;
	
	public BoardPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchBoardException {
		super(myWebInterface, viewer, request);
		mBoard = mFreetalk.getMessageManager().getBoardByName(request.getParam("name"));
	}

	public final void make() {
		
		HTMLNode threadsBox = addContentBox("Threads in '" + mBoard.getName() + "'");
		
		HTMLNode newThreadForm = addFormChild(threadsBox, SELF_URI + "/NewThread", "NewThreadPage");
		newThreadForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		newThreadForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		newThreadForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "New thread" });
		
		// Display the list of known identities
		HTMLNode threadsTable = threadsBox.addChild("table", new String[] {"border", "width"}, new String[] {"0", "100%"});
		HTMLNode row = threadsTable.addChild("tr");
		row.addChild("th", "Title");
		row.addChild("th", "Author");
		row.addChild("th", "Date");
		row.addChild("th", "Replies");
		
		synchronized(mBoard) { /* FIXME: Is this enough synchronization or should we lock the message manager? */
			Iterator<MessageReference> threads = mBoard.threadIterator(mOwnIdentity);
			while(threads.hasNext()) {
				Message thread = threads.next().getMessage();

				row = threadsTable.addChild("tr");

				HTMLNode titleCell = row.addChild("td", new String[] { "align" }, new String[] { "left" });
				titleCell.addChild(new HTMLNode("a", "href", SELF_URI + "/showThread?identity=" + mOwnIdentity.getUID() + 
						"&board=" + mBoard.getName() + "&id=" + thread.getID(), thread.getTitle()));

				/* Author */
				String authorText = thread.getAuthor().getFreetalkAddress();
				/* FIXME: Use the following algorithm for selecting how many characters after the '@' to show:
				 * characterCount = 0
				 * while(two or more identities exist with authorText being the same with given characterCount) characterCount++; */
				authorText = authorText.substring(0, authorText.indexOf('@') + 5);
				row.addChild("td", new String[] { "align" }, new String[] { "left" }, authorText);

				/* Date */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, thread.getDate().toLocaleString()); /* FIXME: Use Calendar */

				/* Reply count */
				row.addChild("td", new String[] { "align" }, new String[] { "right" }, Integer.toString(mBoard.threadReplyCount(mOwnIdentity, thread)));
			}
		}
	}

}
