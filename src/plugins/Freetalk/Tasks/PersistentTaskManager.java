package plugins.Freetalk.Tasks;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.exceptions.DuplicateTaskException;
import plugins.Freetalk.exceptions.NoSuchTaskException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.CurrentTimeUTC;
import freenet.support.Executor;
import freenet.support.Logger;

public class PersistentTaskManager implements Runnable {
	
	protected ExtObjectContainer mDB;
	
	protected Executor mExecutor;
	
	public PersistentTaskManager(ExtObjectContainer myDB, Executor myExecutor) {
		assert(myDB != null);
		assert(myExecutor != null);
		
		mDB = myDB;
		mExecutor = myExecutor;
	}
	
	public void run() {

	}
	
	public void terminate() {

	}
	
	@SuppressWarnings("unchecked")
	protected synchronized void deleteExpiredTasks(long currentTime) {
		Query q = mDB.query();
		
		q.constrain(PersistentTask.class);
		q.descend("mDeleteTime").constrain(currentTime).smaller();
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
	
	@SuppressWarnings("unchecked")
	protected synchronized void proccessTasks(long currentTime) {
		Query q = mDB.query();
		
		q.constrain(PersistentTask.class);
		q.descend("mNextProcessingTime").constrain(currentTime).smaller();
		ObjectSet<PersistentTask> pendingTasks = q.execute();
		
		for(PersistentTask task : pendingTasks) {
			synchronized(mDB.lock()) {
				try {
					task.process();
					task.storeWithoutCommit();
					mDB.commit();
				}
				catch(RuntimeException e) {
					Logger.error(this, "Error while processing a task", e);
					mDB.rollback();
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public synchronized PersistentTask getTask(String id) throws NoSuchTaskException {
		Query q = mDB.query();
		
		q.constrain(PersistentTask.class);
		q.descend("mID").constrain(id);
		ObjectSet<PersistentTask> result = q.execute();
		
		switch(result.size()) {
			case 1:
				return result.next();
			case 0:
				throw new NoSuchTaskException(id);
			default:
				throw new DuplicateTaskException(id, result.size());
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
		
		q.descend("mNextDisplayTime").orderDescending();
		
		return q.execute();
	}

}
