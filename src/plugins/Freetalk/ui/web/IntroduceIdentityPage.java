package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import freenet.clients.http.RedirectException;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class IntroduceIdentityPage extends WebPageImpl {
	
	private WoTIdentityManager mIdentityManager;

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

	@Override
	public void make() throws RedirectException {
		

	}

}
