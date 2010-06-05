package plugins.Freetalk.tasks;

import java.util.Random;

import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.IdentityManager.OwnIdentityDeletedCallback;
import plugins.Freetalk.exceptions.DuplicateTaskException;
import plugins.Freetalk.exceptions.NoSuchTaskException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;

public class PersistentTaskManager implements Runnable, OwnIdentityDeletedCallback {
	
	private static final int THREAD_PERIOD = 5 * 60 * 1000; // TODO: Make configurable.
	
	protected Freetalk mFreetalk;
	
	protected ExtObjectContainer mDB;
	
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
			proccessTasks(getPendingTasks(now), now);
			
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
	
	public void start() {
		mFreetalk.getPluginRespirator().getNode().executor.execute(this, "Freetalk " + this.getClass().getSimpleName());
		Logger.debug(this, "Started.");
	}
	
	public void terminate() {
		Logger.debug(this, "Stopping ...");
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
		Logger.debug(this, "Stopped.");
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
					task.initializeTransient(mFreetalk);
					task.deleteWithoutCommit();
					Persistent.checkedCommit(mDB, this);
				}
				catch(RuntimeException e) {
					Persistent.checkedRollback(mDB, this, e);
				}
			}
		}
	}
	
	/**
	 * @param query A query which must return an resulting ObjectSet of PersistentTask.
	 * @param time The current UTC time. If the given query is time-dependent, the same time must be used there!
	 */
	@SuppressWarnings("unchecked")
	protected void proccessTasks(Query query, long time) {
		
		deleteExpiredTasks(time);
		
		synchronized(mFreetalk.getIdentityManager()) {
		synchronized(mFreetalk.getMessageManager()) {
		synchronized(this) {
		ObjectSet<PersistentTask> pendingTasks = query.execute();
		
		for(PersistentTask task : pendingTasks) {
			try {
				Logger.debug(this, "Processing task " + task);
				task.initializeTransient(mFreetalk);
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
				task.initializeTransient(mFreetalk);
				return task;
			case 0:
				throw new NoSuchTaskException(id);
			default:
				throw new DuplicateTaskException(id, result.size());
		}
	}
	
	private Query getPendingTasks(long currentTime) {
		Query q = mDB.query();
		q.constrain(PersistentTask.class);
		q.descend("mNextProcessingTime").constrain(currentTime).smaller();
		q.descend("mNextProcessingTime").orderAscending();
		return q;
	}
	
	private Query getOwnMessageTasks(OwnIdentity owner) {
		Query q = mDB.query();
		q.constrain(OwnMessageTask.class);
		q.descend("mOwner").constrain(owner).identity();
		return q;
	}
	
	/**
	 * You have to synchronize on this PersistenTaskManager when calling this function and processing the returned ObjectSet.
	 * 
	 * @return The tasks which should be displayed on the web interface right now.
	 */
	@SuppressWarnings("unchecked")
	public ObjectSet<PersistentTask> getVisibleTasks(OwnIdentity owner) {
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
	public synchronized void beforeOwnIdentityDeletion(OwnIdentity identity) {
		Query q = mDB.query();
		
		q.constrain(PersistentTask.class);
		q.descend("mOwner").constrain(identity).identity();
		ObjectSet<PersistentTask> tasks = q.execute();
		
		synchronized(mDB.lock()) {
			try {
				for(PersistentTask task : tasks) {
					task.initializeTransient(mFreetalk);
					task.deleteWithoutCommit();
				}
				
				Logger.debug(this, "Deleted tasks of " + identity);
				Persistent.checkedCommit(mDB, this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(mDB, this, e);
			}
		}
	}

	/**
	 * Called by the {@link MessageManager} when an own message was posted.
	 * 
	 * Attention: This function locks the IdentityManager and the MessageManager, therefore the message manager should not lock itself prior to calling it if it
	 * does not lock the identity manager before.
	 */
	public void onOwnMessagePosted(OwnMessage message) {
		proccessTasks(getOwnMessageTasks((OwnIdentity)message.getAuthor()), CurrentTimeUTC.getInMillis());
	}
	
	public void storeTaskWithoutCommit(PersistentTask task) {
		task.initializeTransient(mFreetalk);
		task.storeWithoutCommit();
	}

}
