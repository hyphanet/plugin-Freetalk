/**
 * This code is part of WoT, a plugin for Freenet. It is distributed 
 * under the GNU General Public License, version 2 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL.
 */
package plugins.Freetalk.ui.web;

import java.util.ArrayList;
import java.util.Iterator;

import plugins.Freetalk.Freetalk;
import freenet.clients.http.PageMaker;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Basic implementation of the WebPage interface. It contains common features
 * for every WebPages.
 * 
 * @author Julien Cornuwel (batosai@freenetproject.org)
 */
public abstract class WebPageImpl implements WebPage {

	/** The URI the plugin can be accessed from. */
	protected static String SELF_URI = Freetalk.PLUGIN_URI;

	/** The node's pagemaker */
	protected PageMaker mPM;
	/** HTMLNode representing the web page */
	protected HTMLNode mPageNode;
	/** A reference to Freetalk */
	protected Freetalk mFreetalk;
	/** The request performed by the user */
	protected HTTPRequest mRequest;
	/** List of all content boxes */
	protected ArrayList<HTMLNode> mContentBoxes;

	/**
	 * Creates a new WebPageImpl. It is abstract because only a subclass can run
	 * the desired make() method to generate the content.
	 * 
	 * @param mFreetalk
	 *            a reference to Freetalk, used to get references to database,
	 *            client, whatever is needed.
	 * @param request
	 *            the request from the user.
	 */
	public WebPageImpl(Freetalk ft, HTTPRequest request) {

		this.mFreetalk = ft;
		this.mPM = mFreetalk.mPageMaker;
		this.mPageNode = mPM.getPageNode(Freetalk.PLUGIN_TITLE, null);
		this.mRequest = request;

		this.mContentBoxes = new ArrayList<HTMLNode>(32); /* FIXME: Figure out a reasonable value */
	}

	/**
	 * Generates the HTML code that will be sent to the browser.
	 * 
	 * @return HTML code of the page.
	 */
	public String toHTML() {

		HTMLNode contentNode = mPM.getContentNode(mPageNode);

		// We add every ContentBoxes
		Iterator<HTMLNode> contentBox = mContentBoxes.iterator();
		while (contentBox.hasNext())
			contentNode.addChild(contentBox.next());

		HTMLNode test = mPM.getInfobox("infobox-alert", "Test");
		test.addChild("#", "Test");
		mPageNode.addChild(test);

		// Generate the HTML output
		return mPageNode.generate();
	}

	/**
	 * Adds a new InfoBox to the WebPage.
	 * 
	 * @param title
	 *            The title of the desired InfoBox
	 * @return the contentNode of the newly created InfoBox
	 */
	protected HTMLNode getContentBox(String title) {

		HTMLNode box = mPM.getInfobox(title);
		mContentBoxes.add(box);
		return mPM.getContentNode(box);
	}
}