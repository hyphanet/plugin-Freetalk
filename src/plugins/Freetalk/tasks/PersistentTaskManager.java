package plugins.Freetalk.tasks;

import java.util.Random;

import plugins.Freetalk.DBUtil;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.exceptions.DuplicateTaskException;
import plugins.Freetalk.exceptions.NoSuchTaskException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.CurrentTimeUTC;
import freenet.support.Executor;
import freenet.support.Logger;

public class PersistentTaskManager implements Runnable {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 1 * 60 * 1000;
	
	protected Freetalk mFreetalk;
	
	protected ExtObjectContainer mDB;
	
	protected Executor mExecutor;
	private volatile boolean isRunning = false;
	private volatile boolean shutdownFinished = false;
	private Thread mThread = null;
	
	public PersistentTaskManager(ExtObjectContainer myDB, Freetalk myFreetalk) {
		assert(myDB != null);
		
		mDB = myDB;
		mFreetalk = myFreetalk;
	}
	
	public void run() {
		Logger.debug(this, "Task manager started.");
		mThread = Thread.currentThread();
		isRunning = true;
		
		Random random = mFreetalk.getPluginRespirator().getNode().fastWeakRandom;
		
		try {
		while(isRunning) {
			Thread.interrupted();
			Logger.debug(this, "Task manager loop running...");

			long now = CurrentTimeUTC.getInMillis();
			deleteExpiredTasks(now);
			proccessTasks(now);
			
			Logger.debug(this, "Task manager loop finished.");

			try {
				Thread.sleep(THREAD_PERIOD/2 + random.nextInt(THREAD_PERIOD)); // TODO: Maybe use a Ticker implementation instead?
			}
			catch (InterruptedException e)
			{
				mThread.interrupt();
			}
		}
		}
		
		finally {
			synchronized (this) {
				shutdownFinished = true;
				Logger.debug(this, "Task manager thread exiting.");
				notify();
			}
		}
	}
	
	public void terminate() {
		Logger.debug(this, "Stopping the task manager...");
		isRunning = false;
		mThread.interrupt();
		synchronized(this) {
			while(!shutdownFinished) {
				try {
					wait();
				}
				catch (InterruptedException e) {
					Thread.interrupted();
				}
			}
		}
		Logger.debug(this, "Stopped the task manager.");
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
					task.initializeTransient(mDB, mFreetalk);
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
	protected void proccessTasks(long currentTime) {
		Query q = mDB.query();
		
		synchronized(mFreetalk.getIdentityManager()) {
		synchronized(mFreetalk.getMessageManager()) {
		synchronized(this) {
		
		q.constrain(PersistentTask.class);
		q.descend("mNextProcessingTime").constrain(currentTime).smaller();
		q.descend("mNextProcessingTime").orderAscending();
		ObjectSet<PersistentTask> pendingTasks = q.execute();
		
		for(PersistentTask task : pendingTasks) {
			try {
				Logger.debug(this, "Processing task " + task);
				task.initializeTransient(mDB, mFreetalk);
				task.process();
				Logger.debug(this, "Processing finished.");
			}
			catch(RuntimeException e) {
				Logger.error(this, "Error while processing a task", e);
			}
		}
		
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
				PersistentTask task = result.next();
				task.initializeTransient(mDB, mFreetalk);
				return task;
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
	
	/**
	 * Called by the {@link IdentityManager} before an identity is deleted from the database.
	 * 
	 * Deletes all it's tasks and commits the transaction.
	 */
	@SuppressWarnings("unchecked")
	public synchronized void onOwnIdentityDeletion(FTOwnIdentity identity) {
		Query q = mDB.query();
		
		q.constrain(PersistentTask.class);
		q.descend("mOwner").constrain(identity).identity();
		ObjectSet<PersistentTask> tasks = q.execute();
		
		synchronized(mDB.lock()) {
			try {
				for(PersistentTask task : tasks) {
					task.initializeTransient(mDB, mFreetalk);
					task.deleteWithoutCommit();
				}
				
				mDB.commit(); Logger.debug(this, "COMMITED: Deleted tasks of " + identity);
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(mDB, this, e);
			}
		}
	}

}
