/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui;

import plugins.Freetalk.Freetalk;
import freenet.support.HTMLNode;

public class Errors {
	
	public static String makeErrorPage(Freetalk ft, String error) {
		return makeErrorPage(ft, "ERROR", error);
	}

	private static String makeErrorPage(Freetalk ft, String title, String error) {
		HTMLNode pageNode = ft.getPageNode();
		HTMLNode contentNode = ft.pm.getContentNode(pageNode);
		contentNode.addChild(createErrorBox(ft, title, error));
		return pageNode.generate();
	}

	private static HTMLNode createErrorBox(Freetalk ft, String title, String errmsg) {
		HTMLNode errorBox = ft.pm.getInfobox("infobox-alert", title);
		errorBox.addChild("#", errmsg);
		return errorBox;
	}
}
