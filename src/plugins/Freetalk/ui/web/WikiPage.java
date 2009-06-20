/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;
import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Wiki;
import plugins.Freetalk.Board.MessageReference;
import freenet.clients.http.RedirectException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor
 */
public final class WikiPage extends WebPageImpl {

	private String mPage;
	private Wiki mWiki;

	public WikiPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchMessageException {
		super(myWebInterface, viewer, request);
		mPage = request.getParam("page");
		if(mPage.equals("")) {
			mPage = "Start";
		}
		mWiki = new Wiki(viewer, mFreetalk, mPage);
		if(!mWiki.doesExist()) {
			throw new NoSuchMessageException();
		}
	}

	public final void make() throws RedirectException {
		if(mOwnIdentity == null)
			throw new RedirectException(logIn);
		HTMLNode test = addContentBox(mPage);
		addEditButton(test);
		test.addChild("#", mWiki.getText());
	}

	private void addEditButton(HTMLNode parent) {
		parent = parent.addChild("div", "style", "float:right");
		HTMLNode newReplyForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/editWiki", "EditWiki");
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "Page", mPage});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "Edit" });
	}
}
