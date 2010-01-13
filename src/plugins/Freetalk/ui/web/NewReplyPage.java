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
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class NewReplyPage extends WebPageImpl {

	private final SubscribedBoard mBoard;
	private final BoardThreadLink mParentThread;
	private final Message mParentMessage;

	public NewReplyPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) 
	throws NoSuchBoardException, NoSuchMessageException {
		super(myWebInterface, viewer, request, _baseL10n);
		mBoard = mFreetalk.getMessageManager().getSubscription(mOwnIdentity, request.getPartAsString("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH));
		
		mParentThread = mBoard.getThreadLink(request.getPartAsString("ParentThreadID", 128));
		mParentMessage = mFreetalk.getMessageManager().get(request.getPartAsString("ParentMessageID", 128)); /* TODO: adapt to maximal ID length when it has been decided */
	}

	public void make() {
		makeBreadcrumbs();
		if(mRequest.isPartSet("CreateReply")) {
			HashSet<Board> boards = new HashSet<Board>();
			boards.add(mBoard);
			String replySubject = mRequest.getPartAsString("ReplySubject", Message.MAX_MESSAGE_TITLE_TEXT_LENGTH);
			String replyText = mRequest.getPartAsString("ReplyText", Message.MAX_MESSAGE_TEXT_LENGTH);
			
			/* FIXME: Add code which warns the user if the subject / text is to long. Currently, getPartAsString just returns an empty string if it is.
			 * For the subject this might be okay because the <input type="text" ...> can and does specify a max length, but the <textarea> cannot AFAIK. */

			try {
				MessageURI parentThreadURI;
				
				try {
					parentThreadURI = mParentThread.getMessage().getURI();
				}
				catch(MessageNotFetchedException e) {
					parentThreadURI = mParentMessage.getThreadURI();
				}
				
				// We do not always use the thread URI of the parent message because it might be the thread itself, then getThreadURI() will fail - threads have none.
				// Further, we cannot do "parentThreadURI = mParentMessage.isThread() ? mParentMessage.getURI() : mParentMessage.getThreadURI()" because
				// the parent thread might be a forked thread, i.e. isThread() will return false and getThreadURI() will return the URI of the original thread
				// - the replies would go to the original thread instead of to the forked one where they actually should go to.
				
				mFreetalk.getMessageManager().postMessage(parentThreadURI,
						mParentMessage, boards, mBoard, mOwnIdentity, replySubject, null, replyText, null);

				HTMLNode successBox = addContentBox(l10n().getString("NewReplyPage.ReplyCreated.Header"));
				successBox.addChild("p", l10n().getString("NewReplyPage.ReplyCreated.Text")); 
				
				HTMLNode aChild = successBox.addChild("#");
                l10n().addL10nSubstitution(
                        aChild, 
                        "NewReplyPage.ReplyCreated.BackToParentThread",
                        new String[] { "link", "/link" }, 
                        new String[] {
                                "<a href=\"" + ThreadPage.getURI(mBoard, mParentThread) + "\">",
                                "</a>" });
				
				successBox.addChild("br");
				
				aChild = successBox.addChild("#");
                l10n().addL10nSubstitution(
                        aChild, 
                        "NewReplyPage.ReplyCreated.BackToBoard",
                        new String[] { "link", "boardname", "/link" }, 
                        new String[] {
                                "<a href=\"" + BoardPage.getURI(mBoard) + "\">", // FIXME: URI without id name, ok?
                                mBoard.getName(),
                                "</a>" });
			} catch (Exception e) {
				HTMLNode alertBox = addAlertBox(l10n().getString("NewReplyPage.ReplyFailed.Header"));
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
	    
	    final String trnsl = l10n().getString(
	            "NewReplyPage.ReplyBox.Header",
	            new String[] { "boardname" }, 
	            new String[] { mBoard.getName() });
		HTMLNode replyBox = addContentBox(trnsl);
	    
		HTMLNode newReplyForm = addFormChild(replyBox, Freetalk.PLUGIN_URI + "/NewReply", "NewReply");
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "ParentThreadID", mParentThread.getThreadID()});
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "ParentMessageID", mParentMessage.getID()});
		
		HTMLNode authorBox = newReplyForm.addChild(getContentBox(l10n().getString("NewReplyPage.ReplyBox.Author")));
		authorBox.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		authorBox.addChild("b", mOwnIdentity.getFreetalkAddress());
		
		HTMLNode subjectBox = newReplyForm.addChild(getContentBox(l10n().getString("NewReplyPage.ReplyBox.Subject")));
		subjectBox.addChild("input", new String[] {"type", "name", "size", "maxlength", "value"},
									new String[] {"text", "ReplySubject", "100", Integer.toString(Message.MAX_MESSAGE_TITLE_TEXT_LENGTH), replySubject});		
		
		HTMLNode textBox = newReplyForm.addChild(getContentBox(l10n().getString("NewReplyPage.ReplyBox.Text")));
		textBox.addChild("textarea", new String[] { "name", "cols", "rows" }, new String[] { "ReplyText", "80", "30" }, replyText);
		
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "CreateReply", l10n().getString("NewReplyPage.ReplyBox.SubmitButton")});
	}

	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		BoardPage.addBreadcrumb(trail, mBoard);
		ThreadPage.addBreadcrumb(trail, mBoard, mParentThread);
		NewReplyPage.addBreadcrumb(trail);
		mContentNode.addChild(trail.getHTMLNode());
	}

	public static void addBreadcrumb(BreadcrumbTrail trail) {
		trail.addBreadcrumbInfo(trail.getL10n().getString("Breadcrumb.Reply"), "");
	}
}
