package plugins.Freetalk.ui.web;

import java.util.List;

import plugins.Freetalk.Configuration;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.NoSuchTaskException;
import plugins.Freetalk.tasks.WoT.IntroduceIdentityTask;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public final class IntroduceIdentityPage extends TaskPage {
	
	protected final int mNumberOfPuzzles;
	
	private final WoTIdentityManager mIdentityManager;
	
	private final boolean mWasPostponed;
	
	public IntroduceIdentityPage(WebInterface myWebInterface, WoTOwnIdentity myViewer, String myTaskID, int numberOfPuzzles, BaseL10n _baseL10n) {
		super(myWebInterface, myViewer, myTaskID, _baseL10n);
		
		mIdentityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		mNumberOfPuzzles = numberOfPuzzles;
		mWasPostponed = false;
	}

	public IntroduceIdentityPage(WebInterface myWebInterface, WoTOwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) {
		super(myWebInterface, viewer, request, _baseL10n);
		
		mIdentityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		synchronized(mFreetalk.getTaskManager()) {
			IntroduceIdentityTask myTask;
			
			try {
				myTask = (IntroduceIdentityTask)mFreetalk.getTaskManager().getTask(mTaskID);
			} catch(NoSuchTaskException e) {
				throw new IllegalArgumentException(e);
			}
			
			if(request.isPartSet("Postpone")) {
				myTask.onHideForSomeTime();
				mNumberOfPuzzles = 0;
				mWasPostponed = true;
				return;
			} else
				mWasPostponed = false;
			
			if(!request.isPartSet("SolvePuzzles")) {
				// We received an invalid request
				mNumberOfPuzzles = 0;
				return;
			}

			int idx = 0;

			while(request.isPartSet("PuzzleID" + idx)) {
				String id = request.getPartAsString("PuzzleID" + idx, 128);
				String solution = request.getPartAsString("Solution" + id, 32); /* TODO: replace "32" with the maximal solution length */

				if(!solution.trim().equals("")) {

					try {
						mIdentityManager.solveIntroductionPuzzle((WoTOwnIdentity)mOwnIdentity, id, solution);

						myTask.onPuzzleSolved();
					}
					catch(Exception e) {
						/* The identity or the puzzle might have been deleted here */
						Logger.error(this, "solveIntroductionPuzzle() failed", e);
					}
				}
				++idx;
			}
			
			mNumberOfPuzzles = myTask.getNumberOfPuzzlesToSolve();
		}
	}
	
	protected void showPuzzles() throws RedirectException {
		HTMLNode contentBox = addAlertBox(l10n().getString("IntroduceIdentityPage.IntroduceIdentity.Header"));
		
		List<String> puzzleIDs = null;
		int trusterCount = 0;
		
		try {
			puzzleIDs = mIdentityManager.getIntroductionPuzzles((WoTOwnIdentity)mOwnIdentity, mNumberOfPuzzles);
			trusterCount = mIdentityManager.getReceivedTrustsCount((WoTOwnIdentity)mOwnIdentity);
		} catch (Exception e) {
			Logger.error(this, "getIntroductionPuzzles() failed", e);

			new ErrorPage(mWebInterface, mOwnIdentity, mRequest, l10n().getString("IntroduceIdentityPage.IntroduceIdentity.ObtainingPuzzlesFailed"), e, l10n()).addToPage(contentBox);
			return;
		}
		
		HTMLNode p;
		
		if(trusterCount > 0) {
		    String trnsl = l10n().getString(
		            "IntroduceIdentityPage.IntroduceIdentity.ReceivedLowTrust",
		            "trustcount",
		            Integer.toString( mFreetalk.getConfig().getInt(Configuration.MINIMUM_TRUSTER_COUNT )));
			p = contentBox.addChild("p", trnsl);
		} else {
			p = contentBox.addChild("p", l10n().getString("IntroduceIdentityPage.IntroduceIdentity.ReceivedNoTrust"));
		}
		
		if(puzzleIDs.size() > 0 )
			p.addChild("#", " " + l10n().getString("IntroduceIdentityPage.IntroduceIdentity.SolveAvailablePuzzles") + ":");
		else
			p = contentBox.addChild("p", l10n().getString("IntroduceIdentityPage.IntroduceIdentity.SolvePuzzlesLater"));
		
		HTMLNode solveForm = mFreetalk.getPluginRespirator().addFormChild(contentBox, Freetalk.PLUGIN_URI + "/IntroduceIdentity", "SolvePuzzles");
		solveForm.addChild("input", new String[] { "type", "name", "value", }, new String[] { "hidden", "TaskID", mTaskID });

		if(puzzleIDs.size() > 0 ) {
			int counter = 0;
			for(String puzzleID : puzzleIDs) {
				// Display as much puzzles per row as fitting in the browser-window via "inline-block" style. Nice, eh?
				HTMLNode cell = solveForm.addChild("div", new String[] { "align" , "style"}, new String[] { "center" , "display: inline-block"});
	
				cell.addChild("img", "src", Freetalk.PLUGIN_URI + "/GetPuzzle?PuzzleID=" + puzzleID); 
				cell.addChild("br");
				cell.addChild("input", new String[] { "type", "name", "value", }, new String[] { "hidden", "PuzzleID" + counter, puzzleID });
				cell.addChild("input", new String[] { "type", "name", "size"}, new String[] { "text", "Solution" + puzzleID, "10" });
	
				++counter;
			}
			
			solveForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "SolvePuzzles",
					l10n().getString("IntroduceIdentityPage.IntroduceIdentity.Submit") });
		}

		solveForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "Postpone", 
				l10n().getString("IntroduceIdentityPage.IntroduceIdentity.Postpone") });
	}
	
	protected void showTaskWasPostponedMessage() {
		HTMLNode contentBox = addContentBox(l10n().getString("IntroduceIdentityPage.TaskWasPostponed.Header"));
		contentBox.addChild("#", l10n().getString("IntroduceIdentityPage.TaskWasPostponed.Text"));		
	}
	
	protected void showEnoughPuzzlesSolvedMessage() {
		HTMLNode contentBox = addContentBox(l10n().getString("IntroduceIdentityPage.EnoughPuzzlesSolved.Header"));
		contentBox.addChild("#", l10n().getString("IntroduceIdentityPage.EnoughPuzzlesSolved.Text"));
	}

	@Override public void make() throws RedirectException {
		if(mNumberOfPuzzles > 0) {
			showPuzzles();
		} else {
			if(mWasPostponed) 
				showTaskWasPostponedMessage();
			else
				showEnoughPuzzlesSolvedMessage();
		}
	}
}
