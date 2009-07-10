/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.HashSet;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageURI;
import plugins.Freetalk.Quoting;
import plugins.Freetalk.Board.BoardThreadLink;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class NewReplyPage extends WebPageImpl {

	private final Board mBoard;
	private final BoardThreadLink mParentThread;
	private final Message mParentMessage;

	public NewReplyPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchBoardException, NoSuchMessageException {
		super(myWebInterface, viewer, request);
		mBoard = mFreetalk.getMessageManager().getBoardByName(request.getPartAsString("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH));
		mParentThread = mBoard.getThreadReference(request.getPartAsString("ParentThreadID", 128));
		mParentMessage = mFreetalk.getMessageManager().get(request.getPartAsString("ParentMessageID", 128)); /* TODO: adapt to maximal ID length when it has been decided */
	}

	public void make() {
		makeBreadcrumbs();
		if(mRequest.isPartSet("CreateReply")) {
			HashSet<Board> boards = new HashSet<Board>();
			boards.add(mBoard);
			String replySubject = mRequest.getPartAsString("ReplySubject", Message.MAX_MESSAGE_TITLE_TEXT_LENGTH);
			String replyText = mRequest.getPartAsString("ReplyText", Message.MAX_MESSAGE_TITLE_TEXT_LENGTH);

			try {
				MessageURI parentThreadURI;
				
				if(mParentThread.getMessage() != null)
					parentThreadURI = mParentThread.getMessage().getURI();
				else
					parentThreadURI = mParentMessage.getThreadURI();
				
				// We do not always use the thread URI of the parent message because it might be the thread itself, then getThreadURI() will fail - threads have none.
				// Further, we cannot do "parentThreadURI = mParentMessage.isThread() ? mParentMessage.getURI() : mParentMessage.getThreadURI()" because
				// the parent thread might be a forked thread, i.e. isThread() will return false and getThreadURI() will return the URI of the original thread
				// - the replies would go to the original thread instead of to the forked one where they actually should go to.
				
				mFreetalk.getMessageManager().postMessage(parentThreadURI,
						mParentMessage, boards, mBoard, mOwnIdentity, replySubject, null, replyText, null);

				HTMLNode successBox = addContentBox("Reply created");
				successBox.addChild("p", "The reply was put into your outbox. Freetalk will upload it after some time."); 
				
				successBox.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/showThread?identity=" + mOwnIdentity.getUID() + 
						"&board=" + mBoard.getName() + "&id=" + mParentThread.getThreadID(),
				"Go back to parent thread"));
				successBox.addChild("br");
				
				successBox.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/showBoard?identity=" + mOwnIdentity.getUID() + "&name=" + mBoard.getName(),
						"Go back to " + mBoard.getName()));
			} catch (Exception e) {
				HTMLNode alertBox = addAlertBox("The reply could not be created.");
				alertBox.addChild("div", e.getMessage());
				
				makeNewReplyPage(replySubject, replyText);
			}
		}
		else {
			String subject = mParentMessage.getTitle();
			if(!subject.startsWith("Re:"))
				subject = "Re: " + subject;
			
			makeNewReplyPage(subject, Quoting.getFullQuote(mParentMessage));
		}
	}

	private void makeNewReplyPage(String replySubject, String replyText) {
		HTMLNode replyBox = addContentBox("New reply to " + mBoard.getName());
		HTMLNode newReplyForm = addFormChild(replyBox, Freetalk.PLUGIN_URI + "/NewReply", "NewReply");
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "ParentThreadID", mParentThread.getThreadID()});
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "ParentMessageID", mParentMessage.getID()});
		
		HTMLNode authorBox = newReplyForm.addChild(getContentBox("Author"));
		authorBox.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		authorBox.addChild("b", mOwnIdentity.getFreetalkAddress());
		
		HTMLNode subjectBox = newReplyForm.addChild(getContentBox("Subject"));
		subjectBox.addChild("input", new String[] {"type", "name", "size", "maxlength", "value"},
									new String[] {"text", "ReplySubject", "100", Integer.toString(Message.MAX_MESSAGE_TITLE_TEXT_LENGTH), replySubject});		
		
		HTMLNode textBox = newReplyForm.addChild(getContentBox("Text"));
		textBox.addChild("textarea", new String[] { "name", "cols", "rows" }, new String[] { "ReplyText", "80", "30" }, replyText);
		
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "CreateReply", "Submit"});
	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail();
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		BoardPage.addBreadcrumb(trail, mBoard);
		ThreadPage.addBreadcrumb(trail, mBoard, mParentThread);
		NewReplyPage.addBreadcrumb(trail);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail) {
		trail.addBreadcrumbInfo("Reply", "");
	}
}
