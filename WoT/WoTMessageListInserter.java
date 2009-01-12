package plugins.Freetalk.WoT;

import java.util.Collection;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.Logger;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageListInserter;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnMessage;

public class WoTMessageListInserter extends MessageListInserter {

	public WoTMessageListInserter(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager,
			MessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		start();
		// TODO Auto-generated constructor stub
	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Collection<BaseClientPutter> createInsertStorage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getPriority() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected long getSleepTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected long getStartupDelay() {
		// TODO Auto-generated method stub
		return 0;
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
