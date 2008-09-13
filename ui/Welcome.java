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
	
	private static HTMLNode createBackupHintBox(FMS fms) {
		HTMLNode bhBox = fms.pm.getInfobox("The boring backup reminder");
		HTMLNode bhContent = fms.pm.getContentNode(bhBox);
		bhContent.addChild("P", "You can not turn me off, because I'm boring. :P");
		bhContent.addChild("P", "Don't forget to backup your data. You find the buttons at the service page.");
		bhContent.addChild(new HTMLNode("a", "href", FMSPlugin.SELF_URI + "/service", "Service Page"));
		return bhBox;
	}
}
