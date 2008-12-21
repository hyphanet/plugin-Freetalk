/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor, saces
 */
public final class Welcome extends WebPageImpl {
	
	public Welcome(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
		// TODO Auto-generated constructor stub
	}

	public final void make() {
		makeWelcomeBox();
		makeOverviewBox();
	}
	
	private final void makeWelcomeBox() {
		HTMLNode welcomeBox = getContentBox("Welcome");
		welcomeBox.addChild("p", "IMPORTANT NOTE: All messages you post with the current Freetalk release are considered as testing messages and will NOT be readable by the first stable release. This is necessary so that we could change internal stuff completely if there is a need to do so.");
		welcomeBox.addChild("p", "To use Freetalk, set up a connection with your newsreader to localhost port 1199.");
		welcomeBox.addChild("p", "As the account name, specify the nickname of an own identity and as the e-mail address specify the Freetalk address. You can look it up on the own identities page. A password is not required.");
	}

	private final void makeOverviewBox() {
		HTMLNode overviewBox = getContentBox("Overview");
		HTMLNode list = overviewBox.addChild("ul");
		list.addChild(new HTMLNode("li", "Known Identities: " + mFreetalk.getIdentityManager().countKnownIdentities()));
		list.addChild(new HTMLNode("li", "Messages waiting to be sent: " + mFreetalk.getMessageManager().countUnsentMessages()));
	}

	/*
	private static HTMLNode createBackupHintBox(Freetalk ft) {
		HTMLNode bhBox = ft.mPageMaker.getInfobox("The boring backup reminder");
		HTMLNode bhContent = ft.mPageMaker.getContentNode(bhBox);
		bhContent.addChild("P", "You can not turn me off, because I'm boring. :P");
		bhContent.addChild("P", "Don't forget to backup your data. You find the buttons below.");
		bhContent.addChild(createExportBox(ft));
		bhContent.addChild(createImportBox(ft));
		return bhBox;
	}
	*/
	
	/*
	private static HTMLNode createExportBox(Freetalk ft) {
		HTMLNode exportBox = ft.mPageMaker.getInfobox("Export");
		HTMLNode exportContent = ft.mPageMaker.getContentNode(exportBox);
		HTMLNode exportForm = ft.mPluginRespirator.addFormChild(exportContent, Freetalk.PLUGIN_URI + "/exportDB", "exportForm");
		exportForm.addChild("#", "Export the database (Identities etc pp) to xml file. \u00a0 ");
		exportForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "export", "Export" });
		exportForm.addChild("br");
		exportForm.addChild("#", "Store the backup at a safe place. You can reimport it at any node and continue flaming...");
		return exportBox;
	}
	*/
	
	/*
	private static HTMLNode createImportBox(Freetalk ft) {
		HTMLNode importBox = ft.mPageMaker.getInfobox("Import");
		HTMLNode importContent = ft.mPageMaker.getContentNode(importBox);
		HTMLNode importForm = ft.mPluginRespirator.addFormChild(importContent, Freetalk.PLUGIN_URI + "/importDB", "importForm");
		importForm.addChild("#", "Choose xml file to import.\u00a0");
		importForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "file", "filename", "" });
		importForm.addChild("#", "\u00a0");
		importForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "import", "Import" });
		importForm.addChild("br");
		importForm.addChild("#", "You should only try to import files that was exported with the function above. Otherwise call the rescue number.");
		return importBox;
	}
	*/
}
