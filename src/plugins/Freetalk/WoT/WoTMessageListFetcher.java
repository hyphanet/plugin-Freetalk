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
import plugins.Freetalk.MessageListFetcher;

public class WoTMessageListFetcher extends MessageListFetcher {

	public WoTMessageListFetcher(Node myNode, HighLevelSimpleClient myClient, String myName) {
		super(myNode, myClient, myName);
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
		// TODO Auto-generated method stub

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
