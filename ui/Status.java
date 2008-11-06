/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui;

import plugins.FMSPlugin.FMS;
import freenet.support.HTMLNode;

public class Status {
	
	public static String makeStatusPage(FMS fms) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild("#", "makeStatusPage");
		return pageNode.generate();
	}
}
