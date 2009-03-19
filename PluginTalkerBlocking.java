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
	
	private PluginTalker mTalker;
        
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
		mTalker = myPR.getPluginTalker(this, Freetalk.WOT_NAME, Freetalk.PLUGIN_TITLE);
	}

	/**
	 * Sends a FCP message and blocks execution until the answer was received and then returns the answer.
	 * This can be used to simplify code which uses FCP very much, especially UI code which needs the result of FCP calls directly.
	 * 
	 * When using sendBlocking(), please make sure that you only ever call it for FCP functions which only send() a single result!
	 */
	public synchronized Result sendBlocking(SimpleFieldSet params, Bucket data) throws PluginNotFoundException {
		assert(mResult == null);
		
		mTalker.send(params, data);

		while (mResult == null /* TODO: or timeout */) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
		
		Result result = mResult;
		mResult = null;
		return result;
	}

	public void onReply(String pluginname, String indentifier, SimpleFieldSet params, Bucket data) {
		if(mResult != null) {
			Logger.error(this, "sendBlocking() called for a FCP function which sends more than 1 reply.");
			return;
		}
		
		mResult = new Result(params, data);
		notifyAll();
	}
} 

