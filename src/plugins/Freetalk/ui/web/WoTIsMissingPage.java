package plugins.Freetalk.ui.web;

import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * A page which tells the user to load the WoT plugin or upgrade it.
 * 
 * 
 * @author xor
 */
public class WoTIsMissingPage extends WebPageImpl {

	private boolean mNeedsNewWoT;
	
	public WoTIsMissingPage(WebInterface myWebInterface, HTTPRequest request, boolean wotIsOutdated) {
		super(myWebInterface, null, request);
		mNeedsNewWoT = wotIsOutdated;
	}

	public void make() {
		if(mNeedsNewWoT) {
			HTMLNode box = addAlertBox("Your Web of Trust plugin is outdated");
			box.addChild("#", "This Freetalk version is incompatible with the WoT plugin version which you have installed."
					+ " Please upgrade the WoT plugin at ");
			box.addChild("a", "href", "/plugins").addChild("#", "your Freenet node's plugin page");
			box.addChild("#", ".");
			// FIXME: add "by going to the plugins page and pressing 'Reload'" after we have WoT in the official plugins list 
		} else {
			HTMLNode box = addAlertBox("Web of Trust plugin is missing");
			box.addChild("#", "Freetalk needs the 'WoT' plugin to be loaded in your Freenet node. Please go to ");
			box.addChild("a", "href", "/plugins").addChild("#", "your Freenet node's plugin page");
			box.addChild("#", " and load the WoT plugin.");
		}
	}

}
