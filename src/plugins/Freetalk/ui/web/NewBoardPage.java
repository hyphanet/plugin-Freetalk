/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.SubscribedBoard;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.l10n.ISO639_3.LanguageCode;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class NewBoardPage extends WebPageImpl {

	public NewBoardPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) {
		super(myWebInterface, viewer, request, _baseL10n);
	}

	public void make() throws RedirectException {
		if(mOwnIdentity == null) {
			throw new RedirectException(logIn);
		}
		
		String boardLanguage = "";
		String boardName = "";
		String boardDescription = "";
		
		if(mRequest.isPartSet("CreateBoard") && mRequest.getMethod().equals("POST")) {
			try {
				// TODO: Maybe introduce a specific limit for the language length... its not so important though, we have a limit, thats sufficient.
			    boardLanguage = mRequest.getPartAsStringThrowing("BoardLanguage", Board.MAX_BOARDNAME_TEXT_LENGTH);
				boardName = mRequest.getPartAsStringThrowing("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH);
				boardDescription = mRequest.getPartAsStringThrowing("BoardDescription", Board.MAX_BOARDNAME_TEXT_LENGTH);
				
				//if we have selected 'no linguistic content' then don't add the prefix.
				if(boardLanguage.equals("zxx"))
					boardLanguage = "";
				else
					boardLanguage += ".";
				
				final String fullBoardName = boardLanguage + boardName;
				
				mFreetalk.getMessageManager().getOrCreateBoard(fullBoardName, boardDescription);
				
				SubscribedBoard subscribedBoard = mFreetalk.getMessageManager().subscribeToBoard(mOwnIdentity, fullBoardName);
				
				HTMLNode successBox = addContentBox(l10n().getString("NewBoardPage.CreateBoardSuccess.Header"));
	            l10n().addL10nSubstitution(
	                    successBox.addChild("div"), 
	                    "NewBoardPage.CreateBoardSuccess.Text",
	                    new String[] { "link", "boardname" },
	                    new HTMLNode[] {
	                    	HTMLNode.link(BoardPage.getURI(subscribedBoard)),
	                    	HTMLNode.text(subscribedBoard.getName()) }
	                    );


				makeNewBoardPage(boardLanguage, "");
			} catch (Exception e) {
				HTMLNode alertBox = addAlertBox(l10n().getString("NewBoardPage.CreateBoardError"));
				alertBox.addChild("div", e.getMessage());
				
				makeNewBoardPage(boardLanguage, boardName);
			}
		}
		else {
			makeNewBoardPage("zxx", ""); // Encourage the usage of no language prefix by making it the default
		}
	}
	
	private HTMLNode getLanguageComboBox(String defaultLanguage) {
		final Map<String, LanguageCode> languages = Board.getAllowedLanguages();
		final SortedMap<String, String> languagesSortedByName = new TreeMap<String, String>();
		
		// TODO: Add l10n for all reference names
		
		for(LanguageCode language : languages.values()) {
			if(languagesSortedByName.put(language.referenceName + " (" + language.id + ")", language.id) != null)
				throw new RuntimeException("Duplicate language: " + language);
		}
		
		return getComboBox("BoardLanguage", languagesSortedByName, defaultLanguage);
	}
	
	private void makeNewBoardPage(String boardLanguage, String boardName) {
		HTMLNode newBoardBox = addContentBox(l10n().getString("NewBoardPage.NewBoardBox.Header"));
		
		newBoardBox.addChild("p", l10n().getString("NewBoardPage.NewBoardBox.Text"));
		
		l10n().addL10nSubstitution(newBoardBox.addChild("p"), "NewBoardPage.NewBoardBox.Text2", new String[] { "bold" }, new HTMLNode[] { new HTMLNode("b")});
		
		HTMLNode newBoardForm = addFormChild(newBoardBox, Freetalk.PLUGIN_URI + "/NewBoard", "NewBoard");
		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		
		HTMLNode languageBox = newBoardForm.addChild(getContentBox(l10n().getString("NewBoardPage.NewBoardBox.LanguageBox.Header")));
		languageBox.addChild("p", l10n().getString("NewBoardPage.NewBoardBox.LanguageBox.Text")+":");
		languageBox.addChild(getLanguageComboBox(boardLanguage));
		
		HTMLNode nameBox = newBoardForm.addChild(getContentBox(l10n().getString("NewBoardPage.NewBoardBox.BoardNameBox.Header")));
		nameBox.addChild("p", l10n().getString("NewBoardPage.NewBoardBox.BoardNameBox.Text"));
		
		nameBox.addChild("input", new String[] { "type", "size", "maxlength", "name", "value"},
				new String[] {"text", "128", Integer.toString(Board.MAX_BOARDNAME_TEXT_LENGTH), "BoardName", boardName});

		HTMLNode descBox = newBoardForm.addChild(getContentBox(l10n().getString("NewBoardPage.NewBoardBox.BoardDescBox.Header")));
		descBox.addChild("p", l10n().getString("NewBoardPage.NewBoardBox.BoardDescBox.Text"));
		
		descBox.addChild("input", new String[] { "type", "size", "maxlength", "name", "value"},
				new String[] {"text", "128", Integer.toString(Board.MAX_BOARDNAME_TEXT_LENGTH), "BoardDescription", boardName});

		newBoardForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "CreateBoard", l10n().getString("NewBoardPage.NewBoardButton")});
	}
}
