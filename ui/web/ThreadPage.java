/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor
 */
public final class ThreadPage extends WebPageImpl {
	
	private final Board mBoard;
	private final Message mThread;

	public ThreadPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchMessageException, NoSuchBoardException {
		super(myWebInterface, viewer, request);
		mBoard = mFreetalk.getMessageManager().getBoardByName(request.getParam("board"));
		mThread = mFreetalk.getMessageManager().get(request.getParam("id"));
	}
	
	public final void make() {
		HTMLNode messageBox = addContentBox("Subject: " + mThread.getTitle());
		messageBox.addChild("p", "Author: " + mThread.getAuthor().getFreetalkAddress());
		addDebugInfo(messageBox, mThread);
		messageBox.addChild("pre", mThread.getText());
	
		addReplyButton(messageBox, mThread);
		
		for(MessageReference reference : mBoard.getAllThreadReplies(mThread)) {
			Message message = reference.getMessage();
			messageBox = addContentBox("Subject: " + message.getTitle());
			messageBox.addChild("p", "Author: " + message.getAuthor().getFreetalkAddress());
			addDebugInfo(messageBox, message);
			messageBox.addChild("pre", message.getText());
			addReplyButton(messageBox, message);
		}
	}
	
	private void addReplyButton(HTMLNode parent, Message parentMessage) {
		HTMLNode newReplyForm = addFormChild(parent, SELF_URI + "/NewReply", "NewReplyPage");
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ParentMessageID", parentMessage.getID()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "Reply" });
	}
	
	private void addDebugInfo(HTMLNode messageBox, Message message) {
		HTMLNode debugParagraph = messageBox.addChild("p");
		
		debugParagraph.addChild("#", "uri: " + message.getURI());
		debugParagraph.addChild("br", "ID: " + message.getID());
		try {
			debugParagraph.addChild("br", "threadID: " + message.getThreadID());
		} catch (NoSuchMessageException e) { }
		
		try {
			debugParagraph.addChild("br", "parentURI: " + message.getParentURI());
		}
		catch(NoSuchMessageException e) {
			debugParagraph.addChild("br", "parentURI: null");
		}
		
		try {
			debugParagraph.addChild("br", "threadURI: " + message.getThreadURI());
		}
		catch(NoSuchMessageException e) {
			debugParagraph.addChild("br", "threadURI: null");
		}
	}

}
