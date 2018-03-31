/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import static freenet.client.InsertException.InsertExceptionMode.CANCELLED;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageInserter;
import plugins.Freetalk.OwnMessage;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientPutter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.api.RandomAccessBucket;
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;
import freenet.support.io.ResumeFailedException;

/**
 * Periodically wakes up and inserts messages as CHK. The CHK URIs are then stored in the messages.
 * When downloading messages, their CHK URI will have to be obtained by the reader by downloading messagelists from the given identity.
 * Therefore, when a message is inserted by this class, only half of the work is done. After messages were inserted as CHK, the
 * <code>WoTMessageListInserter</code> will obtain the CHK URIs of the messages from the <code>MessageManager</code> and publish them in
 * a <code>MessageList</code>.
 * 
 * @author xor
 */
public final class WoTMessageInserter extends MessageInserter {

	private static final int STARTUP_DELAY = Freetalk.FAST_DEBUG_MODE ? (10 * 1000) : (10 * 60 * 1000);
	
	// TODO: The message inserter should not constantly wake up but rather receive an event notification when there are messages to be inserted.
	private static final int THREAD_PERIOD = Freetalk.FAST_DEBUG_MODE ? (2 * 60 * 1000) : (5 * 60 * 1000);
	
	private static final int ESTIMATED_PARALLEL_MESSAGE_INSERT_COUNT = 10;
	
	private final WoTMessageManager mMessageManager;
	
	private final Random mRandom;

	private final RequestClient mRequestClient;

	/**
	 * For each <code>BaseClientPutter</code> (= an object associated with an insert) this HashMap stores the ID of the message which is being
	 * inserted by the <code>BaseClientPutter</code>.
	 */
	private final HashMap<BaseClientPutter, String> mPutterMessageIDs = new HashMap<BaseClientPutter, String>(2*ESTIMATED_PARALLEL_MESSAGE_INSERT_COUNT);

	/**
	 * Contains the IDs of the message lists which are currently being inserted, used for preventing double inserts.
	 */
	private final HashSet<String> mMessageIDs = new HashSet<String>(2*ESTIMATED_PARALLEL_MESSAGE_INSERT_COUNT);
	
	private final WoTMessageXML mXML;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(WoTMessageInserter.class);
	}
	
	
	public WoTMessageInserter(Node myNode, HighLevelSimpleClient myClient, String myName, WoTIdentityManager myIdentityManager,
			WoTMessageManager myMessageManager, WoTMessageXML myMessageXML) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		mMessageManager = myMessageManager;
		mRandom = mNode.fastWeakRandom;
		mRequestClient = mMessageManager.mRequestClient;
		mXML = myMessageXML;
	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		return null;
	}

	@Override
	protected Collection<BaseClientPutter> createInsertStorage() {
		return new ArrayList<BaseClientPutter>(ESTIMATED_PARALLEL_MESSAGE_INSERT_COUNT);
	}

	@Override
	public int getPriority() {
		return NativeThread.NORM_PRIORITY;
	}

	@Override public RequestClient getRequestClient() {
		return mRequestClient;
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
		synchronized(mMessageManager) {
			for(WoTOwnMessage message : mMessageManager.getNotInsertedOwnMessages()) {
				try {
					// TODO: Remove this workaround for the db4o bug as soon as we are sure that it does not happen anymore.
					if(!message.testFreenetURIisNull()) // Logs an error for us
						continue;
					
					if(!mMessageIDs.contains(message.getID()))
						insertMessage(message);
				}
				catch(Exception e) {
					Logger.error(this, "Insert of message failed", e);
				}
			}
		}
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageInserter</code> when using this function.
	 */
	protected void insertMessage(OwnMessage m) throws InsertException, IOException, TransformerException, ParserConfigurationException {
		RandomAccessBucket tempB = mTBF.makeBucket(WoTMessageXML.MAX_XML_SIZE);
		OutputStream os = null;
		
		try {
			os = tempB.getOutputStream();
			mXML.encode(m, os);
			os.close(); os = null;
			tempB.setReadOnly();

			/* We do not specifiy a ClientMetaData with mimetype because that would result in the insertion of an additional CHK */
			InsertBlock ib = new InsertBlock(tempB, null, m.getInsertURI());
			InsertContext ictx = mClient.getInsertContext(true);
			ClientPutter pu = mClient.insert(
				ib, null, false, ictx, this, RequestStarter.INTERACTIVE_PRIORITY_CLASS);
			
			addInsert(pu);
			mPutterMessageIDs.put(pu, m.getID());
			mMessageIDs.add(m.getID());
			tempB = null;

			if(logDEBUG) Logger.debug(this, "Started insert of message from " + m.getAuthor().getNickname());
		}
		finally {
			if(tempB != null)
				tempB.free();
			Closer.close(os);
		}
	}
	
	@Override
	public synchronized void abortMessageInsert(String messageID) {
		if(!mMessageIDs.contains(messageID))
			return;
		
		// TODO: Optimization: If we ever run in public gateway mode and therefore have thousands of pending inserts this needs to be optimized
		for(Map.Entry<BaseClientPutter, String> entry : mPutterMessageIDs.entrySet()) {
			if(messageID.equals(entry.getValue())) {
				// The following will call onFailure which removes the request from mMessageIDs / mPutterMessageIDs
				entry.getKey().cancel(mClientContext);
				break;
			}
		}
	}

	@Override
	public synchronized void onSuccess(BaseClientPutter state) {
		try {
			mMessageManager.onOwnMessageInserted(mPutterMessageIDs.get(state), state.getURI());
		}
		catch(Exception e) {
			Logger.error(this, "Message insert finished but onSuccess() failed", e);
		}
		finally {
			removeInsert(state);
			Closer.close(((ClientPutter)state).getData());
		}
	}
	
	@Override
	public synchronized void onFailure(InsertException e, BaseClientPutter state) {
		try {
			if(e.getMode() == CANCELLED)
				Logger.normal(this, "Message insert cancelled for " + state.getURI());
			else if(e.isFatal())
				Logger.error(this, "Message insert failed", e);
			else
				Logger.warning(this, "Message insert failed non-fatally", e);
		}
		finally {
			removeInsert(state);
			Closer.close(((ClientPutter)state).getData());
		}
	}
	
	/**
	 * This method must be synchronized because onFailure is synchronized and TransferThread calls abortAllTransfers() during shutdown without
	 * synchronizing on this object.
	 */
	@Override protected synchronized void abortAllTransfers() {
		super.abortAllTransfers();
		mPutterMessageIDs.clear();
		mMessageIDs.clear();
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageInserter</code> when using this function.
	 */
	@Override
	protected void removeInsert(BaseClientPutter p) {
		super.removeInsert(p);
		mMessageIDs.remove(mPutterMessageIDs.remove(p));
	}

	
	/* Not needed functions*/
	
	@Override
	public void onSuccess(FetchResult result, ClientGetter state) { }
	
	@Override
	public void onFailure(FetchException e, ClientGetter state) { }
	
	@Override
	public void onGeneratedURI(FreenetURI uri, BaseClientPutter state) { }
	
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
