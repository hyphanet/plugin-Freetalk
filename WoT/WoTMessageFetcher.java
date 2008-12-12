/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageFetcher;
import plugins.Freetalk.MessageXML;
import plugins.Freetalk.exceptions.NoSuchMessageException;

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
	
	private static final int PARALLEL_MESSAGE_FETCH_COUNT = 128;
	
	private final WoTIdentityManager mIdentityManager;
	private final WoTMessageManager mMessageManager;
	
	private Random mRandom;
	
	private final ObjectContainer db;
	
	/* FIXME FIXME FIXME: Use LRUQueue instead. ArrayBlockingQueue does not use a Hashset for contains()! */
	private final ArrayBlockingQueue<FTIdentity> mIdentities = new ArrayBlockingQueue<FTIdentity>(PARALLEL_MESSAGE_FETCH_COUNT * 10); /* FIXME: figure out a decent size */
	
	private static final Calendar mCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	public WoTMessageFetcher(Node myNode, HighLevelSimpleClient myClient, ObjectContainer myDB, String myName, WoTIdentityManager myIdentityManager, WoTMessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		db = myDB;
		mIdentityManager = myIdentityManager;
		mMessageManager = myMessageManager;
		mRandom = mNode.fastWeakRandom;
		start();
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
		ArrayList<FTIdentity> identitiesToFetchFrom = new ArrayList<FTIdentity>(PARALLEL_MESSAGE_FETCH_COUNT);
		
		for(FTIdentity identity : mIdentityManager) {
			synchronized(mIdentities) {
				if(!mIdentities.contains(identity) && mIdentityManager.anyOwnIdentityWantsMessagesFrom(identity)) {
					identitiesToFetchFrom.add(identity);
					mIdentities.add(identity);
				}
			}
		}
		
		/* mIdentities contains all identities which are available, so we have to flush it. */
		if(identitiesToFetchFrom.size() == 0) {
			 mIdentities.clear();
			
			 for(FTIdentity identity : mIdentityManager) {
				 synchronized(mIdentities) {
					 if(mIdentityManager.anyOwnIdentityWantsMessagesFrom(identity)) {
						 identitiesToFetchFrom.add(identity);
						 mIdentities.add(identity);
					 }
				 }
			 }
		}
		
		for(FTIdentity identity : identitiesToFetchFrom) {
			try {
				fetchMessages(identity);
			}
			catch(FetchException e) {
				Logger.error(this, "Fetching of messages from " + identity.getNickname() + " failed.", e);
			}
		}
	}
	
	private void fetchMessages(FTIdentity author) throws FetchException {
		fetchMessage(author, mMessageManager.getUnavailableMessageIndex(author));
	}
	
	private void fetchMessage(FTIdentity author, int index) throws FetchException {
		FreenetURI uri = Message.generateRequestURI(author, index);
		FetchContext fetchContext = mClient.getFetchContext();
		fetchContext.maxSplitfileBlockRetries = 2;
		fetchContext.maxNonSplitfileRetries = 2;
		ClientGetter g = mClient.fetch(uri, -1, this, this, fetchContext);
		//g.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS); /* pluginmanager defaults to interactive priority */
		addFetch(g);
		synchronized(mIdentities) {
			if(!mIdentities.contains(author)) {
				mIdentities.poll();	/* the oldest identity falls out of the FIFO and therefore message downloads from that one are allowed again */
				try {
					mIdentities.put(author); /* put this identity at the beginning of the FIFO */
				} catch(InterruptedException e) {}
			}
		}
		Logger.debug(this, "Trying to fetch message from " + uri.toString());
	}

	@Override
	public void onSuccess(FetchResult result, ClientGetter state) {
		Logger.debug(this, "Fetched message: " + state.getURI());
		
		try {
			Message message = MessageXML.decode(mMessageManager, result.asBucket().getInputStream(), mIdentityManager.getIdentityByURI(state.getURI()), state.getURI());
			try {
				mMessageManager.get(message.getID());
			}
			catch(NoSuchMessageException e) {
				message.store();
			}
			removeFetch(state);
			
			fetchMessage(message.getAuthor(), message.getIndex() + 1);
		}
		catch (Exception e) {
			Logger.error(this, "Parsing failed for message " + state.getURI());
		}
	}
	
	@Override
	public void onFailure(FetchException e, ClientGetter state) {
		Logger.debug(this, "Downloading message " + state.getURI() + " failed.", e);
		removeFetch(state);
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
