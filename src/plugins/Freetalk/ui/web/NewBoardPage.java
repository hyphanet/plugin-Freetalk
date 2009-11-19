/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.SubscribedBoard;
import freenet.clients.http.RedirectException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class NewBoardPage extends WebPageImpl {

	public NewBoardPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
	}

	public void make() throws RedirectException {
		if(mOwnIdentity == null) {
			throw new RedirectException(logIn);
		}
		
		if(mRequest.isPartSet("CreateBoard")) {
		    final int boardLanguageLength = 8;
		    final int maxBoardNameLength = Board.MAX_BOARDNAME_TEXT_LENGTH - boardLanguageLength - 1; // +1 for the '.'
		    String boardLanguage = mRequest.getPartAsString("BoardLanguage", boardLanguageLength);
			String boardName = mRequest.getPartAsString("BoardName", maxBoardNameLength);
			String fullBoardName = boardLanguage + "." + boardName;
			
			try {
				mFreetalk.getMessageManager().getOrCreateBoard(fullBoardName);
				SubscribedBoard subscribedBoard = mFreetalk.getMessageManager().subscribeToBoard(mOwnIdentity, fullBoardName);
				HTMLNode successBox = addContentBox(Freetalk.getBaseL10n().getString("NewBoardPage.CreateBoardSuccess.Header"));
	            Freetalk.getBaseL10n().addL10nSubstitution(
	                    successBox.addChild("div"), 
	                    "NewBoardPage.CreateBoardSuccess.Text",
	                    new String[] { "link", "boardname", "/link" }, 
	                    new String[] {
	                            "<a href=\""+Freetalk.PLUGIN_URI+"/showBoard?identity=" + mOwnIdentity.getID() + "&name=" + subscribedBoard.getName()+"\">",
	                            subscribedBoard.getName(),
	                            "</a>" });

				makeNewBoardPage("en", "");
			} catch (Exception e) {
				HTMLNode alertBox = addAlertBox(Freetalk.getBaseL10n().getString("NewBoardPage.CreateBoardError"));
				alertBox.addChild("div", e.getMessage());
				
				makeNewBoardPage(boardLanguage, boardName);
			}
		}
		else {
			makeNewBoardPage("en", "");
		}
	}
	
	private void makeNewBoardPage(String boardLanguage, String boardName) {
		HTMLNode newBoardBox = addContentBox(Freetalk.getBaseL10n().getString("NewBoardPage.NewBoardBox.Header"));
		HTMLNode newBoardForm = addFormChild(newBoardBox, Freetalk.PLUGIN_URI + "/NewBoard", "NewBoard");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		
		HTMLNode languageBox = newBoardForm.addChild(getContentBox(Freetalk.getBaseL10n().getString("NewBoardPage.NewBoardBox.LanguageBox.Header")));
		languageBox.addChild("p", Freetalk.getBaseL10n().getString("NewBoardPage.NewBoardBox.LanguageBox.Text")+":");
		/* TODO: Locale.getISOLanguages() only returns the abbreviations. Figure out how to get the full names, add some function to Board.java for getting them and use them here. */
		/* For that you will also need to modify getComboBox() to take display names and values instead of only values and using them as display names */
		/* ANSWER: I doubt that there are default strings. We have to provide translated language names by ourself! */
		languageBox.addChild(getComboBox("BoardLanguage", Board.getAllowedLanguageCodes(), boardLanguage));
		
		HTMLNode nameBox = newBoardForm.addChild(getContentBox(Freetalk.getBaseL10n().getString("NewBoardPage.NewBoardBox.BoardNameBox.Header")));
		nameBox.addChild("p", Freetalk.getBaseL10n().getString("NewBoardPage.NewBoardBox.BoardNameBox.Text"));
		
		nameBox.addChild("input", new String[] { "type", "size", "maxlength", "name", "value"},
				new String[] {"text", "128", Integer.toString(Board.MAX_BOARDNAME_TEXT_LENGTH), "BoardName", boardName});
		
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "CreateBoard", Freetalk.getBaseL10n().getString("NewBoardPage.NewBoardButton")});
	}
}
