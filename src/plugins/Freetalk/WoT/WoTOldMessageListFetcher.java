/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;

import plugins.Freetalk.FetchFailedMarker;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageListFetcher;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.LRUQueue;
import freenet.support.Logger;
import freenet.support.TransferThread;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;

/**
 * Periodically wakes up and fetches "old" {@link MessageList}s from identities.
 * 
 * "Old" means that the message list is known to exist because we have already fetched a message list with a higher index. 
 * 
 * The policy currently is the following:
 * - We periodically wake up and check whether there are any old message lists to be fetched.
 * 		If there are, we start up to MAX_PARALLEL_MESSAGELIST_FETCH_COUNT fetches
 * - In the onSuccess() method, for each fetched <code>MessageList</code>, a fetch is started for another old message list It tries to fetch the
 * 		most recent older ones first.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class WoTOldMessageListFetcher extends TransferThread implements MessageListFetcher {

	private static final int STARTUP_DELAY = Freetalk.FAST_DEBUG_MODE ? (10 * 1000) : (5 * 60 * 1000);
	
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (5 * 60 * 1000) : (15 * 60 * 1000);	// TODO: Make configurable
	
	/**
	 * How many message lists do we attempt to fetch in parallel? TODO: This should be configurable.
	 */
	private static final int MAX_PARALLEL_MESSAGELIST_FETCH_COUNT = Freetalk.FAST_DEBUG_MODE ? 64 : 32;
	
	/**
	 * How many identities do we keep in the LRU Hashtable? The hashtable is a queue which is used to ensure that we fetch message lists from different identities
	 * and not always from the same ones.
	 */
	private static final int IDENTITIES_LRU_QUEUE_SIZE_LIMIT = 1024;

	private final Freetalk mFreetalk;
	private final WoTIdentityManager mIdentityManager;
	private final WoTMessageManager mMessageManager;
	private final ClientContext clientContext;
	private final RequestClient mRequestClient;
	
	private final LRUQueue<String> mIdentities = new LRUQueue<String>();
	
	private final Random mRandom;
	
	private final WoTMessageListXML mXML;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(WoTOldMessageListFetcher.class);
	}
	
	
	public WoTOldMessageListFetcher(Freetalk myFreetalk, String myName, WoTMessageListXML myMessageListXML) {
		super(myFreetalk.getPluginRespirator().getNode(), myFreetalk.getPluginRespirator().getHLSimpleClient(), myName);
		
		mFreetalk = myFreetalk;
		mIdentityManager = myFreetalk.getIdentityManager();
		mMessageManager = myFreetalk.getMessageManager();
		clientContext = mNode.clientCore.clientContext;
		mRequestClient = mMessageManager.mRequestClient;
		mRandom = mNode.fastWeakRandom;
		mXML = myMessageListXML;
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

	@Override protected void iterate() {
		fetchMessageLists();
	}
	
	@Override public int getRunningFetchCount() {
		return fetchCount();
	}

	/**
	 * Starts fetches of MessageLists from MAX_PARALLEL_MESSAGELIST_FETCH_COUNT different identities. For each identity, it is attempted to start a fetch
	 * of the latest old message list.
	 * 
	 * The identities are put in a LRU queue, so in the next iteration, fetches will not be allowed from identities of the previous iteration.
	 */
	private synchronized void fetchMessageLists() {
		final int fetchCount = fetchCount();
		
		if(fetchCount >= MAX_PARALLEL_MESSAGELIST_FETCH_COUNT) { // Check before we do the expensive database query.
			if(logDEBUG) Logger.debug(this, "Got " + fetchCount + " fetches, not fetching any more.");
			return;
		}
		
		if(logDEBUG) Logger.debug(this, "Trying to start more message list fetches, amount of fetches now: " + fetchCount);
		
		fetchMessageListsCore();
		
		if(fetchCount() == 0 && !mIdentities.isEmpty()) {
			synchronized(mIdentities) {
				mIdentities.clear();
				fetchMessageListsCore();
			}
		}
	}
	
	private void fetchMessageListsCore() {
		// TODO: Order the identities by date of modification
		
		synchronized(mIdentities) {
			for(WoTIdentity identity : mIdentityManager.getAllIdentities()) {
				if(!mIdentities.contains(identity.getID()) && mIdentityManager.anyOwnIdentityWantsMessagesFrom(identity)) {
					try {
						long unavailableIndex = mMessageManager.getUnavailableOldMessageListIndex(identity);
						
						fetchMessageList((WoTIdentity)identity, unavailableIndex);
						
						if(fetchCount() >= MAX_PARALLEL_MESSAGELIST_FETCH_COUNT)
							break;
					}
					catch(NoSuchMessageListException e) {
						// This identity has no unavailable old list, we skip it
					}
					catch(Exception e) {
						Logger.error(this, "Fetching of MessageList failed for " + identity, e);
					}
				}
			}
		}
	}

	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	private void fetchMessageList(WoTIdentity identity, long index) throws FetchException {
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
		
		FreenetURI uri = WoTMessageList.generateURI(identity, index).sskForUSK(); // We must use a SSK to disallow redirects.
		FetchContext fetchContext = mClient.getFetchContext();
		fetchContext.maxSplitfileBlockRetries = 2; /* 3 and above or -1 = cooldown queue. -1 is infinite */
		fetchContext.maxNonSplitfileRetries = 2;
		fetchContext.maxOutputLength = WoTMessageListXML.MAX_XML_SIZE; // TODO: fetch() also takes a maxSize parameter, why?
		ClientGetter g = mClient.fetch(uri, WoTMessageListXML.MAX_XML_SIZE, mRequestClient, this, fetchContext, RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS);
		addFetch(g);
		Logger.normal(this, "Trying to fetch MessageList from " + uri);
		
		// Not necessary because it's not a HashSet but a fixed-length queue so the identity will get removed sometime anyway.
		//catch(RuntimeException e) {
		//	mIdentities.removeKey(identity);
		//}
	}

	@Override
	public synchronized void onSuccess(FetchResult result, ClientGetter state, ObjectContainer container) {
		Logger.normal(this, "Fetched MessageList: " + state.getURI());

		Bucket bucket = null;
		InputStream inputStream = null;
		WoTIdentity identity = null;
		
		boolean fetchMoreLists = false;
		
		try {
			bucket = result.asBucket();			
			inputStream = bucket.getInputStream();
			
			synchronized(mIdentityManager) {
				identity = (WoTIdentity)mIdentityManager.getIdentityByURI(state.getURI());
				
				synchronized(mMessageManager) {
					try {
						WoTMessageList list = mXML.decode(mFreetalk, identity, state.getURI(), inputStream);
						mMessageManager.onMessageListReceived(list);
						fetchMoreLists = true;
					}
					catch (Exception e) {
						Logger.error(this, "Parsing failed for MessageList " + state.getURI(), e);
						mMessageManager.onMessageListFetchFailed(identity, state.getURI(), FetchFailedMarker.Reason.ParsingFailed);
						fetchMoreLists = true;
					}
				}
			}
		}
		catch (NoSuchIdentityException e) {
			Logger.normal(this, "Identity was deleted already, ignoring MessageList " + state.getURI());
		}
		catch (IOException e) {
			Logger.error(this, "getInputStream() failed.", e);
		}
		finally {
			Closer.close(inputStream);
			Closer.close(bucket);
			removeFetch(state);
		}

		// We only call fetchMessageLists() if we know that the current list was marked as fetched in the database,
		// otherwise the fetch thread could get stuck in a busy loop: "fetch(), onSuccess(), fetch(), onSuccess(), ..."
		if(fetchMoreLists)
			fetchMessageLists();
	}

	@Override
	public synchronized void onFailure(FetchException e, ClientGetter state, ObjectContainer container) {
		try {
			switch(e.getMode()) {
				case FetchException.DATA_NOT_FOUND:
				case FetchException.ALL_DATA_NOT_FOUND:
				case FetchException.RECENTLY_FAILED:
					assert(state.getURI().isSSK());
					
					// We requested an old MessageList, i.e. it's index is lower than the index of the latest known MessageList, so the requested MessageList
					// must have existed but has fallen out of Freenet, we mark it as DNF so it does not spam the request queue.
					
					Logger.normal(this, "Data not found for old MessageList " + state.getURI());
						
					try {
						synchronized(mIdentityManager) {
						final WoTIdentity identity = (WoTIdentity)mIdentityManager.getIdentityByURI(state.getURI());
						//synchronized(mMessageManager) {	// Would only be needed if we call more functions..
						mMessageManager.onMessageListFetchFailed(identity, state.getURI(), FetchFailedMarker.Reason.DataNotFound);
						//}
						}
							
						// We only call fetchMessageLists() if we know that the current list was marked as fetched in the database,
						// otherwise the fetch thread could get stuck in a busy loop: "fetch(), onSuccess(), fetch(), onSuccess(), ..."
						fetchMessageLists();
					} catch (NoSuchIdentityException ex) {
						Logger.normal(this, "Identity was deleted already, not marking MessageList as DNF: " + state.getURI());
					}

					break;
				
				case FetchException.CANCELLED:
					if(logDEBUG) Logger.debug(this, "Cancelled downloading MessageList " + state.getURI());
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
	
	/**
	 * This method must be synchronized because onFailure is synchronized and TransferThread calls abortAllTransfers() during shutdown without
	 * synchronizing on this object.
	 */
	@Override protected synchronized void abortAllTransfers() {
		super.abortAllTransfers();
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

	@Override
	public void onGeneratedMetadata(Bucket metadata, BaseClientPutter state,
			ObjectContainer container) {
		metadata.free();
		throw new UnsupportedOperationException();
	}

}
