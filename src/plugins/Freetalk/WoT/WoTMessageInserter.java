/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Random;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageInserter;
import plugins.Freetalk.OwnMessage;

import com.db4o.ObjectContainer;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientPutter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;

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
	 * For each <code>BaseClientPutter</code> (= an object associated with an insert) this hashtable stores the ID of the message which is being
	 * inserted by the <code>BaseClientPutter</code>.
	 */
	private final Hashtable<BaseClientPutter, String> mMessageIDs = new Hashtable<BaseClientPutter, String>(2*ESTIMATED_PARALLEL_MESSAGE_INSERT_COUNT);
	
	private final WoTMessageXML mXML;
	
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
		
		synchronized(mMessageManager) {
			for(WoTOwnMessage message : mMessageManager.getNotInsertedOwnMessages()) {
				try {
					// TODO: Remove the debug code if we are sure that db4o works
					if(!message.testRealURIisNull())
						continue;
					
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
		Bucket tempB = mTBF.makeBucket(2048 + m.getText().length()); /* TODO: set to a reasonable value */
		OutputStream os = null;
		
		try {
			os = tempB.getOutputStream();
			mXML.encode(m, os);
			os.close(); os = null;
			tempB.setReadOnly();

			/* We do not specifiy a ClientMetaData with mimetype because that would result in the insertion of an additional CHK */
			InsertBlock ib = new InsertBlock(tempB, null, m.getInsertURI());
			InsertContext ictx = mClient.getInsertContext(true);

			ClientPutter pu = mClient.insert(ib, false, null, false, ictx, this, RequestStarter.INTERACTIVE_PRIORITY_CLASS);
			addInsert(pu);
			mMessageIDs.put(pu, m.getID());
			tempB = null;

			Logger.debug(this, "Started insert of message from " + m.getAuthor().getNickname());
		}
		finally {
			if(tempB != null)
				tempB.free();
			Closer.close(os);
		}
	}

	@Override
	public synchronized void onSuccess(BaseClientPutter state, ObjectContainer container) {
		try {
			mMessageManager.onOwnMessageInserted(mMessageIDs.get(state), state.getURI());
		}
		catch(Exception e) {
			Logger.error(this, "Message insert finished but onSuccess() failed", e);
		}
		finally {
			removeInsert(state);
		}
	}
	
	@Override
	public synchronized void onFailure(InsertException e, BaseClientPutter state, ObjectContainer container) {
		try {
			Logger.error(this, "Message insert failed", e);
		}
		
		finally {
			removeInsert(state);
		}
	}
	
	/**
	 * This method must be synchronized because onFailure is synchronized and TransferThread calls abortAllTransfers() during shutdown without
	 * synchronizing on this object.
	 */
	protected synchronized void abortAllTransfers() {
		super.abortAllTransfers();
		mMessageIDs.clear();
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageInserter</code> when using this function.
	 */
	@Override
	protected void removeInsert(BaseClientPutter p) {
		super.removeInsert(p);
		mMessageIDs.remove(p);
	}

	
	/* Not needed functions*/
	
	@Override
	public void onSuccess(FetchResult result, ClientGetter state, ObjectContainer container) { }
	
	@Override
	public void onFailure(FetchException e, ClientGetter state, ObjectContainer container) { }
	
	@Override
	public void onGeneratedURI(FreenetURI uri, BaseClientPutter state, ObjectContainer container) { }
	
	@Override
	public void onFetchable(BaseClientPutter state, ObjectContainer container) { }

	@Override
	public void onMajorProgress(ObjectContainer container) { }
	
}
