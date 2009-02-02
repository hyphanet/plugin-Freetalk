/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.ArrayList;
import java.util.Iterator;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import freenet.clients.http.PageMaker;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Basic implementation of the WebPage interface. It contains common features
 * for every WebPages.
 * 
 * @author Julien Cornuwel (batosai@freenetproject.org), xor
 */
public abstract class WebPageImpl implements WebPage {

	/** The URI the plugin can be accessed from. */
	protected static final String SELF_URI = Freetalk.PLUGIN_URI;

	protected final WebInterface mWebInterface;
	
	/** A reference to Freetalk */
	protected final Freetalk mFreetalk;
	
	/** The node's pagemaker */
	protected final PageMaker mPM;
	
	/** The request performed by the user */
	protected final HTTPRequest mRequest;
	
	/** List of all content boxes */
	protected final ArrayList<HTMLNode> mContentBoxes;
	
	/**
	 * The FTOwnIdentity which is viewing this page.
	 */
	protected final FTOwnIdentity mOwnIdentity;

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

		mWebInterface = myWebInterface;
		
		mFreetalk = mWebInterface.getFreetalk();
		
		mPM = mWebInterface.getPageMaker();
		
		mOwnIdentity = viewer;

		mRequest = request;

		mContentBoxes = new ArrayList<HTMLNode>(32); /* FIXME: Figure out a reasonable value */
	}

	/**
	 * Generates the HTML code that will be sent to the browser.
	 * 
	 * @return HTML code of the page.
	 */
	public final String toHTML() {
		HTMLNode pageNode;
		if(mOwnIdentity != null)
			pageNode = mPM.getPageNode(Freetalk.PLUGIN_TITLE + " - " + mOwnIdentity.getFreetalkAddress(), null);
		else
			pageNode = mPM.getPageNode(Freetalk.PLUGIN_TITLE, null);
		addToPage(pageNode);
		return pageNode.generate();
	}
	
	/**
	 * Adds this WebPage to the given page as a HTMLNode.
	 */
	public final void addToPage(HTMLNode pageNode) {
		make();
		
		HTMLNode contentNode = mPM.getContentNode(pageNode);

		// We add every ContentBoxes
		Iterator<HTMLNode> contentBox = mContentBoxes.iterator();
		while (contentBox.hasNext())
			contentNode.addChild(contentBox.next());
	}

	/**
	 * Adds a new InfoBox to the WebPage.
	 * 
	 * @param title The title of the desired InfoBox
	 * @return the contentNode of the newly created InfoBox
	 */
	protected final HTMLNode addContentBox(String title) {
		HTMLNode box = mPM.getInfobox(title);
		mContentBoxes.add(box);
		return mPM.getContentNode(box);
	}
	
	/**
	 * Get a new Infobox but do not add it to the page. Can be used for putting Infoboxes inside Infoboxes.
	 * @param title The title of the desired Infobox
	 * @return the contentNode of the newly created Infobox
	 */
	protected final HTMLNode getContentBox(String title) {
		return mPM.getContentNode(mPM.getInfobox(title));
	}
	
	protected final HTMLNode getAlertBox(String title) {
		HTMLNode box = mPM.getInfobox("infobox-alert", title);
		mContentBoxes.add(box);
		return mPM.getContentNode(box);
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
}