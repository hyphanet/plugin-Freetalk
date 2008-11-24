/**
 * 
 */
package plugins.Freetalk.WoT;

import java.util.Collection;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.support.Executor;
import plugins.Freetalk.FTMessageFetcher;

/**
 * @author xor
 *
 */
public class FTMessageFetcherWoT extends FTMessageFetcher {

	/**
	 * @param myExecutor
	 * @param myName
	 */
	public FTMessageFetcherWoT(Executor myExecutor, String myName) {
		super(myExecutor, myName);
		// TODO Auto-generated constructor stub
	}

	@Override
	public Collection<ClientGetter> getFetchStorage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<BaseClientPutter> getInsertStorage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getSleepTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getStartupDelay() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void iterate() {
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
