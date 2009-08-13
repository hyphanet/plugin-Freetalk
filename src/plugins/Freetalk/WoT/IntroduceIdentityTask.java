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

	protected IntroduceIdentityTask(WoTOwnIdentity myOwner) {
		super(myOwner);
	}

	@Override
	public WebPage display() {
		// FIXME implement
		
		return null;
	}

	@Override
	public void process() {
		WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		long now = CurrentTimeUTC.getInMillis(); 
		
		try {
			if(mFreetalk.getMessageManager().getMessagesBy(mOwner).size() > 0 && 
					identityManager.getReceivedTrustsCount(mOwner) < mFreetalk.getConfig().getInt(Config.MINIMUM_TRUSTER_COUNT)) {
				
				mNextDisplayTime = now;
			}
			
			mNextProcessingTime = now + PROCESSING_INTERVAL;
		} catch (Exception e) {
			mNextProcessingTime = now + PROCESSING_INTERVAL / 8; 
		}
		
	}

}
