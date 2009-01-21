package plugins.Freetalk.WoT;

import java.util.Collection;
import java.util.HashSet;
import java.util.Random;

import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageListInserter;
import plugins.Freetalk.MessageManager;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.io.NativeThread;

public class WoTMessageListInserter extends MessageListInserter {
	
	private static final int STARTUP_DELAY = 1 * 60 * 1000;
	private static final int THREAD_PERIOD = 5 * 60 * 1000; /* FIXME: tweak before release */
	private static final int MAX_PARALLEL_MESSAGELIST_INSERT_COUNT = 8;

	private final Random mRandom;

	public WoTMessageListInserter(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager,
			MessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		mRandom = mNode.fastWeakRandom;
		start();
	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		return null;
	}

	@Override
	protected Collection<BaseClientPutter> createInsertStorage() {
		return new HashSet<BaseClientPutter>(MAX_PARALLEL_MESSAGELIST_INSERT_COUNT * 2);
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
	public void onFailure(FetchException e, ClientGetter state) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFailure(InsertException e, BaseClientPutter state) {
//		if(e.getMode() == InsertException.COLLISION) {
//			Logger.error(this, "Message insert collided, trying to insert with higher index ...");
//			try {
//				OwnMessage message = mMessageManager.getOwnMessage(state.getURI());
//				message.incrementInsertIndex();
//				insertMessage(message);
//			}
//			catch(Exception ex) {
//				Logger.error(this, "Inserting with higher index failed", ex);
//			}
//		} else
//			Logger.error(this, "Message insert failed", e);

	}

	@Override
	public void onFetchable(BaseClientPutter state) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onGeneratedURI(FreenetURI uri, BaseClientPutter state) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMajorProgress() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSuccess(FetchResult result, ClientGetter state) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSuccess(BaseClientPutter state) {
		// TODO Auto-generated method stub

	}

}
