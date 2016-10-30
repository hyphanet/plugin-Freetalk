/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.SortedMap;
import java.util.Map.Entry;

import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.tasks.PersistentTask;
import plugins.Freetalk.tasks.PersistentTaskManager;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageMaker;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.l10n.BaseL10n;
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
	 * The OwnIdentity which is viewing this page.
	 */
	protected final OwnIdentity mOwnIdentity;
	
	protected HTMLNode mContentNode;

	protected final URI logIn;
	
    protected final BaseL10n baseL10n;
	
	/**
	 * Creates a new WebPageImpl. It is abstract because only a subclass can run
	 * the desired make() method to generate the content.
	 * @param viewer The OwnIdentity which is viewing this page.
	 * @param request
	 *            the request from the user.
	 * @param _baseL10n TODO
	 * @param mFreetalk
	 *            a reference to Freetalk, used to get references to database,
	 *            client, whatever is needed.
	 */
    @Deprecated
	public WebPageImpl(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) {
		
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

		baseL10n = _baseL10n;
	}
    
	/**
	 * @param myWebInterface The WebInterface to which this page belongs.
	 * @param viewer The OwnIdentity which is viewing this page.
	 * @param request The request from the user.
	 */
	public WebPageImpl(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request) {
		
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

		baseL10n = myWebInterface.l10n();
	}

	/**
	 * Generates the HTML code that will be sent to the browser.
	 * 
	 * @return HTML code of the page.
	 * @throws RedirectException 
	 */
	@Override public final String toHTML(ToadletContext ctx) throws RedirectException {
		PageNode page;
		if(mOwnIdentity != null)
			page = mPM.getPageNode(Freetalk.PLUGIN_TITLE + " - " + mOwnIdentity.getShortestUniqueName(), ctx);
		else
			page = mPM.getPageNode(Freetalk.PLUGIN_TITLE, ctx);
		page.addCustomStyleSheet(Freetalk.PLUGIN_URI + "/css/freetalk.css");
		page.content.addAttribute("class", "freetalk");

		if(mOwnIdentity != null && !(this instanceof TaskPage)) {
			PersistentTaskManager taskManager = mFreetalk.getTaskManager();
			
			// TODO: Use a timeout here when trying to acquire the lock, the tasks are being displayed on every page anyway, so it does not matter if they are not
			// being displayed once due to the task manager being locked.
			synchronized(taskManager) { 
				for(PersistentTask task :taskManager.getVisibleTasks(mOwnIdentity))
					task.display(mWebInterface).addToPage(page.content);
			}
		}
		
		addToPage(page.content);
		return page.outer.generate();
	}
	
	/**
	 * Adds this WebPage to the given page as a HTMLNode.
	 * @throws RedirectException 
	 */
	@Override public final void addToPage(HTMLNode contentNode) throws RedirectException {
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
	
	/**
	 * @param name The name of the input-element of the combo box.
	 * @param entries The entries of the combo box. The key of an entry in the map is the text which is displayed in the combo box to the user,
	 * 					the value of on entry is the value which is passed over http if the user submits the form with that entry selected.
	 * @param defaultValue The value (= the value of the map entry) of the entry which should be selected by default
	 */
	protected static HTMLNode getComboBox(String name, SortedMap<String, String> entries, String defaultValue) {
		HTMLNode result = new HTMLNode("select", "name", name);
		
		for(Entry<String, String> entry : entries.entrySet()) {
			if(entry.getValue().equals(defaultValue)) {
				result.addChild("option", 
						new String[] { "value", "selected" }, 
						new String[] { entry.getValue(), "selected" },
						entry.getKey());
			} else {
				result.addChild("option", "value", entry.getValue(), entry.getKey());
			}
		}
		
		return result;
	}

	protected static String maxLength(String s, int max) {
		if(s.length() > max) {
			s = s.substring(0, max-3) + "...";
		}
		return s;
	}
	
    protected BaseL10n l10n() {
        return baseL10n;
    }
}
