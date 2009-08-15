/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.UUID;

import freenet.support.CurrentTimeUTC;
import plugins.Freetalk.ui.web.WebPage;

/**
 * A PersistentTask is a user notification which is stored in the database as long as it is valid.
 * It has stored:
 * - A time after which the process() function of the task shall be called.
 * - A time after which it shall be displayed to the user on the web interface, this can be updated by process().
 * - A time after which it should be deleted, this can be updated by process().
 * 
 * An example for a task is: For each OwnIdentity, there should be a {@link WoTIntroduceIdentityTask} which tells the
 * user to solve introduction puzzles if the identity is not published in enough trust lists. 
 * 
 * @author xor (xor@freenetproject.org)
 * 
 */
public abstract class PersistentTask {
	
	protected final String mID;
	
	protected final FTOwnIdentity mOwner;
	
	protected long mNextProcessingTime;
	
	protected long mNextDisplayTime;
	
	protected long mDeleteTime;
	
	
	protected transient Freetalk mFreetalk;
	
	
	protected PersistentTask(FTOwnIdentity myOwner) {
		if(myOwner == null)
			throw new NullPointerException();
		
		mID = UUID.randomUUID().toString();
		mOwner = myOwner;
		mNextProcessingTime = CurrentTimeUTC.getInMillis();
		mNextDisplayTime = Long.MAX_VALUE;
		mDeleteTime = Long.MAX_VALUE;
	}
	
	protected void initializeTransient(Freetalk myFreetalk) { 
		mFreetalk = myFreetalk;
	}

	protected abstract void process();
	
	public abstract WebPage display();
	
	/**
	 * Called by the WebPage when the user clicks the "Hide for some time" button.
	 */
	public abstract void onHideForSomeTime();
	
	protected void storeWithoutCommit() {
		
	}
	
	protected void deleteWithoutCommit() {
		
	}

}
