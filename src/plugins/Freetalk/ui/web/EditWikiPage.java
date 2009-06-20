/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.HashSet;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Wiki;
import plugins.Freetalk.Quoting;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class EditWikiPage extends WebPageImpl {

	private final String mPage;
	private final Wiki mWiki;

	public EditWikiPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchMessageException {
		super(myWebInterface, viewer, request);
		String page;
		if(request.getMethod().equals("GET")) {
			page = request.getParam("page");
		} else {
			page = request.getPartAsString("Page", 128);
		}
		if(page.equals("")) {
			Wiki start = new Wiki(viewer, mFreetalk, "Start");
			if(!start.doesExist()) {
				page = "Start";
			} else {
				throw new NoSuchMessageException();
			}
		}
		mPage = page;
		mWiki = new Wiki(viewer, mFreetalk, mPage);
	}

	public void make() {
		if(mRequest.isPartSet("Save")) {
			String newText = mRequest.getPartAsString("NewText", 20*1024);
			mWiki.uploadNewVersion(newText);
			HTMLNode successBox = addContentBox("Changes created");
			successBox.addChild("p", "The new version of the page was put into your outbox. Freetalk will upload it after some time."); 
			
			successBox.addChild(new HTMLNode("a", "href", Freetalk.PLUGIN_URI + "/wiki?page=" + mPage, "Back to page"));
		}
		else {
			makeNewEditPage();
		}
	}

	private void makeNewEditPage() {
		HTMLNode replyBox;
		if(mWiki.doesExist()) {
			replyBox = addContentBox("Editing " + mPage);
		} else {
			replyBox = addContentBox("Creating " + mPage);
		}
		HTMLNode newReplyForm = addFormChild(replyBox, Freetalk.PLUGIN_URI + "/editWiki", "editWiki");
		newReplyForm.addChild("input", new String[] { "type", "name", "value"}, new String[] {"hidden", "Page", mPage});
		
		HTMLNode authorBox = newReplyForm.addChild(getContentBox("Author"));
		authorBox.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
		authorBox.addChild("b", mOwnIdentity.getShortestUniqueName(40));
		
		HTMLNode textBox = newReplyForm.addChild(getContentBox("Text"));
		textBox.addChild("textarea", new String[] { "name", "cols", "rows" }, new String[] { "NewText", "80", "30" }, mWiki.getText());
		
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "Save", "Save"});
		newReplyForm.addChild("#", " or back to ");
		newReplyForm.addChild("a", "href", Freetalk.PLUGIN_URI + "/wiki?page=" + mPage, mPage);
	}
}
