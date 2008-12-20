package plugins.Freetalk.ui.web;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class ThreadPage extends WebPageImpl {
	
	private Board mBoard;
	private Message mThread;

	public ThreadPage(Freetalk ft, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchMessageException, NoSuchBoardException {
		super(ft, viewer, request);
		mBoard = ft.getMessageManager().getBoardByName(request.getParam("board"));
		mThread = ft.getMessageManager().get(request.getParam("id"));
	}

	public void make() {
		
		HTMLNode messageBox = getContentBox("Subject: " + mThread.getTitle());
		addDebugInfo(messageBox, mThread);
		messageBox.addChild("pre", mThread.getText());
		
		for(MessageReference reference : mBoard.getAllThreadReplies(mThread)) {
			Message message = reference.getMessage();
			messageBox = getContentBox("Subject: " + message.getTitle());
			addDebugInfo(messageBox, message);
			messageBox.addChild("pre", message.getText());
		}
	}
	
	private void addDebugInfo(HTMLNode messageBox, Message message) {
		HTMLNode debugParagraph = messageBox.addChild("p");
		
		debugParagraph.addChild("#", "uri: " + message.getURI());
		
		try {
			debugParagraph.addChild("br", "parentURI: " + message.getParentURI());
		}
		catch(NoSuchMessageException e) {
			debugParagraph.addChild("br", "parentURI: null");
		}
		
		try {
			debugParagraph.addChild("br", "threadURI: " + message.getParentThreadURI());
		}
		catch(NoSuchMessageException e) {
			debugParagraph.addChild("br", "threadURI: null");
		}
	}

}
