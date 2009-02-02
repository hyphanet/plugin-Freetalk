package plugins.Freetalk.ui.web;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class NewThreadPage extends WebPageImpl {

	private final Board mBoard;
	
	public NewThreadPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchBoardException {
		super(myWebInterface, viewer, request);
		mBoard = mFreetalk.getMessageManager().getBoardByName(request.getPartAsString("BoardName", 256)); /* FIXME: adapt to maximal board name length when it has been decided */
	}

	public void make() {
		HTMLNode threadBox = addContentBox("New thread in " + mBoard.getName());
		HTMLNode newThreadForm = addFormChild(threadBox, SELF_URI + "/NewThread", "NewThread");
		
		HTMLNode authorBox = newThreadForm.addChild(getContentBox("Author"));
		authorBox.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		authorBox.addChild("b", mOwnIdentity.getFreetalkAddress());
		
		HTMLNode subjectBox = newThreadForm.addChild(getContentBox("Subject"));
		subjectBox.addChild("input", new String[] {"type", "name", "size"}, new String[] {"text", "ThreadSubject", "100"}); /* FIXME: Find a reasonable maximal subject length and specify here and elsewhere */		
		
		HTMLNode textBox = newThreadForm.addChild(getContentBox("Text"));
		textBox.addChild("textarea", new String[] { "name", "cols", "rows" }, new String[] { "ThreadText", "80", "30" });
		
		newThreadForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "Submit"});
	}

}
