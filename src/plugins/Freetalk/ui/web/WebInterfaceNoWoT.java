package plugins.Freetalk.ui.web;

import plugins.Freetalk.Freetalk;
import freenet.pluginmanager.PluginHTTPException;
import freenet.support.api.HTTPRequest;


/**
 * A special web interface which is displayed as long as the WoT plugin is not available.
 * It tells the user to load it and might in the future provide basic functionalities which do not need the WoT plugin.
 * 
 * @author xor (xor@freenetproject.org)
 */
public class WebInterfaceNoWoT extends WebInterface {
		
	public WebInterfaceNoWoT(Freetalk myFreetalk) {
		super(myFreetalk);
		
		mPageMaker.removeAllNavigationLinks();
		mPageMaker.addNavigationLink(Freetalk.PLUGIN_URI + "/", "Home", "Freetalk plugin home", false, null);
		mPageMaker.addNavigationLink("/", "Back to Freenet", "Back to your Freenet node", false, null);
		mPageMaker.addNavigationLink("/plugins", "Go to plugins page", "The plugins page of Freenet", false, null);
	}

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		return new WoTIsMissingPage(this, request, false).toHTML();
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return "";
	}


}
