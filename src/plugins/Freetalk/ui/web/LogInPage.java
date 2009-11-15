/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.Iterator;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import freenet.clients.http.RedirectException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class LogInPage extends WebPageImpl {
    
    private static final String[] l10nBoldSubstitutionInput = new String[] { "bold", "/bold" };
    private static final String[] l10nBoldSubstitutionOutput = new String[] { "<b>", "</b>" };

	public LogInPage(WebInterface myWebInterface, HTTPRequest request) {
		super(myWebInterface, null, request);
	}

	public void make() throws RedirectException {
		makeWelcomeBox();
		if(mFreetalk.getIdentityManager().ownIdentityIterator().hasNext()) {
			makeLoginBox();
			makeCreateIdentityBox();
		} else {
			new CreateIdentityWizard(mWebInterface, mRequest).addToPage(mContentNode);
		}
	}

	private final void makeWelcomeBox() {
	    HTMLNode aChild;
		HTMLNode welcomeBox = addContentBox(Freetalk.getBaseL10n().getString("LoginPage.WelcomeHeader"));
		welcomeBox.addChild("p", Freetalk.getBaseL10n().getString("LoginPage.WelcomeText1"));
		aChild = welcomeBox.addChild("p"); // allow bold in this section
		Freetalk.getBaseL10n().addL10nSubstitution(aChild, "LoginPage.WelcomeText2", l10nBoldSubstitutionInput, l10nBoldSubstitutionOutput);
		aChild = welcomeBox.addChild("p"); // allow bold in this section
		Freetalk.getBaseL10n().addL10nSubstitution(aChild, "LoginPage.WelcomeText3", l10nBoldSubstitutionInput, l10nBoldSubstitutionOutput);
	}
	
	private final void makeLoginBox() {
		HTMLNode loginBox = addContentBox(Freetalk.getBaseL10n().getString("LoginPage.LogInHeader"));
	
		Iterator<FTOwnIdentity> iter = mFreetalk.getIdentityManager().ownIdentityIterator();
		
		HTMLNode selectForm = addFormChild(loginBox, Freetalk.PLUGIN_URI + "/LogIn", "LogIn");
		HTMLNode selectBox = selectForm.addChild("select", "name", "OwnIdentityID");
		while(iter.hasNext()) {
			FTOwnIdentity ownIdentity = iter.next();
			selectBox.addChild("option", "value", ownIdentity.getID(), ownIdentity.getShortestUniqueName(40));
		}
		selectForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", Freetalk.getBaseL10n().getString("LoginPage.LogInButton") });
	}
	
	protected static final void addLoginButton(WebPageImpl page, HTMLNode contentNode, FTOwnIdentity identity) {
		HTMLNode logInForm = page.addFormChild(contentNode, Freetalk.PLUGIN_URI + "/LogIn", "LogIn");
		logInForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "OwnIdentityID", identity.getID() });
		logInForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", Freetalk.getBaseL10n().getString("LoginPage.LogInButton") });
	}
	
	private void makeCreateIdentityBox() {
		HTMLNode createIdentityBox = addContentBox(Freetalk.getBaseL10n().getString("LoginPage.CreateOwnIdentityHeader"));
		HTMLNode aChild = createIdentityBox.addChild("p"); 
        Freetalk.getBaseL10n().addL10nSubstitution(
                aChild, 
                "LoginPage.CreateOwnIdentityText", 
                new String[] { "link", "/link" }, 
                new String[] { "<a href=\""+Freetalk.PLUGIN_URI+"/CreateIdentity\">", "</a>" });
	}
}
