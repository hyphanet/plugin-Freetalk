package plugins.Freetalk.tasks.WoT;

import plugins.Freetalk.Config;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.tasks.OwnMessageTask;
import plugins.Freetalk.ui.web.IntroduceIdentityPage;
import plugins.Freetalk.ui.web.WebInterface;
import plugins.Freetalk.ui.web.WebPage;
import freenet.l10n.BaseL10n;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;


/**
 * This task checks every day whether the own identity which owns it needs to solve introduction puzzles to be visible to the web of trust.
 * An identity is considered to be needing introduction if it has written at least 1 message and if less than 5 identities trust it (the value of 5 is configurable).
 */
public class IntroduceIdentityTask extends OwnMessageTask {
	
	/**
	 * How often do we check whether this identity needs to solve introduction puzzles?
	 */
	public static final long PROCESSING_INTERVAL = 1 * 24 * 60 * 60 * 1000;
	
	/**
	 * If an error happens, we try again soon.
	 */
	public static final long PROCESSING_INTERVAL_SHORT = 10 * 60 * 1000;
	
	protected int mPuzzlesToSolve;
	
	protected BaseL10n baseL10n;

	public IntroduceIdentityTask(WoTOwnIdentity myOwner, BaseL10n _baseL10n) {
		super(myOwner);
		
		mPuzzlesToSolve = 0;
		baseL10n = _baseL10n;
	}

	public synchronized WebPage display(WebInterface myWebInterface) {
		return new IntroduceIdentityPage(myWebInterface, (WoTOwnIdentity)mOwner, mID, mPuzzlesToSolve, baseL10n);
	}

	public synchronized void process() {
		WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		long now = CurrentTimeUTC.getInMillis(); 
		
		try {
			MessageManager messageManager = mFreetalk.getMessageManager();
			
			// We must tell the user to solve puzzles if he as written a message ...
			if(messageManager.getOwnMessagesBy(mOwner).size() > 0  
				|| messageManager.getMessagesBy(mOwner).size() > 0) { // Also check for messages which are not stored as own messages anymore.  
				
				int minimumTrusterCount = mFreetalk.getConfig().getInt(Config.MINIMUM_TRUSTER_COUNT); 
				
				// ... and if he has not received enough trust values.
				if(identityManager.getReceivedTrustsCount(mOwner) < minimumTrusterCount) {
					mPuzzlesToSolve = minimumTrusterCount * 2;  
					mNextDisplayTime = now;
					mNextProcessingTime = Long.MAX_VALUE; // Task is in display mode now, no need to proccess it anymore
					storeAndCommit();
					return;
				}
				
			}
			
			mNextProcessingTime = now + PROCESSING_INTERVAL;
			storeAndCommit();
			return;
			
		} catch (Exception e) {
			mNextProcessingTime = now + PROCESSING_INTERVAL_SHORT;
			Logger.error(this, "Error while processing an IntroduceIdentityTask", e);
		}
		
	}
	
	public synchronized void onHideForSomeTime() {
		mPuzzlesToSolve = 0;
		mNextProcessingTime = CurrentTimeUTC.getInMillis() + PROCESSING_INTERVAL;
		mNextDisplayTime = Long.MAX_VALUE;
		
		storeAndCommit();
	}
	
	public synchronized void onPuzzleSolved() {
		if(mPuzzlesToSolve > 0) 
			--mPuzzlesToSolve;
		
		if(mPuzzlesToSolve == 0) {
			mNextProcessingTime = CurrentTimeUTC.getInMillis() + PROCESSING_INTERVAL;
			mNextDisplayTime = Long.MAX_VALUE;
		}
		
		storeAndCommit();
	}
	
	public synchronized int getNumberOfPuzzlesToSolve() {
		return mPuzzlesToSolve;
	}

}
