/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;

import plugins.Freetalk.FetchFailedMarker;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageFetcher;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;

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
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;

/**
 * Periodically wakes up and fetches messages by their CHK URI. The CHK URIs of messages are obtained by querying the <code>MessageManager</code>
 * for MessageLists which contain messages which were not downloaded yet.
 * 
 * @author xor
 */
public final class WoTMessageFetcher extends MessageFetcher {
	
	private static final int STARTUP_DELAY = Freetalk.FAST_DEBUG_MODE ? (1 * 60 * 1000) : (3 * 60 * 1000);
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (3 * 60 * 1000) : (5 * 60 * 1000);
	
	private static final int MAX_PARALLEL_MESSAGE_FETCH_COUNT = 16;
	
	private final Random mRandom;
	
	private final RequestClient requestClient;
	
	/**
	 * For each <code>ClientGetter</code> (= an object associated with a fetch) this hashtable stores the ID of the MessageList to which the
	 * message which is being fetched belongs.
	 */
	private final Hashtable<ClientGetter, String> mMessageLists = new Hashtable<ClientGetter, String>(MAX_PARALLEL_MESSAGE_FETCH_COUNT * 2);
	
	
	/**
	 * Contains a list of messages we are currently trying to fetch. Used for preventing parallel fetch attempts of the same message.
	 */
	private final HashSet<FreenetURI> mMessages = new HashSet<FreenetURI>(MAX_PARALLEL_MESSAGE_FETCH_COUNT * 2);
	

	public WoTMessageFetcher(Node myNode, HighLevelSimpleClient myClient, String myName, WoTIdentityManager myIdentityManager, WoTMessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		mRandom = mNode.fastWeakRandom;
		requestClient = myMessageManager.mRequestClient;
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
		abortAllTransfers();
		fetchMessages();
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	private void fetchMessages() {
		synchronized(mMessageManager) { 
			/* TODO: Obtain WoTMessageLists only, not all. */
			Iterator<MessageList.MessageReference> iter = mMessageManager.notDownloadedMessageIterator();
			while(iter.hasNext()) {
				/* FIXME: Investigate whether this is fair, i.e. whether it will not always try to download the same messages if downloading
				 * of some of them stalls for so long that the thread re-iterates before onFailure is called which results in the messages
				 * not being marked as undownloadable. This fairness can be guranteed by either having a THREAD_PERIOD which is high enough (which is 
				 * difficult because it depends on the node's speed) or randomizing notDownloadedMessageIterator() (the proper solution). */
				if(fetchCount() >= MAX_PARALLEL_MESSAGE_FETCH_COUNT) {
					Logger.debug(this, "Got " + fetchCount() + "fetches, not fetching any more.");
					break;
				}
				
				MessageList.MessageReference ref = iter.next();
				fetchMessage(ref.getMessageList(), ref);
			}
		}
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	private void fetchMessage(MessageList list, MessageList.MessageReference ref) {
		try {
			fetchMessage(list, ref.getURI());
		}
		catch(Exception e) {
			Logger.error(this, "Error while trying to fetch message " + ref.getURI(), e);
		}
	}

	/**
	 * You have to synchronize on this <code>WoTMessageFetcher</code> when using this function.
	 */
	private void fetchMessage(MessageList list, FreenetURI uri) throws FetchException {
		if(mMessages.add(uri) == false)// The message is already being fetched.
			return;

		try {
			FetchContext fetchContext = mClient.getFetchContext();
			fetchContext.maxSplitfileBlockRetries = 2;
			fetchContext.maxNonSplitfileRetries = 2;
			ClientGetter g = mClient.fetch(uri, -1, requestClient, this, fetchContext, RequestStarter.UPDATE_PRIORITY_CLASS);
			addFetch(g);
			mMessageLists.put(g, list.getID());
			Logger.debug(this, "Trying to fetch message from " + uri);
		}
		catch(RuntimeException e) {
			mMessages.remove(uri);
			throw e;
		}
	}

	@Override
	public synchronized void onSuccess(FetchResult result, ClientGetter state, ObjectContainer container) {
		Logger.debug(this, "Fetched message: " + state.getURI());
		
		Bucket bucket = null;
		InputStream inputStream = null;
		WoTMessageList list = null;
		
		boolean fetchMoreMessages = false;
		
		synchronized(mMessageManager) {
		try {
			list = (WoTMessageList)mMessageManager.getMessageList(mMessageLists.get(state));
			bucket = result.asBucket();
			inputStream = bucket.getInputStream();
			Message message = WoTMessageXML.decode(mMessageManager, inputStream, list, state.getURI());
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
			removeFetch(state);
		}
		}
		
		// We only call fetchMessages() if we know that the current message was marked as fetched in the database, otherwise the fetch thread could get stuck
		// in a busy loop: "fetch(), onSuccess(), fetch(), onSuccess(), ..."
		if(fetchMoreMessages)
			fetchMessages();
	}
	
	@Override
	public synchronized void onFailure(FetchException e, ClientGetter state, ObjectContainer container) {
		try {
			switch(e.getMode()) {
				case FetchException.DATA_NOT_FOUND:
					try {
						synchronized(mMessageManager) {
						WoTMessageList list = (WoTMessageList)mMessageManager.getMessageList(mMessageLists.get(state));
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
					finally {
						Logger.normal(this, "DNF for message " + state.getURI());
					}
					break;
					
				case FetchException.CANCELLED:
					Logger.debug(this, "Cancelled downloading Message " + state.getURI());
					break;
					
				default:
					Logger.error(this, "Downloading message " + state.getURI() + " failed.", e);
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
