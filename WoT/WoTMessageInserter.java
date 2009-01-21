/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageInserter;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.MessageXML;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.exceptions.NoSuchMessageException;
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
import freenet.support.Logger;
import freenet.support.api.Bucket;
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

	private static final int STARTUP_DELAY = 1 * 60 * 1000;
	private static final int THREAD_PERIOD = 5 * 60 * 1000; /* FIXME: tweak before release */
	private static final int ESTIMATED_PARALLEL_MESSAGE_INSERT_COUNT = 10;
	
	private final Random mRandom;
	
	/**
	 * For each <code>BaseClientPutter</code> (= an object associated with an insert) this hashtable stores the ID of the message which is being
	 * inserted by the <code>BaseClientPutter</code>.
	 */
	private final Hashtable<BaseClientPutter, String> mMessageIDs = new Hashtable<BaseClientPutter, String>(2*ESTIMATED_PARALLEL_MESSAGE_INSERT_COUNT);
	
	public WoTMessageInserter(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager,
			MessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		mRandom = mNode.fastWeakRandom;
		start();
		Logger.debug(this, "Message inserter started.");
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
		Logger.debug(this, "Message inserter loop running ...");
		abortAllTransfers();
		
		synchronized(mMessageManager) {
			Iterator<OwnMessage> messages = mMessageManager.notInsertedMessageIterator();
			while(messages.hasNext()) {
				try {
					/* FIXME: Delay the messages!!!!! And set their date to reflect the delay */
					insertMessage(messages.next());
				}
				catch(Exception e) {
					Logger.error(this, "Insert of message failed", e);
				}
			}
		}
		Logger.debug(this, "Message inserter loop finished.");
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageInserter</code> when using this function.
	 */
	protected void insertMessage(OwnMessage m) throws InsertException, IOException, TransformerException, ParserConfigurationException {
		Bucket tempB = mTBF.makeBucket(2048 + m.getText().length()); /* TODO: set to a reasonable value */
		OutputStream os = null;
		
		try {
			os = tempB.getOutputStream();
			MessageXML.encode(m, os);
			os.close(); os = null;
			tempB.setReadOnly();

			/* We do not specifiy a ClientMetaData with mimetype because that would result in the insertion of an additional CHK */
			InsertBlock ib = new InsertBlock(tempB, null, m.getInsertURI());
			InsertContext ictx = mClient.getInsertContext(true);

			ClientPutter pu = mClient.insert(ib, false, null, false, ictx, this);
			// pu.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS); /* pluginmanager defaults to interactive priority */
			addInsert(pu);
			mMessageIDs.put(pu, m.getID());
			tempB = null;

			Logger.debug(this, "Started insert of message from " + m.getAuthor().getNickname());
		}
		finally {
			if(tempB != null)
				tempB.free();
			if(os != null)
				os.close();
		}
	}

	@Override
	public synchronized void onSuccess(BaseClientPutter state) {
		try {
			OwnMessage m = mMessageManager.getOwnMessage(mMessageIDs.get(state));
			m.markAsInserted(state.getURI());
			Logger.debug(this, "Successful insert of " + m.getURI());
		}
		catch(NoSuchMessageException e) {
			Logger.error(this, "Message insert finished but message was deleted: " + state.getURI());
		}
		
		finally {
			removeInsert(state);
		}
	}
	
	@Override
	public synchronized void onFailure(InsertException e, BaseClientPutter state) {
		try {
			Logger.error(this, "Message insert failed", e);
		}
		
		finally {
			removeInsert(state);
		}
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageInserter</code> when using this function.
	 */
	@Override
	protected void abortAllTransfers() {
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
	public void onSuccess(FetchResult result, ClientGetter state) { }
	
	@Override
	public void onFailure(FetchException e, ClientGetter state) { }
	
	@Override
	public void onGeneratedURI(FreenetURI uri, BaseClientPutter state) { }
	
	@Override
	public void onFetchable(BaseClientPutter state) { }

	@Override
	public void onMajorProgress() { }
	
}
