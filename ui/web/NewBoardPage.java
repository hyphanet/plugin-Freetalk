package plugins.Freetalk.ui.web;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class NewBoardPage extends WebPageImpl {

	public NewBoardPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
		// TODO Auto-generated constructor stub
	}

	public void make() {
		HTMLNode newBoardBox = addContentBox("Create a new Board");
		HTMLNode newBoardForm = addFormChild(newBoardBox, SELF_URI + "/NewBoard", "NewBoard");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		
		HTMLNode languageBox = newBoardForm.addChild(getContentBox("Language"));
		/* TODO: Locale.getISOLanguages() only returns the abbreviations. Figure out how to get the full names, add some function to Board.java for getting them and use them here. */
		/* For that you will also need to modify getComboBox() to take display names and values instead of only values and using them as display names */
		languageBox.addChild(getComboBox("BoardLanguage", Board.getAllowedLanguageCodes(), "en"));
		
		HTMLNode nameBox = newBoardForm.addChild(getContentBox("Board name"));
		nameBox.addChild("p", "Please try to make the name as self-explantory as possible." +
				"You should split the name in categories separated by dots whenever possible, for example \"Freenet.Support\" instead of just \"Suppport\".");
		
		nameBox.addChild("input", new String[] { "type", "size", "name", "value"}, new String[] {"text", "128", "BoardName", ""}); /* FIXME: Chose a resonable max board name length, specify it here and in Board.java */
		
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "Create the board"});
	}

}
