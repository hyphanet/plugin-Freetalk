package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageListFetcher;
import plugins.Freetalk.exceptions.NoSuchIdentityException;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.LRUQueue;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;

/**
 * Periodically wakes up and fetches <code>MessageList</code>s from identities.
 * 
 * The policy currently is the following:
 * - When waking up, for each selected identity we try to fetch a <code>MessageList</code> with index =
 * (latest known <code>MessageList</code> index + 1) with USK redirects enabled. This means that the node will fetch a hopefully recent and new
 * <code>MessageList</code>. Further, we also try to fetch an older MessageList than the latest available one.
 * - In the onSuccess() method, for each fetched <code>MessageList</code>, a fetch is started for an unavailable <code>MessageList</code> which is
 * *older* than any available MessageList (unless all older MessageLists are available, then it tries to fetch a newer one). It tries to fetch the
 * most recent older ones first.
 * 
 * So the fetching of *new* MessageLists is done by the USK redirects, the fetching of old ones is done from newer to older.
 * 
 * @author xor
 */
public final class WoTMessageListFetcher extends MessageListFetcher {

	private static final int STARTUP_DELAY = Freetalk.FAST_DEBUG_MODE ? (10 * 1000) : (1 * 60 * 1000);	// FIXME: tweak before release
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (3 * 60 * 1000) : (5 * 60 * 1000);	// FIXME: tweak before release
	
	/**
	 * How many message lists do we attempt to fetch in parallel? FIXME: This should be configurable.
	 */
	private static final int MAX_PARALLEL_MESSAGELIST_FETCH_COUNT = 16;
	
	/**
	 * How many identities do we keep in the LRU Hashtable? The hashtable is a queue which is used to ensure that we fetch message lists from different identities
	 * and not always from the same ones.
	 */
	private static final int IDENTITIES_LRU_QUEUE_SIZE_LIMIT = 1024;

	
	private final WoTIdentityManager mIdentityManager;
	private final WoTMessageManager mMessageManager;
	private final ClientContext clientContext;
	private final RequestClient mRequestClient;
	
	private final LRUQueue<String> mIdentities = new LRUQueue<String>();
	
	private final Random mRandom;
	
	public WoTMessageListFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, WoTIdentityManager myIdentityManager, WoTMessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
		mRandom = mNode.fastWeakRandom;
		clientContext = mNode.clientCore.clientContext;
		mRequestClient = mMessageManager.mRequestClient;
	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		return new HashSet<ClientGetter>(MAX_PARALLEL_MESSAGELIST_FETCH_COUNT * 2);
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

	/**
	 * Starts fetches of MessageLists from MAX_PARALLEL_MESSAGELIST_FETCH_COUNT different identities. For each identity, it is attempted to start a fetch
	 * of the latest news message list (by allowing USK redirects) and a fetch of an old message list.
	 * 
	 * The identities are put in a LRU queue, so in the next iteration, fetches will not be allowed from identities of the previous iteration.
	 *  
	 * Further, for each succeeded/failed MessageList fetch, the onSuccess() / onFailure() method starts a new fetch from the same identity.
	 * 
	 * Therefore, each identity for which fetches are started in iterate() gets the chance to get as many message lists downloaded as the node can fetch within
	 * the THREAD_PERIOD timespan.
	 */
	protected synchronized void iterate() {
		abortAllTransfers();
		
		ArrayList<WoTIdentity> identitiesToFetchFrom = new ArrayList<WoTIdentity>(MAX_PARALLEL_MESSAGELIST_FETCH_COUNT + 1);
		
		// FIXME: Order the identities by date of modification
		
		synchronized(mIdentities) {
			for(WoTIdentity identity : mIdentityManager.getAllIdentities()) {
				if(!mIdentities.contains(identity.getID()) && mIdentityManager.anyOwnIdentityWantsMessagesFrom(identity)) {
					identitiesToFetchFrom.add(identity);

					if(identitiesToFetchFrom.size() >= MAX_PARALLEL_MESSAGELIST_FETCH_COUNT)
						break;
				}
			}
		}
		
		/* mIdentities contains all identities which are available, so we have to flush it. */
		if(identitiesToFetchFrom.size() == 0) {
			Logger.debug(this, "Ran out of identities to fetch from, clearing the LRUQueue...");
			mIdentities.clear();

			for(WoTIdentity identity : mIdentityManager.getAllIdentities()) {
				if(mIdentityManager.anyOwnIdentityWantsMessagesFrom(identity)) {
					identitiesToFetchFrom.add(identity);

					if(identitiesToFetchFrom.size() >= MAX_PARALLEL_MESSAGELIST_FETCH_COUNT)
						break;
				}
			}
		}
		
		for(WoTIdentity identity : identitiesToFetchFrom) {
			try {
				fetchMessageLists((WoTIdentity)identity);
			}
			catch(Exception e) {
				Logger.error(this, "Fetching of messages from " + identity.getNickname() + " failed.", e);
			}
		}
	}

