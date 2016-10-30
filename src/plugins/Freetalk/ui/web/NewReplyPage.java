/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.HashSet;

import plugins.Freetalk.Board;
import plugins.Freetalk.Identity;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageURI;
import plugins.Freetalk.Quoting;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessage;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageRatingException;
import plugins.Freetalk.exceptions.NotTrustedException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class NewReplyPage extends WebPageImpl {

	public static final byte INCREASE_TRUST_VALUE = 1;

	public static final byte DECREASE_TRUST_VALUE = -1;


	private final SubscribedBoard mBoard;
	private final BoardThreadLink mParentThread;
	private final Message mParentMessage;

	public NewReplyPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n)
	throws NoSuchBoardException, NoSuchMessageException {
		super(myWebInterface, viewer, request, _baseL10n);
		mBoard = mFreetalk.getMessageManager().getSubscription(mOwnIdentity, request.getPartAsStringFailsafe("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH));

		mParentThread = mBoard.getThreadLink(request.getPartAsStringFailsafe("ParentThreadID", 128));
		mParentMessage = mFreetalk.getMessageManager().get(request.getPartAsStringFailsafe("ParentMessageID", 128)); /* TODO: adapt to maximal ID length when it has been decided */
	}

	@Override public void make() {
		makeBreadcrumbs();

		if((mRequest.isPartSet("CreateReply") || mRequest.isPartSet("CreatePreview")) && mRequest.getMethod().equals("POST")) {
			HashSet<Board> boards = new HashSet<Board>();
			boards.add(mBoard.getParentBoard());

			String replySubject = "";
			String replyText = "";
			byte selectedMessageRating = 0;

			try {
				 replySubject = mRequest.getPartAsStringFailsafe("ReplySubject", Message.MAX_MESSAGE_TITLE_TEXT_LENGTH * 2);
				 replyText = mRequest.getPartAsStringFailsafe("ReplyText", Message.MAX_MESSAGE_TEXT_LENGTH * 2);

				if(replySubject.length() > Message.MAX_MESSAGE_TITLE_TEXT_LENGTH)
					throw new Exception(l10n().getString("Common.Message.Subject.TooLong", "limit", Integer.toString(Message.MAX_MESSAGE_TITLE_TEXT_LENGTH)));

				if(replyText.length() > Message.MAX_MESSAGE_TEXT_LENGTH)
					throw new Exception(l10n().getString("Common.Message.Text.TooLong", "limit", Integer.toString(Message.MAX_MESSAGE_TEXT_LENGTH)));

				selectedMessageRating = getMessageRating(mRequest);

				if (mRequest.isPartSet("CreatePreview")) {
					mContentNode.addChild(new PreviewPane(mWebInterface, mOwnIdentity, mRequest, replySubject, replyText).get());
					makeNewReplyPage(replySubject, replyText, selectedMessageRating);
				}
				else {
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
						mParentMessage, boards, mBoard.getParentBoard(), mOwnIdentity, replySubject, null, replyText, null);

				HTMLNode successBox = addContentBox(l10n().getString("NewReplyPage.ReplyCreated.Header"));
				successBox.addChild("p", l10n().getString("NewReplyPage.ReplyCreated.Text"));

				HTMLNode aChild = successBox.addChild("#");
                l10n().addL10nSubstitution(
                        aChild, 
                        "NewReplyPage.ReplyCreated.BackToParentThread",
                        new String[] { "link" }, 
                        new HTMLNode[] { HTMLNode.link(ThreadPage.getURI(mBoard, mParentThread)) });

				successBox.addChild("br");

				aChild = successBox.addChild("#");
                l10n().addL10nSubstitution(
                        aChild, 
                        "NewReplyPage.ReplyCreated.BackToBoard",
                        new String[] { "link", "boardname"}, 
                        new HTMLNode[] { HTMLNode.link(BoardPage.getURI(mBoard)), HTMLNode.text(mBoard.getName()) });

				rateMessage(selectedMessageRating); // Handles the case where no rating is desired
				}
			} catch (Exception e) {
				HTMLNode alertBox = addAlertBox(l10n().getString("NewReplyPage.ReplyFailed.Header"));
				alertBox.addChild("div", e.getMessage());

				makeNewReplyPage(replySubject, replyText, selectedMessageRating);
			}
			return;
		} else {
			String subject = mParentMessage.getTitle();
			if(!subject.startsWith("Re:"))
				subject = "Re: " + subject;

			String text = Quoting.getFullQuote(mParentMessage);

			makeNewReplyPage(subject, text, null);
		}
	}

	private static byte getMessageRating(HTTPRequest request) {
		if(!request.isPartSet("RateMessage"))
			return 0;

		return (byte)request.getIntPart("RateMessage", 0);
	}

	private void rateMessage(byte value) {
		if(value == 0) // No change
			return;

		try {
			WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
			WoTMessageManager messageManager = (WoTMessageManager)mFreetalk.getMessageManager();

			synchronized(identityManager) {
			synchronized (messageManager) {
				WoTMessage message = (WoTMessage)messageManager.get(mParentMessage.getID()); // It might have be deleted meanwhile, we must re-query
				messageManager.rateMessage((WoTOwnIdentity)mOwnIdentity, message, value);
				HTMLNode successBox = addContentBox(l10n().getString("NewReplyPage.RateMessageSucceededBox.Title"));
				successBox.addChild("div", l10n().getString("NewReplyPage.RateMessageSucceededBox.Text", "value", Byte.toString(value)));
			}
			}
		} catch(Exception e) {
			HTMLNode alertBox = addAlertBox(l10n().getString("NewReplyPage.RateMessageFailedBox.Title"));
			alertBox.addChild("div", l10n().getString("NewReplyPage.RateMessageFailedBox.Text"));
			alertBox.addChild("div", e.getMessage());
		}
	}

	private void makeNewReplyPage(String replySubject, String replyText, Byte selectedMessageRating) {

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
		textBox.addChild("textarea", new String[] { "name", "cols", "rows", "style", "wrap" }, // TODO: Use a CSS stylesheet file
				new String[] { "ReplyText", "80", "30", "font-size: medium;", "soft" }, replyText);

		addRateMessageBox(newReplyForm, selectedMessageRating);

		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "CreateReply", l10n().getString("NewReplyPage.ReplyBox.SubmitButton")});
		newReplyForm.addChild(PreviewPane.createPreviewButton(l10n(), "CreatePreview"));

		HTMLNode bbcodeBox = getContentBox("BBCode");
		addBBCodeList(bbcodeBox);
		newReplyForm.addChild(bbcodeBox);
	}
	
	public static void addBBCodeList(HTMLNode box) {
		// TODO: l10n
		// TODO: Do not embed HTML below!
		box.addChild("%", "Freetalk supports <a href=\"/?_CHECKED_HTTP_=http://en.wikipedia.org/wiki/BBCode\">BBCode</a>. The following tags are supported:");
		HTMLNode list = box.addChild("ul");
		list.addChild("li", "[b] bolded text [/b]");
		list.addChild("li", "[i] italicized text [/i]");
		list.addChild("li", "[link] http://example.org [/link]");
		list.addChild("li", "[link] CHK@freenet-link [/link]");
		list.addChild("li", "[img] CHK@freenet-image-link [/img]");
		list.addChild("li", "[quote] quoted text [/quote]");
		list.addChild("li", "[quote author=name] quoted text with author [/quote]");
		list.addChild("li", "[quote author=name message=message-id] quoted text with author and message ID [/quote]");
		list.addChild("li", "[code] monospaced text [/code]");
	}

	private void addRateMessageBox(HTMLNode parent, Byte selectedMessageRating) {
		Identity identity = mParentMessage.getAuthor();

		if(identity == mOwnIdentity)
			return;

		HTMLNode rateMessageBox = getContentBox(l10n().getString(
				"NewReplyPage.RateMessageBox.Header",
				new String[] { "identityname" },
				new String[] { identity.getShortestUniqueName() }));

		parent.addChild(rateMessageBox);

		try {
			mFreetalk.getMessageManager().getMessageRating(mOwnIdentity, mParentMessage);
			rateMessageBox.addChild("#", l10n().getString("NewReplyPage.RateMessageBox.AlreadyRated"));
			return;
		} catch(NoSuchMessageRatingException e) { }

		Byte currentTrust = null;

		try {
			currentTrust = mFreetalk.getIdentityManager().getTrust((WoTOwnIdentity)mOwnIdentity, (WoTIdentity)identity);
		} catch(NotTrustedException e) {

		} catch (Exception e) {
			Logger.error(this, "Getting current trust failed", e);
			rateMessageBox.addChild("p", l10n().getString("NewReplyPage.RateMessageBox.GetTrustError")); // TODO: Show the exception & stack trace
			return;
		}

		rateMessageBox.addChild("p", l10n().getString("NewReplyPage.RateMessageBox.CurrentTrust", "value",
				(currentTrust!= null ? currentTrust.toString() : l10n().getString("Common.WebOfTrust.Trust.None"))));

		HTMLNode increaseTrustDiv = rateMessageBox.addChild("div");
		HTMLNode keepTrustDiv = rateMessageBox.addChild("div");
		HTMLNode decreaseTrustDiv = rateMessageBox.addChild("div");

		HTMLNode selectedRadio;

		if(selectedMessageRating == null)
			selectedRadio = increaseTrustDiv;
		else {
			if(selectedMessageRating > 0)
				selectedRadio = increaseTrustDiv;
			else if(selectedMessageRating == 0)
				selectedRadio = keepTrustDiv;
			else
				selectedRadio = decreaseTrustDiv;
		}

		// TODO: Move those computations to class WoTMessageRating... there it is also explained why we dont just check for currentTrust<100 ...
		final boolean increasePossible = currentTrust == null || (currentTrust+INCREASE_TRUST_VALUE)<=100;  // TODO: Use a constant
		final boolean decreasePossible = currentTrust == null || (currentTrust+DECREASE_TRUST_VALUE) >= -100;
		final boolean increaseWillUnIgnore = currentTrust != null && currentTrust<0 && (currentTrust+INCREASE_TRUST_VALUE) >= 0;
		final boolean decreaseWillIgnore = currentTrust == null || (currentTrust >=0 && (currentTrust+DECREASE_TRUST_VALUE) < 0);

		if(increasePossible) {
			HTMLNode increaseTrustRadio = increaseTrustDiv.addChild("input",
				new String[] { "type", "name", "value"},
				new String[] { "radio", "RateMessage" , Integer.toString(INCREASE_TRUST_VALUE)});

			if(selectedRadio == increaseTrustDiv)
				increaseTrustRadio.addAttribute("checked", "checked");

			if(!increaseWillUnIgnore)
				increaseTrustDiv.addChild("#", l10n().getString("NewReplyPage.RateMessageBox.IncreaseTrust", "value", Byte.toString(INCREASE_TRUST_VALUE)));
			else
				increaseTrustDiv.addChild("#", l10n().getString("NewReplyPage.RateMessageBox.IncreaseTrustToPositive", "value", Byte.toString(INCREASE_TRUST_VALUE)));
		}

		HTMLNode keepTrustRadio = keepTrustDiv.addChild("input",
			new String[] { "type", "name", "value" },
			new String[] { "radio", "RateMessage" , "0"});

		if(selectedRadio == keepTrustDiv ||
				(selectedRadio == increaseTrustDiv && !increasePossible) ||
				(selectedRadio == decreaseTrustDiv && !decreasePossible))  {
			keepTrustRadio.addAttribute("checked", "checked");
		}

		keepTrustDiv.addChild("#", l10n().getString("NewReplyPage.RateMessageBox.KeepTrust"));


		if(decreasePossible) {
			HTMLNode decreaseTrustRadio = decreaseTrustDiv.addChild("input",
					new String[] { "type", "name", "value" },
					new String[] { "radio", "RateMessage" , Integer.toString(DECREASE_TRUST_VALUE)});

			if(selectedRadio == decreaseTrustDiv)
				decreaseTrustRadio.addAttribute("checked", "checked");

			if(!decreaseWillIgnore)
				decreaseTrustDiv.addChild("#", l10n().getString("NewReplyPage.RateMessageBox.DecreaseTrust", "value", Byte.toString(DECREASE_TRUST_VALUE)));
			else
				decreaseTrustDiv.addChild("#", l10n().getString("NewReplyPage.RateMessageBox.DecreaseTrustToNegative", "value", Byte.toString(DECREASE_TRUST_VALUE)));
		}

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
