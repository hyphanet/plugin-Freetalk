/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.tasks;

import java.util.UUID;

import plugins.Freetalk.DBUtil;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.ui.web.WebInterface;
import plugins.Freetalk.ui.web.WebPage;

import com.db4o.ext.ExtObjectContainer;

import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;

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
	
	protected transient ExtObjectContainer mDB;
	
	
	protected PersistentTask(FTOwnIdentity myOwner) {
		if(myOwner == null)
			throw new NullPointerException();
		
		mID = UUID.randomUUID().toString();
		mOwner = myOwner;
		mNextProcessingTime = CurrentTimeUTC.getInMillis();
		mNextDisplayTime = Long.MAX_VALUE;
		mDeleteTime = Long.MAX_VALUE;
	}
	
	public void initializeTransient(ExtObjectContainer db, Freetalk myFreetalk) {
		mDB = db;
		mFreetalk = myFreetalk;
	}

	/**
	 * ATTENTION: The process method must synchronize on this PersistentTask and then on the database lock when storing
	 * modifications to itself.
	 */
	protected abstract void process();
	
	/**
	 * ATTENTION: Returned web page objects MUST NOT synchronize on the identity manager or message manager.
	 * The reason is that WebPageImpl.toHTML() locks the PersistentTaskManager before calling display() but does not lock
	 * the IdentityManager and MessageManager before - the locking order requires those to be locked before the task manager.
	 */
	public abstract WebPage display(WebInterface myWebInterface);
	
	/**
	 * Called by the WebPage when the user clicks the "Hide for some time" button.
	 */
	public abstract void onHideForSomeTime();
	
	public void storeWithoutCommit() {
		try {
			DBUtil.checkedActivate(mDB, this, 3); // TODO: Figure out a suitable depth.

			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			mDB.store(this);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(mDB, this, e);
		}
	}
	
	public synchronized void storeAndCommit() {
		synchronized(mDB.lock()) {
			storeWithoutCommit();
			mDB.commit(); Logger.debug(this, "COMMITED.");
		}
	}
	
	protected void deleteWithoutCommit() {
		try {
			DBUtil.checkedActivate(mDB, this, 3); // TODO: Figure out a suitable depth.
			
			DBUtil.checkedDelete(mDB, this);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(mDB, this, e);
		}
	}

}
