/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.HashSet;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Quoting;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class EditWikiPage extends WebPageImpl {

	private final Board mBoard;
	private final Message mThread;
	private final Message mParentMessage;
	private final String mSubject;

	public EditWikiPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchBoardException, NoSuchMessageException {
		super(myWebInterface, viewer, request);
		mBoard = mFreetalk.getMessageManager().getBoardByName("en.test");
		mParentMessage = mFreetalk.getMessageManager().get(request.getPartAsString("ParentMessageID", 128)); /* TODO: adapt to maximal ID length when it has been decided */
		mSubject = request.getPartAsString("Page", 128);
		Message thread;
		try {
			thread = (mParentMessage.isThread() ? mParentMessage : mParentMessage.getThread());
		} catch(NoSuchMessageException e) {
			// the thread is not loaded yet, make do with the message we are replying to
			thread = mParentMessage;
		}
		mThread = thread;
	}

	public void make() {
		if(mRequest.isPartSet("Save")) {
			HashSet<Board> boards = new HashSet<Board>();
			boards.add(mBoard);
			String replySubject = mRequest.getPartAsString("ReplySubject", Message.MAX_MESSAGE_TITLE_TEXT_LENGTH);
			String replyText = mRequest.getPartAsString("ReplyText", Message.MAX_MESSAGE_TITLE_TEXT_LENGTH);

			try {
				mFreetalk.getMessageManager().postMessage(mParentMessage, boards, mBoard, mOwnIdentity, replySubject, null, replyText, null);

				HTMLNode successBox = addContentBox("Changes created");
				successBox.addChild("p", "The new version of the page was put into your outbox. Freetalk will upload it after some time."); 
				
				successBox.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/Wiki?page=" + replySubject, "Back to page"));
			} catch (Exception e) {
				HTMLNode alertBox = addAlertBox("The new version of the page could not be created.");
				alertBox.addChild("div", e.getMessage());
				
				makeNewReplyPage(replySubject, replyText);
			}
		}
		else {
			makeNewReplyPage(mSubject, mParentMessage.getText());
		}
	}

	private void makeNewReplyPage(String replySubject, String replyText) {
		HTMLNode replyBox = addContentBox("Editing " + replySubject);
		HTMLNode newReplyForm = addFormChild(replyBox, Freetalk.PLUGIN_URI + "/EditWiki", "EditWiki");
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "ParentMessageID", mParentMessage.getID()});
		
		HTMLNode authorBox = newReplyForm.addChild(getContentBox("Author"));
		authorBox.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		authorBox.addChild("b", mOwnIdentity.getFreetalkAddress());
		
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "ReplySubject", replySubject});
		
		HTMLNode textBox = newReplyForm.addChild(getContentBox("Text"));
		textBox.addChild("textarea", new String[] { "name", "cols", "rows" }, new String[] { "ReplyText", "80", "30" }, replyText);
		
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "Save", "Save"});
		newReplyForm.addChild("#", " or back to ");
		newReplyForm.addChild("a", "href", Freetalk.PLUGIN_URI + "/Wiki?page=" + replySubject, replySubject);
	}
}
