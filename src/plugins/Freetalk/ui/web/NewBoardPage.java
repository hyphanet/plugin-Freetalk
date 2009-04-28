/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class NewBoardPage extends WebPageImpl {

	public NewBoardPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
	}

	public void make() {
		if(mRequest.isPartSet("CreateBoard")) {
		    final int boardLanguageLength = 8;
		    final int maxBoardNameLength = Board.MAX_BOARDNAME_TEXT_LENGTH - boardLanguageLength - 1; // +1 for the '.'
		    String boardLanguage = mRequest.getPartAsString("BoardLanguage", boardLanguageLength);
			String boardName = mRequest.getPartAsString("BoardName", maxBoardNameLength);
			
			try {
				Board board = mFreetalk.getMessageManager().getOrCreateBoard(boardLanguage + "." + boardName);
				HTMLNode successBox = addContentBox("Board was created");
				successBox.addChild("div", "The board "); /* TODO: I have no idea how to make this text appear in one line without removing the <u> */
				successBox.addChild("u").addChild(new HTMLNode("a", "href", SELF_URI + "/showBoard?identity=" + mOwnIdentity.getUID() + "&name=" + board.getName(), board.getName()));
				successBox.addChild("div", " was successfully created.");
				makeNewBoardPage("en", "");
			} catch (InvalidParameterException e) {
				HTMLNode alertBox = addAlertBox("The board could not be created");
				alertBox.addChild("div", e.getMessage());
				
				makeNewBoardPage(boardLanguage, boardName);
			}
		}
		else
			makeNewBoardPage("en", "");
	}
	
	private void makeNewBoardPage(String boardLanguage, String boardName) {
		HTMLNode newBoardBox = addContentBox("Create a new board");
		HTMLNode newBoardForm = addFormChild(newBoardBox, SELF_URI + "/NewBoard", "NewBoard");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		
		HTMLNode languageBox = newBoardForm.addChild(getContentBox("Language"));
		languageBox.addChild("p", "The board name will be prefixed with the following language code:");
		/* TODO: Locale.getISOLanguages() only returns the abbreviations. Figure out how to get the full names, add some function to Board.java for getting them and use them here. */
		/* For that you will also need to modify getComboBox() to take display names and values instead of only values and using them as display names */
		languageBox.addChild(getComboBox("BoardLanguage", Board.getAllowedLanguageCodes(), boardLanguage));
		
		HTMLNode nameBox = newBoardForm.addChild(getContentBox("Board name"));
		nameBox.addChild("p", "Please try to make the name as self-explantory as possible." +
				"You should split the name in categories separated by dots whenever possible, for example \"Freenet.Support\" instead of just \"Suppport\".");
		
		nameBox.addChild("input", new String[] { "type", "size", "name", "value"}, new String[] {"text", "128", "BoardName", boardName}); /* FIXME: Chose a resonable max board name length, specify it here and in Board.java */
		
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "CreateBoard", "Create the board"});
	}

}
