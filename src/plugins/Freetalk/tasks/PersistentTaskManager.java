/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.tasks;

import java.util.Random;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.IdentityManager.OwnIdentityDeletedCallback;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.exceptions.DuplicateTaskException;
import plugins.Freetalk.exceptions.NoSuchTaskException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.node.PrioRunnable;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;
import freenet.support.TrivialTicker;
import freenet.support.codeshortification.IfNull;
import freenet.support.io.NativeThread;

public class PersistentTaskManager implements PrioRunnable, OwnIdentityDeletedCallback {
	
	private static final int THREAD_PERIOD = 5 * 60 * 1000; // TODO: Make configurable.
	
	protected Freetalk mFreetalk;
	
	protected ExtObjectContainer mDB;
	
	private final TrivialTicker mTicker;
	private final Random mRandom;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(PersistentTaskManager.class);
	}
	
	
	public PersistentTaskManager(Freetalk myFreetalk, ExtObjectContainer myDB) {
		assert(myDB != null);
		
		mFreetalk = myFreetalk;
		mDB = myDB;
		
		mTicker = mFreetalk.getPluginRespirator() != null ? new TrivialTicker(mFreetalk.getPluginRespirator().getNode().executor) : null;
		mRandom = mFreetalk.getPluginRespirator() != null ? mFreetalk.getPluginRespirator().getNode().fastWeakRandom : null;
		
		mFreetalk.getIdentityManager().registerOwnIdentityDeletedCallback(this);
	}

	@Override public int getPriority() {
		return NativeThread.LOW_PRIORITY;
	}

	@Override public void run() {
		if(logDEBUG) Logger.debug(this, "Main loop running...");

		try {
			long now = CurrentTimeUTC.getInMillis();
			proccessTasks(getPendingTasks(now), now);
		
			// TODO: Its nonsense to sleep for random times and check whether a task has expired, we should rather sleep until the next task will
			// expire... Implement that!
		} finally {
			if(mTicker != null) {
				final long sleepTime =  THREAD_PERIOD/2 + mRandom.nextInt(THREAD_PERIOD);
				if(logDEBUG) Logger.debug(this, "Sleeping for " + (sleepTime / (60*1000)) + " minutes.");
				mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), sleepTime, false, true);
			}
		}
		
		if(logDEBUG) Logger.debug(this, "Main loop finished.");
	}
	
	public void start() {
		if(logDEBUG) Logger.debug(this, "Starting...");
		IfNull.thenThrow(mTicker, "Ticker may only be null in unit tests, otherwise deadlocks can happen");
		mTicker.queueTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), 0, false, true);
		if(logDEBUG) Logger.debug(this, "Started.");
	}
	
	public void terminate() {
		if(logDEBUG) Logger.debug(this, "Terminating ...");
		mTicker.shutdown();
		if(logDEBUG) Logger.debug(this, "Terminated.");
	}
	
	public void processTasksSoon() {
		if(mTicker != null) {
			mTicker.rescheduleTimedJob(this, "Freetalk " + this.getClass().getSimpleName(), 0);
			Logger.normal(this, "Scheduled task execution to be run ASAP.");
		}
	}
	
	protected synchronized void deleteExpiredTasks(long currentTime) {
		Logger.normal(this, "Deleting expired tasks...");
		final Query q = mDB.query();
		q.constrain(PersistentTask.class);
		q.descend("mDeleteTime").constrain(currentTime).smaller();;
		
		for(PersistentTask task : new Persistent.InitializingObjectSet<PersistentTask>(mFreetalk, q)) {
			synchronized(Persistent.transactionLock(mDB)) {
				try {
					task.deleteWithoutCommit();
					Persistent.checkedCommit(mDB, this);
				}
				catch(RuntimeException e) {
					Persistent.checkedRollback(mDB, this, e);
				}
			}
		}
		Logger.normal(this, "Deleting expired tasks finished.");
	}
	
	/**
	 * @param query A query which must return an resulting ObjectSet of PersistentTask.
	 * @param time The current UTC time. If the given query is time-dependent, the same time must be used there!
	 */
	protected void proccessTasks(Query query, long time) {
		deleteExpiredTasks(time);
		
		synchronized(mFreetalk.getIdentityManager()) {
		synchronized(mFreetalk.getMessageManager()) {
		synchronized(this) {
		Logger.normal(this, "Processing pending tasks...");
		for(PersistentTask task : new Persistent.InitializingObjectSet<PersistentTask>(mFreetalk, query)) {
			try {
				Logger.normal(this, "Processing task " + task);
				task.process();
				Logger.normal(this, "Processing finished.");
			}
			catch(RuntimeException e) {
				Logger.error(this, "Error while processing a task", e);
			}
		}
		Logger.normal(this, "Processing pending tasks finished.");
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
	public ObjectSet<PersistentTask> getVisibleTasks(OwnIdentity owner) {
		Query q = mDB.query();
		
		long time = CurrentTimeUTC.get().getTime();
		
		q.constrain(PersistentTask.class);
		q.descend("mNextDisplayTime").constrain(time).smaller();
		q.descend("mDeleteTime").constrain(time).greater();
		q.descend("mOwner").constrain(owner).identity();
		
		q.descend("mNextDisplayTime").orderDescending();
		
		return new Persistent.InitializingObjectSet<PersistentTask>(mFreetalk, q);
	}
	
	/**
	 * Called by the {@link IdentityManager} before an identity is deleted from the database.
	 * 
	 * Deletes all it's tasks and commits the transaction.
	 */
	@Override public synchronized void beforeOwnIdentityDeletion(OwnIdentity identity) {
		final Query q = mDB.query();
		q.constrain(PersistentTask.class);
		q.descend("mOwner").constrain(identity).identity();
		final ObjectSet<PersistentTask> tasks = new Persistent.InitializingObjectSet<PersistentTask>(mFreetalk, q);
		
		synchronized(Persistent.transactionLock(mDB)) {
			try {
				for(PersistentTask task : tasks) {
					task.deleteWithoutCommit();
				}
				
				if(logDEBUG) Logger.debug(this, "Deleted tasks of " + identity);
				Persistent.checkedCommit(mDB, this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(mDB, this, e);
			}
		}
	}

	/**
	 * Called by the {@link MessageManager} when an own message was posted.
	 * Schedules a thread to process the tasks which are related to the posted own message.
	 * Does not take any locks.
	 */
	public void onOwnMessagePosted(final OwnMessage message) {
		final Runnable r = new Runnable() {
			@Override
			public void run() {
				proccessTasks(getOwnMessageTasks((OwnIdentity)message.getAuthor()), CurrentTimeUTC.getInMillis());
			}
		};
		
		if(mTicker != null)
			mTicker.queueTimedJob(r,  1 * 1000);
		else // For unit tests only, can cause deadlocks in live code. start() won't work with mTicker==null so  we can safely do this. 
			r.run();
	}
	
	public void storeTaskWithoutCommit(PersistentTask task) {
		task.initializeTransient(mFreetalk);
		task.storeWithoutCommit();
		Logger.normal(this, "Stored task " + task);
	}

}
