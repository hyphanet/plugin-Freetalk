/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.ui;

import plugins.FMSPlugin.FMS;
import plugins.FMSPlugin.FMSPlugin;
import freenet.support.HTMLNode;

public class Errors {
	
	public static String makeErrorPage(FMS fms, String error) {
		return makeErrorPage(fms, "ERROR", error);
	}

	private static String makeErrorPage(FMS fms, String title, String error) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild(createErrorBox(fms, title, error));
		return pageNode.generate();
	}

	private static HTMLNode createErrorBox(FMS fms, String title, String errmsg) {
		HTMLNode errorBox = fms.pm.getInfobox("infobox-alert", title);
		errorBox.addChild("#", errmsg);
		return errorBox;
	}

	
	public static String makeStartStopPage(FMS fms) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild(createImpExportBox(fms));
		return pageNode.generate();
	}
	
	public static String makeStatusPage(FMS fms) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);

		HTMLNode stopBox = fms.pm.getInfobox("");
		contentNode.addChild(stopBox);
		HTMLNode stopContent = fms.pm.getContentNode(stopBox);
		HTMLNode stopForm = fms.pr.addFormChild(stopContent, FMSPlugin.SELF_URI + "/startStopDealer", "dealerForm");
		stopForm.addChild("#", "Stop Dealer. \u00a0 ");
		stopForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "stopDealer", "Stop" });

		contentNode.addChild("#", "makeStatusPage");
		return pageNode.generate();
	}


	private static HTMLNode createImpExportBox(FMS fms) {
		HTMLNode importBox = fms.pm.getInfobox("Imp & Export");
		// contentNode.addChild(importBox);
		HTMLNode importContent = fms.pm.getContentNode(importBox);
		HTMLNode importForm = fms.pr.addFormChild(importContent, FMSPlugin.SELF_URI + "/impexport", "impexForm");
		importForm.addChild("#", "Imp/Export xml file. \u00a0 ");
		importForm.addChild("#", "Filename : ");
		importForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "filename", "70", "fms-kidding.xml" });
		// importForm.addChild("br");
		importForm.addChild("#", "\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0");
		importForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "importall", "Import" });
		importForm.addChild("#", "\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0");
		// importForm.addChild("BR");
		// importForm.addChild("#", "Export to Wot. \u00a0 ");
		importForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "exportall", "Export" });
		// importForm.addChild("BR");
		return importBox;
	}


}
