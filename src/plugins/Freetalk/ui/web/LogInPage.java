package plugins.Freetalk.ui.web;

import java.util.Iterator;

import plugins.Freetalk.FTOwnIdentity;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class LogInPage extends WebPageImpl {

	public LogInPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
		// TODO Auto-generated constructor stub
	}

	public void make() {
		makeWelcomeBox();
		makeLoginBox();
	}
	
	private final void makeWelcomeBox() {
		/* TODO: Make double-sure that the following text is short and yet makes very clear what Freetalk is about. It will be the first
		 * text which people read when they access Freetalk from FProxy. */
		HTMLNode welcomeBox = addContentBox("Welcome to Freetalk");
		welcomeBox.addChild("p", "Freetalk is a pseudo-anonymous messaging system based on Freenet. It is very similar to internet forums " +
				"and newsgroups. Pseudo-anonymous means that you post your messages using an 'identity' which is uniquely identitfied by it's " +
				"Freetalk address which consists of a nickname and an unique cryptography key.");
		HTMLNode p = welcomeBox.addChild("p", "Because you can keep the private part of the cryptography key secret, only you can post under " + 
				"your Freetalk address, which means that everyone knows that all messages from a given identity probably come from the same " + 
				"author, therefore you are "); p.addChild("b", "pseudo"); p.addChild("#", "-anonymous.");
		welcomeBox.addChild("p", "But due to the nature of Freenet, nobody will know who is inserting the messages of your identity, which " +
				"means that you as a real person are still an ").addChild("b", "anonymous author.");
	}
	
	private final void makeLoginBox() {
		HTMLNode loginBox = addContentBox("Log in");
	
		Iterator<FTOwnIdentity> iter = mFreetalk.getIdentityManager().ownIdentityIterator();
		if(!iter.hasNext()) {
			loginBox.addChild("p", "Sorry, Freetalk has not yet downloaded your own identities from the WoT plugin. Please wait 1-2 minutes.");
			HTMLNode p = loginBox.addChild("p", "If you have not created an own identity yet, please go to the ");
			p.addChild("a", "href", "/plugins/plugins.WoT.WoT", "WoT web interface");
			p.addChild("#", " and create an own identity there. Do not forget to introduce it to others by solving introduction puzzles.");
			return;
		}
		
		HTMLNode selectForm = addFormChild(loginBox, SELF_URI + "/LogIn", "LogIn");
		HTMLNode selectBox = selectForm.addChild("select", "name", "OwnIdentityID");
		while(iter.hasNext()) {
			FTOwnIdentity ownIdentity = iter.next();
			selectBox.addChild("option", "value", ownIdentity.getUID(), ownIdentity.getFreetalkAddress());				
		}
		selectForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", "Log in" });
	}
	

}
