/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.WoT.WoTIdentity;
import freenet.keys.FreenetURI;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class CreateIdentityWizard extends WebPageImpl {
	
	/* Step 1: Choose URI */
	private FreenetURI[] mIdentityURI = null; /* insert URI at index 0 and request URI at index 1 */
	
	/* Step 2: Choose Nickname */
	private String mIdentityNickname = null;

	public CreateIdentityWizard(WebInterface myWebInterface, HTTPRequest request) {
		super(myWebInterface, null, request);
	}

	public void make() {
		makeCreateIdentityBox();
	}

	/* TODO: In the future this function should maybe be cleaned up to be more readable: Maybe separate it into several functions */
	private void makeCreateIdentityBox() {
		HTMLNode createBox = addContentBox("Create an own identity");
		HTMLNode createForm = addFormChild(createBox, SELF_URI + "/CreateIdentity", "CreateIdentity");
		
		boolean randomSSK = false;
		Exception requestURIproblem = null;
		Exception insertURIproblem = null;
		Exception nicknameProblem = null;
		
		/* ======== Stage 1: Parse the passed form data ====================================================================================== */
		
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
			/* Store the parsed form data in the new form data */
			createForm.addChild("input",	new String[] { "type", "name", "value" },
											new String[] { "hidden", "InsertURI", mIdentityURI[0].toString() });
			createForm.addChild("input",	new String[] { "type", "name", "value" },
											new String[] { "hidden", "RequestURI", mIdentityURI[1].toString() });
			
			HTMLNode chooseNameBox = getContentBox("Step 2 of 3: Choose the Nickname");
			createForm.addChild(chooseNameBox);
			
			chooseNameBox.addChild("p", "The nickname of an identity cannot be changed after the identity has been created, please choose it" +
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

		createForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", "Continue" });
	}

}
