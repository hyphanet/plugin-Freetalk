/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Freetalk;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor, saces
 */
public final class Welcome extends WebPageImpl {
	
	public Welcome(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) {
		super(myWebInterface, viewer, request, _baseL10n);
	}

	@Override public final void make() throws RedirectException {
		if(mOwnIdentity == null) {
			throw new RedirectException(logIn);
		}
		makeWelcomeBox();
		makeOverviewBox();
		new BoardsPage(mWebInterface, mOwnIdentity, mRequest, l10n()).addToPage(mContentNode);
	}
	
	private final void makeWelcomeBox() {
        final String[] l10nBoldSubstitutionInput = new String[] { "bold", "/bold" };
        final String[] l10nBoldSubstitutionOutput = new String[] { "<b>", "</b>" };
	    
		HTMLNode welcomeBox = addContentBox(l10n().getString("Welcome.WelcomeBox.Header"));
		
        HTMLNode p;
        p = welcomeBox.addChild("p");
        l10n().addL10nSubstitution(p, "Welcome.WelcomeBox.Text1", l10nBoldSubstitutionInput, l10nBoldSubstitutionOutput);
        p = welcomeBox.addChild("p");
        l10n().addL10nSubstitution(p, "Welcome.WelcomeBox.Text2", l10nBoldSubstitutionInput, l10nBoldSubstitutionOutput);
        p = welcomeBox.addChild("p");
        l10n().addL10nSubstitution(p, "Welcome.WelcomeBox.Text3", l10nBoldSubstitutionInput, l10nBoldSubstitutionOutput);
	}

	private final void makeOverviewBox() {
		HTMLNode overviewBox = addContentBox(l10n().getString("Welcome.OverviewBox.Header"));
		HTMLNode list = overviewBox.addChild("ul");
		list.addChild(new HTMLNode("li", l10n().getString("Welcome.OverviewBox.MessagesWaiting") + ": " + mFreetalk.getMessageManager().countUnsentMessages()));
	}

	public static void addBreadcrumb(BreadcrumbTrail trail) {
		trail.addBreadcrumbInfo("Freetalk", Freetalk.PLUGIN_URI);
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
