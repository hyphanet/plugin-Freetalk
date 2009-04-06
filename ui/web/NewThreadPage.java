/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.HashSet;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class NewThreadPage extends WebPageImpl {

	private final Board mBoard;
	
	public NewThreadPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchBoardException {
		super(myWebInterface, viewer, request);
		mBoard = mFreetalk.getMessageManager().getBoardByName(request.getPartAsString("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH));
	}
	
	public void make() {
		if(mRequest.isPartSet("CreateThread")) { 
			HashSet<Board> boards = new HashSet<Board>();
			boards.add(mBoard);
			String threadSubject = mRequest.getPartAsString("ThreadSubject", Message.MAX_MESSAGE_TITLE_TEXT_LENGTH);
			String threadText = mRequest.getPartAsString("ThreadText", Message.MAX_MESSAGE_TEXT_BYTE_LENGTH);

			try {
				mFreetalk.getMessageManager().postMessage(null, boards, mBoard, mOwnIdentity, threadSubject, null, threadText, null);

				HTMLNode successBox = addContentBox("Thread created");
				successBox.addChild("p", "The thread was put into your outbox. Freetalk will upload it after some time.");
				successBox.addChild(new HTMLNode("a", "href", SELF_URI + "/showBoard?identity=" + mOwnIdentity.getUID() + "&name=" + mBoard.getName(),
								"Go back to " + mBoard.getName()));
			} catch (Exception e) {
				HTMLNode alertBox = addAlertBox("The thread could not be created.");
				alertBox.addChild("div", e.getMessage());
				
				makeNewThreadPage(threadSubject, threadText);
			}
		}
		else
			makeNewThreadPage("", "");
	}

	private void makeNewThreadPage(String threadSubject, String threadText) {
		HTMLNode threadBox = addContentBox("New thread in " + mBoard.getName());
		HTMLNode newThreadForm = addFormChild(threadBox, SELF_URI + "/NewThread", "NewThread");
		newThreadForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		
		HTMLNode authorBox = newThreadForm.addChild(getContentBox("Author"));
		authorBox.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		authorBox.addChild("b", mOwnIdentity.getFreetalkAddress());
		
		HTMLNode subjectBox = newThreadForm.addChild(getContentBox("Subject"));
		subjectBox.addChild("input", new String[] {"type", "name", "size", "value"}, new String[] {"text", "ThreadSubject", "100", threadSubject}); /* FIXME: Find a reasonable maximal subject length and specify here and elsewhere */		
		
		HTMLNode textBox = newThreadForm.addChild(getContentBox("Text"));
		textBox.addChild("textarea", new String[] { "name", "cols", "rows" }, new String[] { "ThreadText", "80", "30" }, threadText);
		
		newThreadForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "CreateThread", "Submit"});
	}

}
