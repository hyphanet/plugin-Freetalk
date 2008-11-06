/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui;

import plugins.Freetalk.Freetalk;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class Messages {
	
	public static String makeMessagesPage(Freetalk fms, HTTPRequest request ) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild("#", "makeMessagesPage");
		return pageNode.generate();
	}


}
