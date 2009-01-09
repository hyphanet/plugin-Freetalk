/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageFetcher;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageXML;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.Logger;
import freenet.support.io.NativeThread;

/**
 * @author xor
 *
 */
public class WoTMessageFetcher extends MessageFetcher {
	
	protected static final int STARTUP_DELAY = 1 * 60 * 1000;
	protected static final int THREAD_PERIOD = 15 * 60 * 1000; /* FIXME: tweak before release */
	private static final int PARALLEL_MESSAGE_FETCH_COUNT = 128;
	
	private final WoTIdentityManager mIdentityManager;
	private final WoTMessageManager mMessageManager;
	
	private Random mRandom;
	
	private final ObjectContainer db;
	
	private final Hashtable<ClientGetter, WoTMessageList> mMessageLists = new Hashtable<ClientGetter, WoTMessageList>(PARALLEL_MESSAGE_FETCH_COUNT * 2);

	public WoTMessageFetcher(Node myNode, HighLevelSimpleClient myClient, ObjectContainer myDB, String myName, WoTIdentityManager myIdentityManager, WoTMessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		db = myDB;
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
		mRandom = mNode.fastWeakRandom;
		start();
		Logger.debug(this, "Message fetcher started.");
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
		Logger.debug(this, "Message fetcher loop running ...");

		Logger.debug(this, "Message fetcher loop finished.");
	}
	
	private void fetchMessages(WoTMessageList list) {
		for(MessageList.MessageReference ref : list) {
			if(!ref.wasDownloaded()) {
				try {
					fetchMessage(list, ref.getURI());
				}
				catch(Exception e) {
					Logger.error(this, "Error while trying to fetch message " + ref.getURI(), e);
				}
			}
		}
	}

	private void fetchMessage(WoTMessageList list, FreenetURI uri) throws FetchException {
		FetchContext fetchContext = mClient.getFetchContext();
		fetchContext.maxSplitfileBlockRetries = 2;
		fetchContext.maxNonSplitfileRetries = 2;
		ClientGetter g = mClient.fetch(uri, -1, this, this, fetchContext);
		//g.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS); /* pluginmanager defaults to interactive priority */
		addFetch(g);
		mMessageLists.put(g, list);
		Logger.debug(this, "Trying to fetch message from " + uri);
	}

	@Override
	public synchronized void onSuccess(FetchResult result, ClientGetter state) {
		Logger.debug(this, "Fetched message: " + state.getURI());
		
		try {
			Message message = MessageXML.decode(mMessageManager, result.asBucket().getInputStream(), mMessageLists.get(state), state.getURI());
			mMessageManager.onMessageReceived(message);
		}
		catch (Exception e) {
			Logger.error(this, "Parsing failed for message " + state.getURI(), e);
			/* FIXME: Mark non-parseable messages (in the MessageList) so that they do not block the download queue */ 
		}
		finally {
			removeFetch(state); /* FIXME: This was in the try{} block somewhere else in the FT/WoT code. Move it to finally{} there, too */
		}
	}
	
	@Override
	public synchronized void onFailure(FetchException e, ClientGetter state) {
		/* TODO: Handle DNF in some reasonable way. Mark the messages in the MessageList maybe.
		if(e.getMode() == FetchException.DATA_NOT_FOUND) {
		
		}
		*/
		
		Logger.error(this, "Downloading message " + state.getURI() + " failed.", e);
		removeFetch(state);
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	protected void removeFetch(ClientGetter g) {
		super.removeFetch(g);
		mMessageLists.remove(g);
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
