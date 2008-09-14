/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.ui;

import plugins.FMSPlugin.FMS;
import plugins.FMSPlugin.FMSPlugin;
import freenet.support.HTMLNode;

public class Welcome {
	
	public static String makeWelcomePage(FMS fms) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild(createWelcomeBox(fms));
		contentNode.addChild(createOverviewBox(fms));
		contentNode.addChild(createBackupHintBox(fms));
		return pageNode.generate();
	}
	
	private static HTMLNode createWelcomeBox(FMS fms) {
		HTMLNode welcomeBox = fms.pm.getInfobox("Welcome");
		HTMLNode welcomeContent = fms.pm.getContentNode(welcomeBox);
		welcomeContent.addChild("P", "Welcome to GenTec Labs. This is our last experiment: cloning FMS.");
		welcomeContent.addChild("P", "Things happens you didn't expect? Call 0800-GordonFreeman for rescue");
		return welcomeBox;
	}

	private static HTMLNode createOverviewBox(FMS fms) {
		HTMLNode overviewBox = fms.pm.getInfobox("Overview");
		HTMLNode overviewContent = fms.pm.getContentNode(overviewBox);
		HTMLNode list = overviewContent.addChild("ul");
		list.addChild(new HTMLNode("li", "Own Identities: " + fms.countOwnIdentities()));
		list.addChild(new HTMLNode("li", "Known Identities: " + fms.countIdentities()));
		list.addChild(new HTMLNode("li", "WoT plugin: " + (fms.isWoTpresent()?"":"not ") + "found"));
		return overviewBox;
	}

	private static HTMLNode createBackupHintBox(FMS fms) {
		HTMLNode bhBox = fms.pm.getInfobox("The boring backup reminder");
		HTMLNode bhContent = fms.pm.getContentNode(bhBox);
		bhContent.addChild("P", "You can not turn me off, because I'm boring. :P");
		bhContent.addChild("P", "Don't forget to backup your data. You find the buttons below.");
		bhContent.addChild(createExportBox(fms));
		bhContent.addChild(createImportBox(fms));
		return bhBox;
	}
	
	private static HTMLNode createExportBox(FMS fms) {
		HTMLNode exportBox = fms.pm.getInfobox("Export");
		HTMLNode exportContent = fms.pm.getContentNode(exportBox);
		HTMLNode exportForm = fms.pr.addFormChild(exportContent, FMSPlugin.SELF_URI + "/exportDB", "exportForm");
		exportForm.addChild("#", "Export the database (Identities etc pp) to xml file. \u00a0 ");
		exportForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "export", "Export" });
		exportForm.addChild("br");
		exportForm.addChild("#", "Store the backup at a safe place. You can reimport it at any node and continue flaming...");
		return exportBox;
	}
	
	private static HTMLNode createImportBox(FMS fms) {
		HTMLNode importBox = fms.pm.getInfobox("Import");
		HTMLNode importContent = fms.pm.getContentNode(importBox);
		HTMLNode importForm = fms.pr.addFormChild(importContent, FMSPlugin.SELF_URI + "/importDB", "importForm");
		importForm.addChild("#", "Choose xml file to import.\u00a0");
		importForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "file", "filename", "" });
		importForm.addChild("#", "\u00a0");
		importForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "import", "Import" });
		importForm.addChild("br");
		importForm.addChild("#", "You should only try to import files that was exported with the function above. Otherwise call the rescue number.");
		return importBox;
	}
}
