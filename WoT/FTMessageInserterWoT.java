package plugins.Freetalk.WoT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.Executor;
import freenet.support.io.NativeThread;
import plugins.Freetalk.FTIdentityManager;
import plugins.Freetalk.FTMessage;
import plugins.Freetalk.FTMessageInserter;
import plugins.Freetalk.FTOwnIdentity;

public class FTMessageInserterWoT extends FTMessageInserter {

	private Random mRandom;
	
	public FTMessageInserterWoT(Node myNode, HighLevelSimpleClient myClient, String myName, FTIdentityManager myIdentityManager) {
		super(myNode, myClient, myName, myIdentityManager);
		mRandom = mNode.fastWeakRandom;
		start();
	}

	@Override
	public void postMessage(FTOwnIdentity identity, FTMessage message) {
		// TODO Auto-generated method stub

	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		return null;
	}

	@Override
	protected Collection<BaseClientPutter> createInsertStorage() {
		return new ArrayList<BaseClientPutter>(10);
	}

	@Override
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
	public void onSuccess(BaseClientPutter state) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public void onFailure(InsertException e, BaseClientPutter state) {
		// TODO Auto-generated method stub
	}

	
	/* Not needed functions*/
	
	@Override
	public void onSuccess(FetchResult result, ClientGetter state) { }
	
	@Override
	public void onFailure(FetchException e, ClientGetter state) { }
	
	@Override
	public void onGeneratedURI(FreenetURI uri, BaseClientPutter state) { }
	
	@Override
	public void onFetchable(BaseClientPutter state) { }

	@Override
	public void onMajorProgress() { }

}
