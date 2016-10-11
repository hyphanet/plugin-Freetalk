/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.ArrayList;

import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;

/**
 * <p>A BreadcrumbTrail is the path of a webpage which is displayed at top of it, for example:</p>
 * 
 * <p>Freetalk > Your Boards > en.freenet</p>
 * 
 * <p>Each entry in the path is a link except the last one. So the purpose of BreadcrumbTrails is easy navigation.</p>
 * 
 * <p>Each {@link WebPage} constructs a BreadcrumbTrail by calling {@link addBreadcrumbInfo} for each entry in the path and using {@link getHTMLNode} when done.</p>
 * 
 * @author xor (xor@freenetproject.org)
 * @author gerard_
 */
public final class BreadcrumbTrail {
	// For small sizes arrays are faster than linked lists.
	protected final ArrayList<String> mTitles = new ArrayList<String>(8);
	protected final ArrayList<String> mLinks = new ArrayList<String>(8);
	
	protected final BaseL10n mL10n;

	public BreadcrumbTrail(final BaseL10n myL10n) {
		mL10n = myL10n;
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
	
	protected BaseL10n getL10n() {
		return mL10n;
	}
}
