/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Collection;
import java.util.HashSet;
import java.util.Random;

import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageFetcher;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.io.NativeThread;

/**
 * @author xor
 *
 */
public class WoTMessageFetcher extends MessageFetcher {
	
	private static final int PARALLEL_MESSAGE_FETCH_COUNT = 128;
	
	private Random mRandom;

	public WoTMessageFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager) {
		super(myNode, myClient, myName, myIdentityManager);
		mRandom = mNode.fastWeakRandom;
		start();
	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		return new HashSet<ClientGetter>(PARALLEL_MESSAGE_FETCH_COUNT * 2);
	}

	@Override
	protected Collection<BaseClientPutter> createInsertStorage() {
		return null;
	}

	public int getPriority() {
		return NativeThread.NORM_PRIORITY;
	}
	
	@Override
	protected long getStartupDelay() {
		return STARTUP_DELAY/2 + mRandom.nextInt(STARTUP_DELAY);
	}
	
	@Override
	protected long getSleepTime() {
		return THREAD_PERIOD/2 + mRandom.nextInt(THREAD_PERIOD);
	}

	@Override
	protected void iterate() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSuccess(FetchResult result, ClientGetter state) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public void onFailure(FetchException e, ClientGetter state) {
		// TODO Auto-generated method stub
	}
	
	
	/* Not needed functions, called for inserts */

	@Override
	public void onGeneratedURI(FreenetURI uri, BaseClientPutter state) { }
	
	@Override
	public void onSuccess(BaseClientPutter state) { }
	
	@Override
	public void onFailure(InsertException e, BaseClientPutter state) { }
	
	@Override
	public void onFetchable(BaseClientPutter state) { }

	@Override
	public void onMajorProgress() { }

}
