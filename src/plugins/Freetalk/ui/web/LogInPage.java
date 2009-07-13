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

	public LogInPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
		// TODO Auto-generated constructor stub
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
		HTMLNode welcomeBox = addContentBox("Welcome to Freetalk");
		welcomeBox.addChild("p", "Freetalk is a pseudo-anonymous messaging system based on Freenet. It is very similar to internet forums " +
				"and newsgroups. Pseudo-anonymous means that you post your messages using an 'identity' which is uniquely identitfied by it's " +
				"Freetalk address which consists of a nickname and an unique cryptography key.");
		HTMLNode p = welcomeBox.addChild("p", "Because you can keep the private part of the cryptography key (the insert URI) secret, only you can post under " + 
				"your Freetalk address, which means that everyone knows that all messages from your identity probably come from the same " + 
				"author, therefore you are "); p.addChild("b", "pseudo"); p.addChild("#", "-anonymous.");
		welcomeBox.addChild("p", "But due to the nature of Freenet, nobody will know who is the real person inserting the messages of your identity, which " +
				"means that you as a real person are still an ").addChild("b", "anonymous author.");
	}
	
	private final void makeLoginBox() {
		HTMLNode loginBox = addContentBox("Log in");
	
		Iterator<FTOwnIdentity> iter = mFreetalk.getIdentityManager().ownIdentityIterator();
		/*
		if(!iter.hasNext()) {
			loginBox.addChild("p", "Sorry, Freetalk has not yet downloaded your own identities from the WoT plugin. Please wait 1-2 minutes.");
			HTMLNode p = loginBox.addChild("p", "If you have not created an own identity yet, please go to the ");
			p.addChild("a", "href", "/plugins/plugins.WoT.WoT", "WoT web interface");
			p.addChild("#", " and create an own identity there. Do not forget to introduce it to others by solving introduction puzzles.");
			return;
		}
		*/
		
		HTMLNode selectForm = addFormChild(loginBox, Freetalk.PLUGIN_URI + "/LogIn", "LogIn");
		HTMLNode selectBox = selectForm.addChild("select", "name", "OwnIdentityID");
		while(iter.hasNext()) {
			FTOwnIdentity ownIdentity = iter.next();
			selectBox.addChild("option", "value", ownIdentity.getID(), ownIdentity.getShortestUniqueName(40));
		}
		selectForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", "Log in" });
	}
	
	private void makeCreateIdentityBox() {
		HTMLNode createIdentityBox = addContentBox("Create an own identity");
		createIdentityBox.addChild("a", "href", Freetalk.PLUGIN_URI + "/CreateIdentity", "You can create another own identity here");
	}
}
