package plugins.Freetalk.WoT;

import freenet.support.CurrentTimeUTC;
import plugins.Freetalk.Config;
import plugins.Freetalk.PersistentTask;
import plugins.Freetalk.ui.web.WebPage;


/**
 * This task checks every day whether the own identity which owns it needs to solve introduction puzzles to be visible to the web of trust.
 * An identity is considered to be needing introduction if it has written at least 1 message and if less than 5 identities trust it (the value of 5 is configurable).
 */
public class IntroduceIdentityTask extends PersistentTask {
	
	/**
	 * How often do we check whether this identity needs to solve introduction puzzles?
	 */
	public static final long PROCESSING_INTERVAL = 1 * 24 * 60 * 60 * 1000;
	
	protected int mPuzzlesToSolve;

	protected IntroduceIdentityTask(WoTOwnIdentity myOwner) {
		super(myOwner);
		
		mPuzzlesToSolve = 0;
	}

	@Override
	public synchronized WebPage display() {
		// FIXME implement
		
		
		
		return null;
	}

	@Override
	public synchronized void process() {
		WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		long now = CurrentTimeUTC.getInMillis(); 
		
		try {
			if(mFreetalk.getMessageManager().getMessagesBy(mOwner).size() > 0) {
				int minimumTrusterCount = mFreetalk.getConfig().getInt(Config.MINIMUM_TRUSTER_COUNT); 
				
				if(identityManager.getReceivedTrustsCount(mOwner) < minimumTrusterCount) {
					mPuzzlesToSolve = minimumTrusterCount * 2;  
					mNextDisplayTime = now;
				}
			}
			
			mNextProcessingTime = now + PROCESSING_INTERVAL;
		} catch (Exception e) {
			mNextProcessingTime = now + PROCESSING_INTERVAL / 8; 
		}
		
		storeWithoutCommit();
	}
	
	public synchronized void onHideForSomeTime() {
		mPuzzlesToSolve = 0;
		mNextProcessingTime = CurrentTimeUTC.getInMillis() + PROCESSING_INTERVAL;
		mNextDisplayTime = Long.MAX_VALUE;
		
		storeWithoutCommit();
	}
	
	public synchronized void onPuzzleSolved() {
		if(mPuzzlesToSolve > 0) 
			--mPuzzlesToSolve;
		
		if(mPuzzlesToSolve == 0)
			mNextDisplayTime = Long.MAX_VALUE;
		
		storeWithoutCommit();
	}

}
