/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.LinkedList;
import java.util.List;

import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;

/**
 * 
 * @author gerard_
 *
 */
public final class BreadcrumbTrail {
	protected final List<String> mTitles;
	protected final List<String> mLinks;
	
	protected final BaseL10n mL10n;

	public BreadcrumbTrail(final BaseL10n myL10n) {
		mTitles = new LinkedList<String>();
		mLinks = new LinkedList<String>();
		
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
