/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.List;
import java.util.LinkedList;

import freenet.support.HTMLNode;

/**
 * 
 * @author gerard_
 *
 */
public final class BreadcrumbTrail {
	protected final List<String> mTitles;
	protected final List<String> mLinks;

	public BreadcrumbTrail() {
		mTitles = new LinkedList<String>();
		mLinks = new LinkedList<String>();
	}

	protected void addBreadcrumbInfo(String title, String link) {
		mLinks.add(link);
		mTitles.add(title);
	}

	protected HTMLNode getHTMLNode() {
		HTMLNode result = new HTMLNode("p", "class", "breadcrumb_trail");

		for (int i = 0, count = mTitles.size()-1; i < count; i++) {
			result.addChild(new HTMLNode("a", "href", mLinks.get(i), mTitles.get(i)));
			result.addChild(new HTMLNode("span", " > "));
		}
		result.addChild(new HTMLNode("span", mTitles.get(mTitles.size()-1)));
		return result;
	}
}
