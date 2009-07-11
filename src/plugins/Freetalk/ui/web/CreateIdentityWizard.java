/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import freenet.keys.FreenetURI;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class CreateIdentityWizard extends WebPageImpl {

	/* Step 1: Choose URI */
	private FreenetURI[] mIdentityURI = null; /*
											 * insert URI at index 0 and request
											 * URI at index 1
											 */

	/* Step 2: Choose Nickname */
	private String mIdentityNickname = null;

	/* Step 3: Set preferences */
	private Boolean mIdentityPublishesTrustList = null;

	/*
	 * TODO: Evaluate whether we need to ask the user whether he wants to
	 * publish puzzles during identity creation. I cannot think of any privacy
	 * problems with that. Does anyone else have an idea?
	 */
	/* private Boolean mIdentityPublishesPuzzles = null; */

	public CreateIdentityWizard(WebInterface myWebInterface, HTTPRequest request) {
		super(myWebInterface, null, request);
	}

	public void make() {
		makeCreateIdentityBox();
	}

	/*
	 * TODO: In the future this function should maybe be cleaned up to be more
	 * readable: Maybe separate it into several functions
	 */
	private void makeCreateIdentityBox() {
		HTMLNode createBox = addContentBox("Create an own identity");
		HTMLNode createForm = addFormChild(createBox, Freetalk.PLUGIN_URI + "/CreateIdentity", "CreateIdentity");
		
		boolean randomSSK = false;
		Exception requestURIproblem = null;
		Exception insertURIproblem = null;
		Exception nicknameProblem = null;
		
		/* ======== Stage 1: Parse the passed form data ====================================================================================== */
		
		int previousStep = mRequest.isPartSet("Step") ? Integer.parseInt(mRequest.getPartAsString("Step", 1)) : 0;
		
		/* Parse the "Generate random SSK?" boolean specified in step 1 */
		if(mRequest.isPartSet("GenerateRandomSSK")) {
			randomSSK = mRequest.getPartAsString("GenerateRandomSSK", 5).equals("true");
			if(randomSSK)
				mIdentityURI = mFreetalk.getPluginRespirator().getHLSimpleClient().generateKeyPair("");
		}
		
		/* Parse the URI specified in step 1 */
		if(mRequest.isPartSet("RequestURI") && mRequest.isPartSet("InsertURI")) {
			assert(randomSSK == false);
			
			mIdentityURI = new FreenetURI[2];
			
			try { mIdentityURI[0] = new FreenetURI(mRequest.getPartAsString("InsertURI", 256)); }
			catch(Exception e) { insertURIproblem = e; }
			
			try { mIdentityURI[1] = new FreenetURI(mRequest.getPartAsString("RequestURI", 256)); }
			catch(Exception e) { requestURIproblem = e; }
			
			if(insertURIproblem != null || requestURIproblem != null)
				mIdentityURI = null;
			
			/* FIXME: Check whether the URI pair is correct, i.e. if the insert URI really is one, if the request URI really is one and
			 * if the two belong together. How to do this? */
		}
		
		/* Parse the nickname specified in step 2 */
		if(mRequest.isPartSet("Nickname")) {
			try {
				mIdentityNickname = mRequest.getPartAsString("Nickname", 256);
				WoTIdentity.validateNickname(mIdentityNickname);
			}
			catch(Exception e) {
				nicknameProblem = e;
				mIdentityNickname = null;
			}
		}
		
		/* Parse the preferences specified in step 3 */
		if(previousStep == 3) { /* We cannot just use isPartSet("PublishTrustList") because it won't be set if the checkbox is unchecked */
			if(mRequest.isPartSet("PublishTrustList"))
				mIdentityPublishesTrustList = mRequest.getPartAsString("PublishTrustList", 5).equals("true");
			else
				mIdentityPublishesTrustList = false;
		}
		
		
		/* ======== Stage 2: Display the wizard stage at which we are ======================================================================== */
		
		/* Step 1: URI */
		if(mIdentityURI == null) {
			HTMLNode chooseURIbox = getContentBox("Step 1 of 3: Choose the SSK URI");
			createForm.addChild(chooseURIbox);
			
			chooseURIbox.addChild("p", "The SSK URI of your identity is a public-private keypair which uniquely identifies your identity " +
					"on the network. You can either let Freenet generate a new, random one for you or enter an existing SSK URI pair - for " +
					"example if you own a Freesite you can re-use it's URI to show others that your identity belongs to your Freesite.");
		
			if(!mRequest.isPartSet("GenerateRandomSSK")) {
				HTMLNode p = chooseURIbox.addChild("p");
				p.addChild("input", 	new String[] { "type", "name", "value" , "checked"},
										new String[] { "radio", "GenerateRandomSSK" , "true", "checked"});
				p.addChild("#", "Generate a new, random SSK keypair for the identity.");
				
				p = chooseURIbox.addChild("p");
				p.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", "GenerateRandomSSK" , "false"});
				p.addChild("#", "I want to use an existing SSK URI keypair for the identity.");
			} else {
				assert(randomSSK == false);
				
				HTMLNode p = chooseURIbox.addChild("p");
				p.addChild("input", new String[] { "type", "name", "value", "checked"},
									new String[] { "radio", "GenerateRandomSSK" , "false", "checked"});
				p.addChild("#", "I want to use an existing SSK URI keypair for the identity.");

				if(requestURIproblem != null) {
					p.addChild("p", "style", "color: red;")
					.addChild("#", "Request URI error: " + requestURIproblem.getLocalizedMessage());
				}
				
				if(insertURIproblem != null) {
					p.addChild("p", "style", "color: red;").
					addChild("#", "Insert URI error: " + insertURIproblem.getLocalizedMessage());
				}

				p.addChild("#", "Please enter the SSK URI pair:");
				p.addChild("br"); chooseURIbox.addChild("#", "Request URI: ");
				p.addChild("input",	new String[] { "type", "name", "size", "value" },
									new String[] { "text", "RequestURI", "70", mRequest.getPartAsString("RequestURI", 256) });

				p.addChild("br"); chooseURIbox.addChild("#", "Insert URI: ");
				p.addChild("input",	new String[] { "type", "name", "size", "value" },
									new String[] { "text", "InsertURI", "70", mRequest.getPartAsString("InsertURI", 256) });
			}
		}
		
		/* Step 2: Nickname */
		if(mIdentityURI != null && mIdentityNickname == null) {
			addHiddenFormData(createForm, "2");
			
			HTMLNode chooseNameBox = getContentBox("Step 2 of 3: Choose the Nickname");
			createForm.addChild(chooseNameBox);
			
			chooseNameBox.addChild("p", "The nickname of an identity cannot be changed after the identity has been created, please choose it " +
					"carefully. You cannot use spaces and some special characters in the nickname.");
			HTMLNode p = chooseNameBox.addChild("p");
			
			if(nicknameProblem != null) {
				p.addChild("p", "style", "color: red;").
				addChild("#", "Nickname error: " + nicknameProblem.getLocalizedMessage());
			}
			
			p.addChild("#", "Nickname: ");
			p.addChild("input",	new String[] { "type", "name", "size", "value" },
								new String[] { "text", "Nickname", "50", mRequest.getPartAsString("Nickname", 50) });

		}
		
		/* Step 3: Preferences */
		if(mIdentityURI != null && mIdentityNickname != null && mIdentityPublishesTrustList == null) {
			addHiddenFormData(createForm, "3");
			
			HTMLNode choosePrefsBox = getContentBox("Step 3 of 3: Choose your preferences");
			createForm.addChild(choosePrefsBox);
			
			HTMLNode tlBox = getContentBox("Trust list");
			choosePrefsBox.addChild(tlBox);
			
			HTMLNode p = tlBox.addChild("p", "Trust lists are the "); p.addChild("b", "fundament of Freetalk");
			p.addChild("#", ": You can publish a "); p.addChild("b", "trust value"); p.addChild("#", " for every identity in your trust list. " +
					"The trust value can be between -100 and +100 inclusive and is a measurement for how valuable you rate the messages of an " +
					"identity. If someone posts spam, you can give a negative trust value, if he writes useful posts, you can assign a " +
					" positive one.");
			
			p = tlBox.addChild("p", "For every identity, Freetalk will accumulate trust values which it has received by other identities and " +
					"calculate a "); p.addChild("b", "score"); p.addChild("#", " - a weighted average - from those. If the score of an " +
					"identity is negative, Freetalk will not download messages from that identity anymore (unless you tell it to do so). " +
					"Therefore, the purpose of the trust system is to "); p.addChild("b", "prevent spam"); p.addChild("#", " but it is ");
					p.addChild("b", "not to punish authors for having a different opinion than yours.");
					p.addChild("#", " Please respect that when assigning trust values.");
					
			p = tlBox.addChild("p", "Here you can choose whether Freetalk should publish a trust list for your identity. It is generally a " +
					"good idea because it helps the other users. However you can tell Freetalk not to publish your trust list if you are " +
					"concernced that it might compromise your anonymity because the trust values among several of your identities might " +
					"correlate.");
			
			p = tlBox.addChild("p");
			p.addChild("input",	new String[] { "type", "name", "value", "checked" },
								new String[] { "checkbox", "PublishTrustList", "true", "checked"});
			p.addChild("#", "Publish the trust list of the identitiy");
		}
		
		if(mIdentityURI != null && mIdentityNickname != null && mIdentityPublishesTrustList != null) {
			addHiddenFormData(createForm, "4");
			
			try {
				WoTOwnIdentity id = (WoTOwnIdentity)mFreetalk.getIdentityManager().createOwnIdentity(mIdentityNickname,
						mIdentityPublishesTrustList, true, mIdentityURI[1], mIdentityURI[0]);
						
				HTMLNode summaryBox = getContentBox("Identity created");
				createForm.addChild(summaryBox);
				
				summaryBox.addChild("a", "href", Freetalk.PLUGIN_URI + "/LogIn?OwnIdentityID=" + id.getID(),
						"Your identity was successfully created. You can log in with it now."); 
			}
			catch(Exception e) {
				HTMLNode errorBox = getAlertBox("Sorry, there was a problem creating your identity");
				createForm.addChild(errorBox);
				
				errorBox.addChild("p", e.getLocalizedMessage());
			}
			
			return; /* TODO: Instead of just returning, provide a "Go back" button if creation fails, etc. */
		}


		createForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", "Continue" });
	}

	/**
	 * Stores the form data which the user has already specified as hidden
	 * elements.
	 * 
	 * @param myForm
	 *            The HTMLNode of the parent form.
	 */
	private void addHiddenFormData(HTMLNode myForm, String step) {
		myForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "Step", step});
		
		if(mIdentityURI != null) {
			myForm.addChild("input",	new String[] { "type", "name", "value" },
										new String[] { "hidden", "InsertURI", mIdentityURI[0].toString() });
			myForm.addChild("input",	new String[] { "type", "name", "value" },
										new String[] { "hidden", "RequestURI", mIdentityURI[1].toString() });
		}

		if(mIdentityNickname != null) {
			myForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "Nickname", mIdentityNickname });
		}

		if(mIdentityPublishesTrustList != null) {
			myForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "PublishTrustList",
					mIdentityPublishesTrustList.toString() });
		}
	}

}
