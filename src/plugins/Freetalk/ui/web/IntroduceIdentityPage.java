package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import freenet.clients.http.RedirectException;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class IntroduceIdentityPage extends WebPageImpl {
	
	private WoTIdentityManager mIdentityManager;
	
	//protected final int mNumberOfPuzzles;
	
	public IntroduceIdentityPage(WebInterface myWebInterface, WoTOwnIdentity myViewer, String myTaskID, int numberOfPuzzles) {
		super(myWebInterface, myViewer, null);
		
		//mNumberOfPuzzles = numberOfPuzzles;
	}

	public IntroduceIdentityPage(WebInterface myWebInterface,
			FTOwnIdentity viewer, HTTPRequest request) {
		super(myWebInterface, viewer, request);
		
		mIdentityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		if(request.isPartSet("SolvePuzzles")) {
			
			int idx = 0;
			
			while(request.isPartSet("id" + idx)) {
				String id = request.getPartAsString("id" + idx, 128);
				String solution = request.getPartAsString("Solution" + id, 32); /* TODO: replace "32" with the maximal solution length */
				
				if(!solution.equals("")) {
				
					try {
						mIdentityManager.solveIntroductionPuzzle((WoTOwnIdentity)mOwnIdentity, id, solution);
					}
					catch(Exception e) {
						/* The identity or the puzzle might have been deleted here */
						Logger.error(this, "solveIntroductionPuzzle() failed", e);
					}
				}
				++idx;
			}
		}
	}

	public void make() throws RedirectException {
		HTMLNode contentBox = addAlertBox("Introduce your identity");
		
		contentBox.addChild("p", "You have not received enough trust values from other identities: Your messages will not be seen by others." +
				" You have to solve the following puzzles to get trusted by other identities, then your messages will be visible to the most identities: ");
	}

}
