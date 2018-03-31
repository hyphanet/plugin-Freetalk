/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import plugins.Freetalk.FetchFailedMarker;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageFetcher;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;

import com.db4o.ObjectSet;

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
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;
import freenet.support.io.ResumeFailedException;

/**
 * Periodically wakes up and fetches messages by their CHK URI. The CHK URIs of messages are obtained by querying the <code>MessageManager</code>
 * for MessageLists which contain messages which were not downloaded yet.
 * 
 * TODO: Change this to event-driven code: The MessageManager should raise an event if new messages are to be fetched, instead of having the MessageFetcher
 * wake up periodically.
 * 
 * Runs up to {@link MAX_PARALLEL_MESSAGE_FETCH_COUNT} fetches in parallel. As soon as a fetch succeeds/fails more fetches are started if unfetched message
 * URI are available in the database.  
 * 
 * @author xor (xor@freenetproject.org}
 */
public final class WoTMessageFetcher extends MessageFetcher {
	
	private static final int STARTUP_DELAY = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) : (3 * 60 * 1000);
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (3 * 60 * 1000) : (5 * 60 * 1000);
	
	/**
	 * How many fetches are run in parallel? 
	 * TODO: This should be a function of node speed. It's not severe right now that we have a static limit because if a fetch succeeds/fails
	 * we immediately start a new one.
	 */
	private static final int MAX_PARALLEL_MESSAGE_FETCH_COUNT = 32;
	
	private final Random mRandom;
	
	private final RequestClient requestClient;
	
	/**
	 * For each <code>ClientGetter</code> (= an object associated with a fetch) this HashMap stores the ID of the MessageList to which the
	 * message which is being fetched belongs.
	 */
	private final HashMap<ClientGetter, String> mMessageLists = new HashMap<ClientGetter, String>(MAX_PARALLEL_MESSAGE_FETCH_COUNT * 2);
	
	
	/**
	 * Contains a list of messages we are currently trying to fetch. Used for preventing parallel fetch attempts of the same message.
	 */
	private final HashSet<FreenetURI> mMessages = new HashSet<FreenetURI>(MAX_PARALLEL_MESSAGE_FETCH_COUNT * 2);
	
	private final WoTMessageXML mXML;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(WoTMessageFetcher.class);
	}
	

	public WoTMessageFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, Freetalk myFreetalk, WoTIdentityManager myIdentityManager, WoTMessageManager myMessageManager,
			WoTMessageXML myMessageXML) {
		super(myNode, myClient, myName, myFreetalk, myIdentityManager, myMessageManager);
		mRandom = mNode.fastWeakRandom;
		requestClient = myMessageManager.mRequestClient;
		mXML = myMessageXML;
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

	@Override public RequestClient getRequestClient() {
		return requestClient;
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
		fetchMessages();
	}

	@Override public int getRunningFetchCount() {
		return fetchCount();
	}
	
	/**
	 * Checks the database for unfetched messages and starts message fetches until this fetches is running the maximum of MAX_PARALLEL_MESSAGE_FETCH_COUNT fetches.
	 * Does not abort fetches which were started by previous calls to this function: We use a finite retry count for each started fetch so the node should
	 * call onFailure / onSuccess after a finite amount of time which causes not working message fetches to be aborted.
	 * 
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	private void fetchMessages() {
		final int fetchCount = fetchCount();
		
		if(fetchCount >= MAX_PARALLEL_MESSAGE_FETCH_COUNT) { // Check before we do the expensive database query.
			if(logMINOR) Logger.minor(this, "Got " + fetchCount + "fetches, not fetching any more.");
			return;
		}
		
		if(logMINOR) Logger.minor(this, "Trying to start more message fetches, amount of fetches now: " + fetchCount);
		
		synchronized(mIdentityManager) { // TODO: Get rid of this lock by making anyOwnIdentityWantsMessagesFrom use a cache
		synchronized(mMessageManager) { 
			/* TODO: Obtain WoTMessageLists only, not all. */
			final ObjectSet<MessageList.MessageReference> notDownloadedMessages = mMessageManager.notDownloadedMessageIterator();
			
			for(MessageList.MessageReference ref : notDownloadedMessages) {
				try {
					// TODO: This should maybe be done inside the database query
					if(mIdentityManager.anyOwnIdentityWantsMessagesFrom(ref.getMessageList().getAuthor()))
						fetchMessage(ref);
				}
				catch(Exception e) {
					Logger.error(this, "Error while trying to fetch message " + ref.getURI(), e);
				}

				if(fetchCount() >= MAX_PARALLEL_MESSAGE_FETCH_COUNT)
					break;
			}
		}
		}
	}
	

	/**
	 * Starts a fetch for the given message.
	 * Uses a finite amount of retries, so the fetch will definitely finish in a finite amount of time.
	 * 
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	private void fetchMessage(MessageList.MessageReference ref) throws FetchException {
		final FreenetURI uri = ref.getURI(); 
		
		if(mMessages.add(uri) == false)// The message is already being fetched.
			return;

		try {
			FetchContext fetchContext = mClient.getFetchContext();
			// We MUST use a finite amount of retries because this function is specified to do so and the callers rely on that.
			fetchContext.maxSplitfileBlockRetries = 2;
			fetchContext.maxNonSplitfileRetries = 2;
			fetchContext.maxOutputLength = WoTMessageXML.MAX_XML_SIZE; // TODO: fetch() also takes a maxSize parameter, why?
			ClientGetter g = mClient.fetch(uri, WoTMessageXML.MAX_XML_SIZE, this, fetchContext,
				RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS);
			addFetch(g);
			mMessageLists.put(g, ref.getMessageList().getID());
			Logger.normal(this, "Trying to fetch message from " + uri);
		}
		catch(RuntimeException e) {
			mMessages.remove(uri);
			throw e;
		}
	}

	@Override
	public synchronized void onSuccess(FetchResult result, ClientGetter state) {
		Logger.normal(this, "Fetched message: " + state.getURI());
		final String messageListID = mMessageLists.get(state);
		removeFetch(state); // This must be called before we call fetchMessages() because fetchMessages has a parallel fetch count limit.
		
		Bucket bucket = null;
		InputStream inputStream = null;
		WoTMessageList list = null;
		
		boolean fetchMoreMessages = false;
		
		synchronized(mMessageManager) {
		try {
			list = (WoTMessageList)mMessageManager.getMessageList(messageListID);
			bucket = result.asBucket();
			inputStream = bucket.getInputStream();
			Message message = mXML.decode(mFreetalk, inputStream, list, state.getURI());
			mMessageManager.onMessageReceived(message);
			
			fetchMoreMessages = true;
		}
		catch (NoSuchMessageListException e) {
			Logger.normal(this, "MessageList was deleted already, not importing message: " + state.getURI());
		}
		catch (Exception e) {
			Logger.error(this, "Parsing failed for message " + state.getURI(), e);
		
			try {
				mMessageManager.onMessageFetchFailed(list.getReference(state.getURI()), FetchFailedMarker.Reason.ParsingFailed);
					
				fetchMoreMessages = true;
			}
			catch(NoSuchMessageException ex) {
				Logger.error(this, "SHOULD NOT HAPPEN", ex);
				assert(false);
			}
		}
		finally {
			Closer.close(inputStream);
			Closer.close(bucket);
		}
		}
		
		// We only call fetchMessages() if we know that the current message was marked as fetched in the database, otherwise the fetch thread could get stuck
		// in a busy loop: "fetch(), onSuccess(), fetch(), onSuccess(), ..."
		if(fetchMoreMessages)
			fetchMessages();
	}
	
	@Override
	public synchronized void onFailure(FetchException e, ClientGetter state) {
		final String messageListID = mMessageLists.get(state);
		removeFetch(state); // This must be called before we call fetchMessages() because fetchMessages has a parallel fetch count limit.
		
			switch(e.getMode()) {
				case DATA_NOT_FOUND:
				case ALL_DATA_NOT_FOUND:
				case RECENTLY_FAILED:
					Logger.normal(this, "Data not found for message: " + state.getURI());
					
					try {
						synchronized(mMessageManager) {
						WoTMessageList list = (WoTMessageList)mMessageManager.getMessageList(messageListID);
						mMessageManager.onMessageFetchFailed(list.getReference(state.getURI()), FetchFailedMarker.Reason.DataNotFound);
						}
						
						// We only call fetchMessages() if we know that the message for which the fetch failed was marked as failed, otherwise the fetch
						// thread could get stuck in a busy loop: "fetch(), onFailure(), fetch(), onFailure() ..."
						fetchMessages();
					} catch(NoSuchMessageListException ex) {
						Logger.normal(this, "MessageList was deleted already, not marking message as fetch failed: " + state.getURI());
					} catch (Exception ex) {
						Logger.error(this, "SHOULD NOT HAPPEN", ex);
						assert(false);
					}
					break;
					
				case CANCELLED:
					if(logDEBUG) Logger.debug(this, "Cancelled downloading Message " + state.getURI());
					break;
					
				default:
					Logger.error(this, "Downloading message " + state.getURI() + " failed.", e);
					break;
			}
	}
	
	/**
	 * This method must be synchronized because onFailure is synchronized and TransferThread calls abortAllTransfers() during shutdown without
	 * synchronizing on this object.
	 */
	@Override
	protected synchronized void abortAllTransfers() {
		super.abortAllTransfers();
		mMessageLists.clear();
		mMessages.clear();
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	@Override
	protected void removeFetch(ClientGetter g) {
		super.removeFetch(g);
		mMessageLists.remove(g);
		mMessages.remove(g.getURI());
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

	@Override public void onGeneratedMetadata(Bucket metadata, BaseClientPutter state) {
		metadata.free();
		throw new UnsupportedOperationException();
	}

	@Override public void onResume(ClientContext context) throws ResumeFailedException {
		assert(false);
		throw new ResumeFailedException("This class doesn't create persistent requests!");
	}
}
