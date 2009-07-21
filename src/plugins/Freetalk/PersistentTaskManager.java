package plugins.Freetalk;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.CurrentTimeUTC;
import freenet.support.Executor;
import freenet.support.Logger;

public abstract class PersistentTaskManager implements Runnable {
	
	protected ExtObjectContainer mDB;
	
	protected Executor mExecutor;
	
	public PersistentTaskManager(ExtObjectContainer myDB, Executor myExecutor) {
		assert(myDB != null);
		assert(myExecutor != null);
		
		mDB = myDB;
		mExecutor = myExecutor;
	}
	
	@SuppressWarnings("unchecked")
	protected synchronized void deleteExpiredTasks() {
		Query q = mDB.query();
		
		q.constrain(PersistentTask.class);
		q.descend("mDeleteTime").constrain(CurrentTimeUTC.get().getTime()).smaller();
		ObjectSet<PersistentTask> expiredTasks = q.execute();
		
		for(PersistentTask task : expiredTasks) {
			synchronized(mDB.lock()) {
				try {
					task.deleteWithoutCommit();
					mDB.commit();
				}
				catch(RuntimeException e) {
					Logger.error(this, "Error while trying to delete an expired task", e);
					mDB.rollback();
				}
			}
		}
	}
	
	/**
	 * You have to synchronize on this PersistenTaskManager when calling this function and processing the returned ObjectSet.
	 * 
	 * @return The tasks which should be displayed on the web interface right now.
	 */
	@SuppressWarnings("unchecked")
	public ObjectSet<PersistentTask> getVisibleTasks(FTOwnIdentity owner) {
		Query q = mDB.query();
		
		long time = CurrentTimeUTC.get().getTime();
		
		q.constrain(PersistentTask.class);
		q.descend("mNextDisplayTime").constrain(time).smaller();
		q.descend("mDeleteTime").constrain(time).greater();
		q.descend("mOwner").constrain(owner).identity();
		return q.execute();
	}
}
