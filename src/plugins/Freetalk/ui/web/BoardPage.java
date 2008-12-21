/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class BoardPage extends WebPageImpl {

	private Board mBoard;
	
	public BoardPage(Freetalk ft, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchBoardException {
		super(ft, viewer, request);
		mBoard = ft.getMessageManager().getBoardByName(request.getParam("name"));
	}

	public void make() {
		HTMLNode threadsBox = getContentBox("Threads in '" + mBoard.getName() + "'");
		
		// Display the list of known identities
		HTMLNode threadsTable = threadsBox.addChild("table", new String[] {"border", "width"}, new String[] {"0", "100%"});
		HTMLNode row = threadsTable.addChild("tr");
		row.addChild("th", "Title");
		row.addChild("th", "Author");
		row.addChild("th", "Date");
		row.addChild("th", "Replies");
		
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
