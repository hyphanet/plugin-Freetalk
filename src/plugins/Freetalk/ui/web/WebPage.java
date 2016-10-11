/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.net.URISyntaxException;

import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.support.HTMLNode;

/**
 * Interface specifying what a WebPage should do.
 * 
 * @author Julien Cornuwel (batosai@freenetproject.org)
 */
public interface WebPage {
	
	/**
	 * Actually generates the page's content.
	 * @throws URISyntaxException 
	 * @throws  
	 * @throws RedirectException 
	 */
	public void make() throws RedirectException;
	
	/**
	 * @return the HTML code of this WebPage.
	 * @throws RedirectException 
	 */
	public String toHTML(ToadletContext ctx) throws RedirectException;
	
	/**
	 * Adds this WebPage to the given page as a HTMLNode.
	 * @throws RedirectException 
	 */
	public abstract void addToPage(HTMLNode contentNode) throws RedirectException;
}
