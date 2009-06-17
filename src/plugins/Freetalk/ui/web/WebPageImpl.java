/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.net.URI;
import java.net.URISyntaxException;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Freetalk;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageMaker;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Basic implementation of the WebPage interface. It contains common features
 * for every WebPages.
 * 
 * @author Julien Cornuwel (batosai@freenetproject.org), xor
 */
public abstract class WebPageImpl implements WebPage {

	protected final WebInterface mWebInterface;
	
	/** A reference to Freetalk */
	protected final Freetalk mFreetalk;
	
	/** The node's pagemaker */
	protected final PageMaker mPM;
	
	/** The request performed by the user */
	protected final HTTPRequest mRequest;
	
	/**
	 * The FTOwnIdentity which is viewing this page.
	 */
	protected final FTOwnIdentity mOwnIdentity;
	
	protected HTMLNode mContentNode;

	protected final URI logIn;
	
	/**
	 * Creates a new WebPageImpl. It is abstract because only a subclass can run
	 * the desired make() method to generate the content.
	 * 
	 * @param mFreetalk
	 *            a reference to Freetalk, used to get references to database,
	 *            client, whatever is needed.
	 * @param viewer The FTOwnIdentity which is viewing this page.
	 * @param request
	 *            the request from the user.
	 */
	public WebPageImpl(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		
		try {
			logIn = new URI(Freetalk.PLUGIN_URI+"/LogIn");
		} catch (URISyntaxException e) {
			throw new Error(e);
		}
		
		mWebInterface = myWebInterface;
		
		mFreetalk = mWebInterface.getFreetalk();
		
		mPM = mWebInterface.getPageMaker();
		
		mOwnIdentity = viewer;

		mRequest = request;
	}

	/**
	 * Generates the HTML code that will be sent to the browser.
	 * 
	 * @return HTML code of the page.
	 * @throws RedirectException 
	 */
	public final String toHTML(ToadletContext ctx) throws RedirectException {
		PageNode page;
		if(mOwnIdentity != null)
			page = mPM.getPageNode(Freetalk.PLUGIN_TITLE + " - " + mOwnIdentity.getShortestUniqueName(40), ctx);
		else
			page = mPM.getPageNode(Freetalk.PLUGIN_TITLE, ctx);

		addToPage(page.content);
		return page.outer.generate();
	}
	
	/**
	 * Adds this WebPage to the given page as a HTMLNode.
	 * @throws RedirectException 
	 */
	public final void addToPage(HTMLNode contentNode) throws RedirectException {
		mContentNode = contentNode;
		make();
	}

	/**
	 * Adds a new InfoBox to the WebPage.
	 * 
	 * @param title The title of the desired InfoBox
	 * @return the contentNode of the newly created InfoBox
	 */
	protected final HTMLNode addContentBox(String title) {
		InfoboxNode infobox = mPM.getInfobox(title);
		HTMLNode box = infobox.outer;
		mContentNode.addChild(box);
		return infobox.content;
	}
	
	/**
	 * Get a new Infobox but do not add it to the page. Can be used for putting Infoboxes inside Infoboxes.
	 * @param title The title of the desired Infobox
	 * @return the contentNode of the newly created Infobox
	 */
	protected final HTMLNode getContentBox(String title) {
		InfoboxNode infobox = mPM.getInfobox(title);
		return infobox.outer;
	}
	
	protected final HTMLNode getAlertBox(String title) {
		InfoboxNode infobox = mPM.getInfobox("infobox-alert", title);
		return infobox.outer;
	}
	
	protected final HTMLNode addAlertBox(String title) {
		return mPM.getInfobox("infobox-alert", title, mContentNode);
	}
	
	protected HTMLNode addFormChild(HTMLNode parentNode, String target, String name) {
		return mFreetalk.getPluginRespirator().addFormChild(parentNode, target, name);
	}
	
	protected HTMLNode getComboBox(String name, String[] options, String defaultOption) {
		HTMLNode result = new HTMLNode("select", "name", name);
		
		for(String value : options) {
			if(value.equals(defaultOption))
				result.addChild("option", new String[] { "value", "selected" }, new String[] { value, "selected" }, value);
			else
				result.addChild("option", "value", value, value);
		}
		
		return result;
	}

	protected static String maxLength(String s, int max) {
		if(s.length() > max) {
			s = s.substring(0, max-3) + "...";
		}
		return s;
	}
}
