/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import freenet.keys.FreenetURI;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class CreateIdentityWizard extends WebPageImpl {
	
	private String mIdentityNickname;
	private FreenetURI mIdentityURI;

	public CreateIdentityWizard(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
		// TODO Auto-generated constructor stub
	}

	public void make() {
		makeCreateIdentityBox();
	}

	private void makeCreateIdentityBox() {
		HTMLNode createBox = addContentBox("Create an own identity");
		
		HTMLNode createForm = addFormChild(createBox, SELF_URI + "/CreateIdentity", "CreateIdentity");
		
		if(mIdentityURI == null) {
			HTMLNode chooseURIbox = getContentBox("Step 1: Choose the SSK URI");
			createForm.addChild(chooseURIbox);
			
			chooseURIbox.addChild("p", "The SSK URI of your identity is a public-private keypair which uniquely identifies your identity " +
					"on the network. You can either let Freenet generate a new, random one for you or enter an existing SSK URI pair - for " +
					"example if you own a Freesite you can re-use it's URI to show others that your identity belongs to your Freesite.");
			HTMLNode selectURIsource = chooseURIbox.addChild("select", "name", "OwnIdentityID");
			selectURIsource.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", "GenerateRandomSSK" , "true"});
			selectURIsource.addChild("#", "Generate a new, random SSK keypair for the identity.");
			selectURIsource.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", "GenerateRandomSSK" , "false"});
			selectURIsource.addChild("#", "I want to use an existing SSK URI keypair for the identity.");
		}

		createForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", "Continue" });
	}

}
