package plugins.Freetalk.WoT;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

import plugins.Freetalk.MessageListFetcher;
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

	private static final int STARTUP_DELAY = 1 * 60 * 1000;
	private static final int THREAD_PERIOD = 5 * 60 * 1000; /* FIXME: tweak before release */
	private static final int MAX_PARALLEL_MESSAGELIST_FETCH_COUNT = 64;
	
	private final WoTIdentityManager mIdentityManager;
	private final WoTMessageManager mMessageManager;
	
	/* FIXME FIXME FIXME: Use LRUQueue instead. ArrayBlockingQueue does not use a Hashset for contains()! */
	private final ArrayBlockingQueue<WoTIdentity> mIdentities = new ArrayBlockingQueue<WoTIdentity>(MAX_PARALLEL_MESSAGELIST_FETCH_COUNT * 10); /* FIXME: figure out a decent size */
	
	// private static final Calendar mCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	
	private final Random mRandom;
	
	public WoTMessageListFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, WoTIdentityManager myIdentityManager, WoTMessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
		mRandom = mNode.fastWeakRandom;
		start();
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

	@Override
	protected synchronized void iterate() {
		abortAllTransfers();
		
		ArrayList<WoTIdentity> identitiesToFetchFrom = new ArrayList<WoTIdentity>(MAX_PARALLEL_MESSAGELIST_FETCH_COUNT + 1);
		
		for(WoTIdentity identity : mIdentityManager.getAllIdentities()) {
			synchronized(mIdentities) {
				if(!mIdentities.contains(identity) && mIdentityManager.anyOwnIdentityWantsMessagesFrom(identity)) {
					identitiesToFetchFrom.add(identity);
					mIdentities.add(identity);
					
					if(identitiesToFetchFrom.size() >= MAX_PARALLEL_MESSAGELIST_FETCH_COUNT)
						break;
				}
			}
		}
		
		/* mIdentities contains all identities which are available, so we have to flush it. */
		if(identitiesToFetchFrom.size() == 0) {
			 mIdentities.clear();
			
			 for(WoTIdentity identity : mIdentityManager.getAllIdentities()) {
				 synchronized(mIdentities) {
					 if(mIdentityManager.anyOwnIdentityWantsMessagesFrom(identity)) {
						 identitiesToFetchFrom.add(identity);
						 mIdentities.add(identity);
						 
						if(identitiesToFetchFrom.size() >= MAX_PARALLEL_MESSAGELIST_FETCH_COUNT)
							break;
					 }
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
		FreenetURI uri = WoTMessageList.generateURI(identity, index);
		if(!followRedirectsToHigherIndex)
			uri.setKeyType("SSK");
		FetchContext fetchContext = mClient.getFetchContext();
		fetchContext.maxSplitfileBlockRetries = 2; /* 3 and above or -1 = cooldown queue. -1 is infinite */
		fetchContext.maxNonSplitfileRetries = 2;
		ClientGetter g = mClient.fetch(uri, -1, this, this, fetchContext);
		//g.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS); /* pluginmanager defaults to interactive priority */
		addFetch(g);
		Logger.debug(this, "Trying to fetch MessageList from " + uri);
	}

	@Override
	public synchronized void onSuccess(FetchResult result, ClientGetter state) {
		Logger.debug(this, "Fetched MessageList: " + state.getURI());
		
		InputStream input = null;
		WoTIdentity identity = null;
		
		try {
			identity = (WoTIdentity)mIdentityManager.getIdentityByURI(state.getURI());
			input = result.asBucket().getInputStream();
			WoTMessageList list = WoTMessageListXML.decode(mMessageManager, identity, state.getURI(), input);
			input.close(); input = null;
			mMessageManager.onMessageListReceived(list);
		}
		catch (Exception e) {
			Logger.error(this, "Parsing failed for MessageList " + state.getURI(), e);
			/* FIXME: Mark non-parseable MessageLists so that they do not block the download queue */ 
		}
		finally {
			if(input != null) {
				try {
					input.close();
				} catch (Exception e) {
					Logger.error(this, "Error while closing Bucket InputStream", e);
				}
			}
				
			removeFetch(state);
		}
		
		try {
			int unavailableIndex = mMessageManager.getUnavailableOldMessageListIndex(identity);
			boolean unavailableIsNewer = unavailableIndex > state.getURI().getSuggestedEdition(); /* Follow redirects then! */
			fetchMessageList(identity, unavailableIndex , unavailableIsNewer);
		} catch(Exception e) {
			Logger.error(this, "Fetching of next MessageList failed.", e);
		}
	}

	@Override
	public synchronized void onFailure(FetchException e, ClientGetter state) {
		try {
			/* TODO: Handle DNF in some reasonable way. Mark the MessageLists as unavailable after a certain amount of retries maybe */
			if(e.getMode() == FetchException.DATA_NOT_FOUND) {
			
			}
			else if (e.mode == FetchException.PERMANENT_REDIRECT) {
				try {
					state.restart(e.newURI);
				} catch (FetchException e1) {
					Logger.error(this, "Request restart failed.", e1);
				}
			}
			else 
				Logger.error(this, "Downloading MessageList " + state.getURI() + " failed.", e);
		}
		
		finally {
			removeFetch(state);
		}
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
