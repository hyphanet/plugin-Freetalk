/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class LogInPage extends WebPageImpl {
    
	public LogInPage(WebInterface myWebInterface, HTTPRequest request, BaseL10n _baseL10n) {
		super(myWebInterface, null, request, _baseL10n);
	}

	@Override public void make() throws RedirectException {
		makeWelcomeBox();
		if(mFreetalk.getIdentityManager().ownIdentityIterator().hasNext()) {
			makeLoginBox();
			makeCreateIdentityBox();
		} else {
			new CreateIdentityWizard(mWebInterface, mRequest, l10n()).addToPage(mContentNode);
		}
	}

	private final void makeWelcomeBox() {
	    final String[] l10nBoldSubstitutionInput = new String[] { "bold", "/bold" };
	    final String[] l10nBoldSubstitutionOutput = new String[] { "<b>", "</b>" };
	    HTMLNode aChild;
		HTMLNode welcomeBox = addContentBox(l10n().getString("LoginPage.Welcome.Header"));
		welcomeBox.addChild("p", l10n().getString("LoginPage.Welcome.Text1"));
		aChild = welcomeBox.addChild("p"); // allow bold in this section
		l10n().addL10nSubstitution(aChild, "LoginPage.Welcome.Text2", l10nBoldSubstitutionInput, l10nBoldSubstitutionOutput);
		aChild = welcomeBox.addChild("p"); // allow bold in this section
		l10n().addL10nSubstitution(aChild, "LoginPage.Welcome.Text3", l10nBoldSubstitutionInput, l10nBoldSubstitutionOutput);
	}
	
	private final void makeLoginBox() {
		HTMLNode loginBox = addContentBox(l10n().getString("LoginPage.LogIn.Header"));
	
		HTMLNode selectForm = addFormChild(loginBox, Freetalk.PLUGIN_URI + "/LogIn", "LogIn");
		HTMLNode selectBox = selectForm.addChild("select", "name", "OwnIdentityID");
		for(WoTOwnIdentity ownIdentity : mFreetalk.getIdentityManager().ownIdentityIterator()) {
			selectBox.addChild("option", "value", ownIdentity.getID(), ownIdentity.getShortestUniqueName());
		}
		selectForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", l10n().getString("LoginPage.LogIn.Button") });
		selectForm.addChild("p", l10n().getString("LoginPage.CookiesRequired.Text"));
	}
	
	protected static final void addLoginButton(WebPageImpl page, HTMLNode contentNode, OwnIdentity identity, BaseL10n l10n) {
		HTMLNode logInForm = page.addFormChild(contentNode, Freetalk.PLUGIN_URI + "/LogIn", "LogIn");
		logInForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "OwnIdentityID", identity.getID() });
		logInForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", l10n.getString("LoginPage.LogIn.Button") });
		logInForm.addChild("p", l10n.getString("LoginPage.CookiesRequired.Text"));
	}
	
	private void makeCreateIdentityBox() {
		HTMLNode createIdentityBox = addContentBox(l10n().getString("LoginPage.CreateOwnIdentity.Header"));
		HTMLNode aChild = createIdentityBox.addChild("p"); 
        l10n().addL10nSubstitution(
                aChild, 
                "LoginPage.CreateOwnIdentity.Text", 
                new String[] { "link", "/link" }, 
                new String[] { "<a href=\""+Freetalk.PLUGIN_URI+"/CreateIdentity\">", "</a>" });
	}
}