	/**
	 * You have to synchronize on this <code>WoTMessageListFetcher</code> when using this function.
	 * @throws FetchException 
	 */
	private void fetchMessageLists(WoTIdentity identity) throws FetchException {
		int newIndex = mMessageManager.getUnavailableNewMessageListIndex(identity);
		fetchMessageList(identity, newIndex, true);
		
		if(newIndex > 0) {
			int oldIndex = mMessageManager.getUnavailableOldMessageListIndex(identity);
			if(oldIndex != newIndex)
				fetchMessageList(identity, mMessageManager.getUnavailableOldMessageListIndex(identity), false);
		}
	}

	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 * @param followRedirectsToHigherIndex If true, the USK redirects will be used to download the latest instead of the specified index.
	 */
	private void fetchMessageList(WoTIdentity identity, int index, boolean followRedirectsToHigherIndex) throws FetchException {
		synchronized(mIdentities) {
			// mIdentities contains up to IDENTITIES_LRU_QUEUE_SIZE_LIMIT identities of which we have recently downloaded MessageLists. This queue is used to ensure
			// that we download MessageLists from different identities and not always from the same ones. 
			// The oldest identity falls out of the LRUQueue if it has reached it size limit and therefore MessageList downloads from that one are allowed again.
			
			// We do not disallow fetches from the given identity if it is in the LRUQueue: This is done by iterate() which iterates with a delay of THREAD_PERIOD.
			// - By not disallowing the fetches from the given identity here, we can make onSuccess()/onFailure() download the next message list of the identity. 
		 
			if(mIdentities.size() >= IDENTITIES_LRU_QUEUE_SIZE_LIMIT) {
				// We first checked whether the identity was in the queue because if the given identity is already in the pipeline then downloading a list from it
				// should NOT cause a different identity to fall out - the given identity should be moved to the top and the others should stay in the pipeline.
				if(!mIdentities.contains(identity.getID()))
					mIdentities.pop();
			}
			
			mIdentities.push(identity.getID()); // put this identity at the beginning of the LRUQueue
		}
		
		FreenetURI uri = WoTMessageList.generateURI(identity, index);
		if(!followRedirectsToHigherIndex)
			uri = uri.sskForUSK();
		FetchContext fetchContext = mClient.getFetchContext();
		fetchContext.maxSplitfileBlockRetries = 2; /* 3 and above or -1 = cooldown queue. -1 is infinite */
		fetchContext.maxNonSplitfileRetries = 2;
		ClientGetter g = mClient.fetch(uri, -1, mRequestClient, this, fetchContext);
		g.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS, mClientContext, null);
		addFetch(g);
		Logger.debug(this, "Trying to fetch MessageList from " + uri);
		
		// Not necessary because it's not a HashSet but a fixed-length queue so the identity will get removed sometime anyway.
		//catch(RuntimeException e) {
		//	mIdentities.removeKey(identity);
		//}
	}

	@Override
	public synchronized void onSuccess(FetchResult result, ClientGetter state, ObjectContainer container) {
		Logger.debug(this, "Fetched MessageList: " + state.getURI());

		Bucket bucket = null;
		InputStream inputStream = null;
		WoTIdentity identity = null;
		
		try {
			identity = (WoTIdentity)mIdentityManager.getIdentityByURI(state.getURI());
			bucket = result.asBucket();			
			inputStream = bucket.getInputStream();
			WoTMessageList list = WoTMessageListXML.decode(mMessageManager, identity, state.getURI(), inputStream);
			mMessageManager.onMessageListReceived(list);
		}
		catch (Exception e) {
			Logger.error(this, "Parsing failed for MessageList " + state.getURI(), e);

			if(identity != null) {
				mMessageManager.onMessageListFetchFailed(identity, state.getURI(), MessageList.MessageListFetchFailedReference.Reason.ParsingFailed);
			}
		}
		finally {
			Closer.close(inputStream);
			Closer.close(bucket);
			removeFetch(state);
		}
		
		try {
			int unavailableIndex = mMessageManager.getUnavailableOldMessageListIndex(identity);
			boolean unavailableIsNewer = unavailableIndex > state.getURI().getEdition(); /* Follow redirects then! */
			fetchMessageList(identity, unavailableIndex , unavailableIsNewer);
		} catch(Exception e) {
			Logger.error(this, "Fetching of next MessageList failed.", e);
		}
	}

	@Override
	public synchronized void onFailure(FetchException e, ClientGetter state, ObjectContainer container) {
		try {
			switch(e.getMode()) {
				case FetchException.DATA_NOT_FOUND:
					WoTIdentity identity;
					try {
						identity = (WoTIdentity)mIdentityManager.getIdentityByURI(state.getURI());
						mMessageManager.onMessageListFetchFailed(identity, state.getURI(), MessageList.MessageListFetchFailedReference.Reason.DataNotFound);
					} catch (NoSuchIdentityException ex) {
						Logger.error(this, "SHOULD NOT HAPPEN", ex);
					}
					
					Logger.debug(this, "DNF for MessageList " + state.getURI());
					break;
				
				case FetchException.PERMANENT_REDIRECT:
					try {
						state.restart(e.newURI, null, clientContext);
					} catch (FetchException e1) {
						Logger.error(this, "Request restart failed.", e1);
					}
					break;
					
				case FetchException.CANCELLED:
					Logger.debug(this, "Cancelled downloading MessageList " + state.getURI());
					break;
					
				default:
					Logger.error(this, "Downloading MessageList " + state.getURI() + " failed.", e);
					break;
			}
		}
		
		finally {
			removeFetch(state);
		}
	}
	
	/* Not needed functions, called for inserts */

	@Override
	public void onGeneratedURI(FreenetURI uri, BaseClientPutter state, ObjectContainer container) { }
	
	@Override
	public void onSuccess(BaseClientPutter state, ObjectContainer container) { }
	
	@Override
	public void onFailure(InsertException e, BaseClientPutter state, ObjectContainer container) { }
	
	@Override
	public void onFetchable(BaseClientPutter state, ObjectContainer container) { }

	@Override
	public void onMajorProgress(ObjectContainer container) { }

}
