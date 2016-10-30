package plugins.Freetalk.ui.web;

import java.util.List;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.OwnIdentity;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class DeleteEmptyBoardsPage extends WebPageImpl {

	public DeleteEmptyBoardsPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) {
		super(myWebInterface, viewer, request, _baseL10n);
	}

	@Override public void make() throws RedirectException {
		if(mOwnIdentity == null)
			throw new RedirectException(logIn);

		makeBreadcrumbs();
		
		if(mRequest.isPartSet("ReallyDeleteEmptyBoards")) {
			final List<String> deletedBoards = mFreetalk.getMessageManager().deleteEmptyBoards();
			
			HTMLNode boardsDeletedBox = addContentBox(l10n().getString("DeleteEmptyBoardsPage.BoardsDeletedBox.Header"));
			boardsDeletedBox.addChild("p", l10n().getString("DeleteEmptyBoardsPage.BoardsDeletedBox.Text"));
			
			for(final String board : deletedBoards) {
				boardsDeletedBox.addChild("p", board);
			}
		} else {
			makeDeleteEmptyBoardsPage();
		}
	}

	private void makeDeleteEmptyBoardsPage() {
		HTMLNode newBoardBox = addContentBox(l10n().getString("DeleteEmptyBoardsPage.DeleteEmptyBoardsBox.Header"));
		
		newBoardBox.addChild("p", l10n().getString("DeleteEmptyBoardsPage.DeleteEmptyBoardsBox.Text"));
		
		HTMLNode buttonRow = newBoardBox.addChild("p").addChild("div", "class", "button-row");
		
		HTMLNode buttonDiv = buttonRow.addChild("div", "class", "button-row-button");
		HTMLNode reallyDeleteForm = addFormChild(buttonDiv,  Freetalk.PLUGIN_URI + "/DeleteEmptyBoards", "DeleteEmptyBoards");
		reallyDeleteForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		reallyDeleteForm.addChild("input", new String[] {"type", "name", "value"}, 
				new String[] {"submit", "ReallyDeleteEmptyBoards",
				l10n().getString("DeleteEmptyBoardsPage.DeleteEmptyBoardsBox.ReallyDeleteEmptyBoardsButton")});
		
		// TODO: Refactoring: Write a function in SelectBoardsPage for obtaining the form HTMLNode...
		buttonDiv = buttonRow.addChild("div", "class", "button-row-button");
		HTMLNode cancelForm = addFormChild(buttonDiv, Freetalk.PLUGIN_URI + "/SelectBoardsPage", "SelectBoards");
		cancelForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		cancelForm.addChild("input", new String[] {"type", "name", "value"}, 
				new String[] {"submit", "CancelDeleteEmptyBoards", l10n().getString("DeleteEmptyBoardsPage.DeleteEmptyBoardsBox.CancelButton")});
		
		// TODO: There is some breakage with the HTML, Firefox displays the buttons outside of the content box for me. Don't know what's wrong :|
	}
	
	public static void addBreadcrumb(BreadcrumbTrail trail) {
		trail.addBreadcrumbInfo(trail.getL10n().getString("DeleteEmptyBoardsPage.Breadcrumb"), Freetalk.PLUGIN_URI + "/DeleteEmptyBoards");
	}
	
	private void makeBreadcrumbs() {
		BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
		Welcome.addBreadcrumb(trail);
		BoardsPage.addBreadcrumb(trail);
		SelectBoardsPage.addBreadcrumb(trail);
		DeleteEmptyBoardsPage.addBreadcrumb(trail);
		mContentNode.addChild(trail.getHTMLNode());
	}
}
