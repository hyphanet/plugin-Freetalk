/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Freetalk;
import freenet.support.HTMLNode;

public class Status {
	
	public static String makeStatusPage(Freetalk ft) {
		HTMLNode pageNode = ft.getPageNode();
		HTMLNode contentNode = ft.mPageMaker.getContentNode(pageNode);
		contentNode.addChild("#", "makeStatusPage");
		return pageNode.generate();
	}
}
