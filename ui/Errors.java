/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui;

import plugins.FMSPlugin.FMS;
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
}
