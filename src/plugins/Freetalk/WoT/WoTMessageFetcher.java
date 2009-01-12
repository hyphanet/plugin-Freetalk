/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;

import plugins.Freetalk.Message;
import plugins.Freetalk.MessageFetcher;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageXML;
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
 * Periodically wakes up and fetches messages by their CHK URI. The CHK URIs of messages are obtained by querying the <code>MessageManager</code>
 * for MessageLists which contain messages which were not downloaded yet.
 * 
 * @author xor
 */
public final class WoTMessageFetcher extends MessageFetcher {
	
	private static final int STARTUP_DELAY = 1 * 60 * 1000;
	private static final int THREAD_PERIOD = 5 * 60 * 1000; /* FIXME: tweak before release */
	private static final int MAX_PARALLEL_MESSAGE_FETCH_COUNT = 64;
	
	/**
	 * If a message download succeeds/fails and there are less parallel downloads than this constant, the sleeping loop of the fetch thread
	 * is interrupted by onSuccess/onFailure so that it starts new downloads. Do not set this too high, otherwise the thread will be interrupted
	 * too often if there are no more message links in the database.
	 */
	private static final int MIN_PARALLEL_MESSAGE_FETCH_COUNT = 4; 
	
	private final Random mRandom;
	
	/**
	 * For each <code>ClientGetter</code> (= an object associated with a fetch) this hashtable stores the ID of the MessageList to which the
	 * message which is being fetched belongs.
	 */
	private final Hashtable<ClientGetter, String> mMessageLists = new Hashtable<ClientGetter, String>(MAX_PARALLEL_MESSAGE_FETCH_COUNT * 2);

	public WoTMessageFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, WoTIdentityManager myIdentityManager, WoTMessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		mRandom = mNode.fastWeakRandom;
		start();
		Logger.debug(this, "Message fetcher started.");
	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		return new HashSet<ClientGetter>(MAX_PARALLEL_MESSAGE_FETCH_COUNT * 2);
	}

	@Override
	protected Collection<BaseClientPutter> createInsertStorage() {
		return null;
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
	protected synchronized void iterate() {
		Logger.debug(this, "Message fetcher loop running ...");
		abortAllTransfers();
		
		synchronized(MessageList.class) {
			Iterator<MessageList> iter = mMessageManager.notDownloadedMessageListIterator();
			while(iter.hasNext()) {
				fetchMessages(iter.next());
				
				/* FIXME: Investigate whether this is fair, i.e. whether it will not always try to download the same messages if downloading
				 * of some of them stalls for so long that the thread re-iterates before onFailure is called which results in the messages
				 * not being marked as undownloadable. (Currently, we do not mark messages as undownloadable anyway! However, onFailure
				 * should do that in the future) */
				if(mMessageLists.size() > MAX_PARALLEL_MESSAGE_FETCH_COUNT)
					break;
			}
		}

		Logger.debug(this, "Message fetcher loop finished.");
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	private void fetchMessages(MessageList list) {
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

	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	private void fetchMessage(MessageList list, FreenetURI uri) throws FetchException {
		FetchContext fetchContext = mClient.getFetchContext();
		fetchContext.maxSplitfileBlockRetries = 2;
		fetchContext.maxNonSplitfileRetries = 2;
		ClientGetter g = mClient.fetch(uri, -1, this, this, fetchContext);
		//g.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS); /* pluginmanager defaults to interactive priority */
		addFetch(g);
		mMessageLists.put(g, list.getID());
		Logger.debug(this, "Trying to fetch message from " + uri);
	}

	@Override
	public synchronized void onSuccess(FetchResult result, ClientGetter state) {
		Logger.debug(this, "Fetched message: " + state.getURI());
		
		try {
			WoTMessageList list = (WoTMessageList)mMessageManager.getMessageList(mMessageLists.get(state));
			Message message = MessageXML.decode(mMessageManager, result.asBucket().getInputStream(), list, state.getURI());
			mMessageManager.onMessageReceived(message);
		}
		catch (Exception e) {
			Logger.error(this, "Parsing failed for message " + state.getURI(), e);
			/* FIXME: Mark non-parseable messages (in the MessageList) so that they do not block the download queue */ 
		}
		finally {
			removeFetch(state); /* FIXME: This was in the try{} block somewhere else in the FT/WoT code. Move it to finally{} there, too */
			
			if(mMessageLists.size() < MIN_PARALLEL_MESSAGE_FETCH_COUNT)
				nextIteration();
		}
	}
	
	@Override
	public synchronized void onFailure(FetchException e, ClientGetter state) {
		try {
			/* TODO: Handle DNF in some reasonable way. Mark the messages in the MessageList maybe.
			if(e.getMode() == FetchException.DATA_NOT_FOUND) {
			
			}
			*/
			
			Logger.error(this, "Downloading message " + state.getURI() + " failed.", e);
		}
		
		finally {
			removeFetch(state);
		
			if(mMessageLists.size() < MIN_PARALLEL_MESSAGE_FETCH_COUNT)
				nextIteration();
		}
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	@Override
	protected void abortAllTransfers() {
		mMessageLists.clear();
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	@Override
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
