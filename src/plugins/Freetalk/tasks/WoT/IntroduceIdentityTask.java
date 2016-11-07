/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.tasks.WoT;

import java.util.Date;

import plugins.Freetalk.Configuration;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.tasks.OwnMessageTask;
import plugins.Freetalk.ui.web.IntroduceIdentityPage;
import plugins.Freetalk.ui.web.WebInterface;
import plugins.Freetalk.ui.web.WebPage;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;
import freenet.support.codeshortification.IfNotEquals;


/**
 * This task checks every day whether the own identity which owns it needs to solve introduction puzzles to be visible to the web of trust.
 * An identity is considered to be needing introduction if it has written at least 1 message and if less than 5 identities trust it (the value of 5 is configurable).
 */
// @IndexedField // I can't think of any query which would need to get all IntroduceIdentityTask objects.
public class IntroduceIdentityTask extends OwnMessageTask {
	
	/**
	 * How often do we check whether this identity needs to solve introduction puzzles?
	 * ATTENTION: This interval is hardcoded in l10n IntroduceIdentityPage.TaskWasPostponed.Text
	 */
	public static transient final long PROCESSING_INTERVAL = 1 * 24 * 60 * 60 * 1000;
	
	/**
	 * If an error happens, we try again soon.
	 */
	public static transient final long PROCESSING_INTERVAL_SHORT = 10 * 60 * 1000;
	
	protected int mPuzzlesToSolve;
	
	/**
	 * True if the user pressed the "Hide until tomorrow" button and the next processing time is the time after which the warning may
	 * be displayed again.
	 * If true, the task may not display even if its processing is started because an own message was posted.
	 * If false, the task may always display  when its processing is started (= due to expiration or posting of an own message).
	 */
	protected boolean mWasHidden;
	
	public IntroduceIdentityTask(WoTOwnIdentity myOwner) {
		super(myOwner);
		
		mPuzzlesToSolve = 0;
	}

	@Override public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		checkedActivate(1);
		
		IfNotEquals.thenThrow(mDeleteTime, Long.MAX_VALUE, "mDeleteTime");
		
		final long maxDelay = CurrentTimeUTC.getInMillis() + PROCESSING_INTERVAL;
		
		if(mNextDisplayTime > maxDelay && mNextProcessingTime > maxDelay)
			throw new IllegalStateException("mNextProcessingTime == " + new Date(mNextProcessingTime));
	}

	@Override public synchronized WebPage display(WebInterface myWebInterface) {
		checkedActivate(1);
		try {
			return new IntroduceIdentityPage(myWebInterface, (WoTOwnIdentity)getOwner(), mID, mPuzzlesToSolve, myWebInterface.l10n());
		} catch (NoSuchIdentityException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Will be executed
	 * - upon timeout of the mNextProcessingTime
	 * - when an own message is posted of the owning identity.
	 */
	@Override public synchronized void process() {
		WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		checkedActivate(1);
		
		long now = CurrentTimeUTC.getInMillis(); 
		
		if(mWasHidden && now < mNextProcessingTime)
			return;

		mWasHidden = false;
		
		try {
			MessageManager messageManager = mFreetalk.getMessageManager();
	
			// We must tell the user to solve puzzles if he as written a message ...
			if(messageManager.getOwnMessagesBy(getOwner()).size() > 0   // TODO: Optimization: Create & use get(Own)MessageCount() ...
				|| messageManager.getMessagesBy(getOwner()).size() > 0) { // Also check for messages which are not stored as own messages anymore.  
				
				int minimumTrusterCount = mFreetalk.getConfig().getInt(Configuration.MINIMUM_TRUSTER_COUNT); 
				
				// ... and if he has not received enough trust values.
				if(identityManager.getReceivedTrustsCount(mOwner) < minimumTrusterCount) {
					mPuzzlesToSolve = minimumTrusterCount * 2;  
					mNextDisplayTime = now;
					mNextProcessingTime = now + PROCESSING_INTERVAL; // schedule this task again for processing because introductions are not the only way to get onto trust lists
					storeAndCommit();
					return;
				} else { 
					mPuzzlesToSolve = 0;
					mNextDisplayTime = Long.MAX_VALUE;
					mNextProcessingTime = now + PROCESSING_INTERVAL;
					mWasHidden = true; // In case an own message is posted the next processing time is ignored so we must hide it.
				}
				
			} else
				mNextProcessingTime = now + PROCESSING_INTERVAL;
			
			
			storeAndCommit();
			return;
			
		} catch (Exception e) {
			mNextProcessingTime = now + PROCESSING_INTERVAL_SHORT;
			Logger.error(this, "Error while processing an IntroduceIdentityTask", e);
		}
		
	}

	@Override public synchronized void onHideForSomeTime() {
		checkedActivate(1);
		
		mWasHidden = true;
		mPuzzlesToSolve = 0;
		mNextProcessingTime = CurrentTimeUTC.getInMillis() + PROCESSING_INTERVAL;
		mNextDisplayTime = Long.MAX_VALUE;
		
		storeAndCommit();
	}
	
	public synchronized void onPuzzleSolved() {
		checkedActivate(1);
		
		if(mPuzzlesToSolve > 0) 
			--mPuzzlesToSolve;
		
		if(mPuzzlesToSolve == 0) {
			mNextProcessingTime = CurrentTimeUTC.getInMillis() + PROCESSING_INTERVAL;
			mNextDisplayTime = Long.MAX_VALUE;
			mWasHidden = true; // In case an own message is posted the next processing time is ignored so we must hide it.
		}
		
		storeAndCommit();
	}
	
	public synchronized int getNumberOfPuzzlesToSolve() {
		checkedActivate(1);
		return mPuzzlesToSolve;
	}
}
