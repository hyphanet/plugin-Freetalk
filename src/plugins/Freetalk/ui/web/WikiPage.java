/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;
import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import freenet.clients.http.RedirectException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor
 */
public final class WikiPage extends WebPageImpl {

	private String mSubject;
	private Board mBoard;
	private Message mThread;

	public WikiPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchMessageException, NoSuchBoardException {
		super(myWebInterface, viewer, request);
		mSubject = request.getParam("page");
		mBoard = mFreetalk.getMessageManager().getBoardByName("en.test");
		mThread = getWikiThread(mBoard, mSubject);
	}

	public final void make() throws RedirectException {
		if(mOwnIdentity == null)
			throw new RedirectException(logIn);
		HTMLNode test = addContentBox(mSubject);

		Message last = null;

		for(MessageReference reference : mBoard.getAllThreadReplies(mThread, true)) {
			last = reference.getMessage();
		}
		if(last != null) {
			addEditButton(test, last);
			test.addChild("#", last.getText());
		}
	}

    private void addEditButton(HTMLNode parent, Message parentMessage) {
        parent = parent.addChild("div", "style", "float:right");
        HTMLNode newReplyForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/EditWiki", "EditWiki");
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "Page", mSubject});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ParentMessageID", parentMessage.getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "Edit" });
    }

	private Message getWikiThread(Board board, String subject) throws NoSuchMessageException{
		synchronized(mBoard) {
			for(MessageReference threadReference : board.getThreads(mOwnIdentity)) {
				Message thread = threadReference.getMessage();
				// we are looking for a thread with subject mSubject
				if(thread.getTitle().equals(subject)) {
					// found!
					return thread;
				}
			}
		}
		throw new NoSuchMessageException();
	}

	private void makeBoardsList() {
		HTMLNode boardsBox = addContentBox("Boards");

		HTMLNode newBoardForm = addFormChild(boardsBox, Freetalk.PLUGIN_URI + "/NewBoard", "NewBoardPage");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "New board" });
		
		// Display the list of known identities
		HTMLNode boardsTable = boardsBox.addChild("table", "border", "0");
		HTMLNode row = boardsTable.addChild("tr");
		row.addChild("th", "Name");
		row.addChild("th", "Description");
		row.addChild("th", "Messages");
		row.addChild("th", "Latest message");
		
		DateFormat dateFormat = DateFormat.getInstance();
		
		/* FIXME: Currently we show all boards. We should rather show the boards which the identity has selected */
		synchronized(mFreetalk.getMessageManager()) {
			Iterator<Board> boards = mFreetalk.getMessageManager().boardIterator();
			while(boards.hasNext()) {
				Board board = boards.next();
				row = boardsTable.addChild("tr");

				HTMLNode nameCell = row.addChild("th", new String[] { "align" }, new String[] { "left" });
				nameCell.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/showBoard?identity=" + mOwnIdentity.getUID() + "&name=" + board.getName(),
						board.getName()));

				/* Description */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, "not implemented yet");

				/* Message count */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, Integer.toString(board.messageCount()));
				
				/* Date of latest message */
				row.addChild("td", new String[] { "align" }, new String[] { "center" }, board.getLatestMessageDate() == null ? "-" : dateFormat.format(board.getLatestMessageDate()));
			}
		}

		boardsBox.addChild("p", "It may take a few minutes before Freetalk has discovered all boards and all messages posted to it.");
	}
}
