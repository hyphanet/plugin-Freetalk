/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class PluginTalkerBlocking implements FredPluginTalker {
	
	/**
	 * Timeout when waiting for FCP replies from WoT. This is set to a very high value because nodes which are under very heavy load sometimes cause
	 * the FCP thread to stall for very long times...
	 */
	public static final long TIMEOUT = 10 * 60 * 1000;
	
	private PluginTalker mTalker;
	
	private final Object mLock = new Object();
	
	private volatile boolean mWaitingForResult = false;
        
	private volatile Result mResult = null;

	public static class Result {
		final public SimpleFieldSet params;
		final public Bucket data;

		Result(SimpleFieldSet myParams, Bucket myData) {
			params = myParams;
			data = myData;
		}
	};

	public PluginTalkerBlocking(PluginRespirator myPR) throws PluginNotFoundException {
		mTalker = myPR.getPluginTalker(this, Freetalk.WOT_PLUGIN_NAME, Freetalk.PLUGIN_TITLE);
	}

	/**
	 * Sends a FCP message and blocks execution until the answer was received and then returns the answer.
	 * This can be used to simplify code which uses FCP very much, especially UI code which needs the result of FCP calls directly.
	 * 
	 * When using sendBlocking(), please make sure that you only ever call it for FCP functions which only send() a single result!
	 */
	public synchronized Result sendBlocking(SimpleFieldSet params, Bucket data) throws PluginNotFoundException {
		assert(mResult == null);
		assert(mWaitingForResult == false);
		
		// The "synchronized" prefix of sendBlocking ensures that only 1 send is running at once. This is necessary because the onReply will not receive
		// any information which could be used to tell to WHICH request the reply belongs.
		
		// We must synchronize on a different lock here: If we used the same lock which locks the entrance of sendBlocking(), onReply would call notifyAll()
		// which would NOT wake up threads which are in wait() first, it might as well wake up another thread which is waiting to enter sendBlocking.
		// This would cause another request to be sent while the sendBlocking() of the first request was not finished, the resulting call to onReply() could
		// overwrite the result of the first request with the result of the second one. Therefore, sendBlocking would return the wrong result.
		// So we must use two different locks: One for enforcing "one request at once" and one for wait/notifyAll.
		
		synchronized(mLock) { 
			mResult = null;
			mWaitingForResult = true;
			mTalker.send(params, data);

			long startTime = System.currentTimeMillis();

			while (mResult == null) {
				try {
					mLock.wait(TIMEOUT);
				} catch (InterruptedException e) {
				}

				if(mResult == null && (System.currentTimeMillis() - startTime) >= TIMEOUT) {
					mWaitingForResult = false;
					throw new PluginNotFoundException("Timeout while waiting for reply from target plugin, message was: " + params);
				}
			}

			Result result = mResult;
			mWaitingForResult = false;
			mResult = null;
			return result;
		}
	}

	@Override public void onReply(
			String pluginname, String indentifier, SimpleFieldSet params, Bucket data) {
		
		// TODO: This has one more synchronization issue: If an old request times out and it's sendBlocking throws an exception therefore,
		// the next call to sendBlocking might receive the late answer to the previous call as reply - that is the wrong reply!
		// What is lacking in the FCP design is a unique ID for each request.
		// I'm marking this as TO-DO and not as FIX-ME because timeouts should only happen when something is wrong anyway.
		
		synchronized(mLock) { // Synchronized for notifyAll()
			if(!mWaitingForResult) {
				Logger.error(this, "sendBlocking() received onReply too late: " + params);
				return;
			}

			if(mResult != null) {
				Logger.error(this, "sendBlocking() called for a FCP function which sends more than 1 reply, second reply was: " + params);
				return;
			}

			mResult = new Result(params, data);
			mLock.notifyAll();
		}
	}
} 

