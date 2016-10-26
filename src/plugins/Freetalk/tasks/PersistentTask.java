/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.tasks;

import java.util.UUID;

import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.ui.web.WebInterface;
import plugins.Freetalk.ui.web.WebPage;
import freenet.support.CurrentTimeUTC;
import freenet.support.codeshortification.IfNull;

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
// @IndexedClass // I can't think of any query which would need to get all PersistentTask objects.
public abstract class PersistentTask extends Persistent {
	
	@IndexedField
	protected final String mID;
	
	@IndexedField
	protected final OwnIdentity mOwner;
	
	@IndexedField
	protected long mNextProcessingTime;
	
	@IndexedField
	protected long mNextDisplayTime;
	
	@IndexedField
	protected long mDeleteTime;
	
	
	protected PersistentTask(OwnIdentity myOwner) {
		mID = UUID.randomUUID().toString();
		mOwner = myOwner;
		mNextProcessingTime = CurrentTimeUTC.getInMillis();
		mNextDisplayTime = Long.MAX_VALUE;
		mDeleteTime = Long.MAX_VALUE;
	}

	@Override public void databaseIntegrityTest() throws Exception {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		
		IfNull.thenThrow(mID, "mID");
		
		// Owner may be null.
	}
	
	public OwnIdentity getOwner() throws NoSuchIdentityException {
		checkedActivate(1);
		if(mOwner == null)
			throw new NoSuchIdentityException();
		
		if(mOwner instanceof Persistent)
			((Persistent)mOwner).initializeTransient(mFreetalk);
		
		return mOwner;
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

	@Override protected void storeWithoutCommit() {
		try {
			checkedActivate(1);
			
			// We cannot throw because PersistentTasks are usually created within the transaction which is used to create the owner.
			// if(mOwner != null) DBUtil.throwIfNotStored(mDB, mOwner);

			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
	
	protected synchronized void storeAndCommit() {
		synchronized(Persistent.transactionLock(mDB)) {
			storeWithoutCommit();
			checkedCommit(this);
		}
	}

	@Override protected void deleteWithoutCommit() {
		deleteWithoutCommit(1);
	}

}
