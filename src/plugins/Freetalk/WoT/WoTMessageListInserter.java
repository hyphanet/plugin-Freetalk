package plugins.Freetalk.WoT;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import com.db4o.ObjectContainer;

import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageListInserter;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
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
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;

public final class WoTMessageListInserter extends MessageListInserter {
	
	private static final int STARTUP_DELAY = 1 * 60 * 1000;
	private static final int THREAD_PERIOD = 5 * 60 * 1000; /* FIXME: tweak before release */
	private static final int MAX_PARALLEL_MESSAGELIST_INSERT_COUNT = 8;

	private final WoTMessageManager mMessageManager;
	
	private final Random mRandom;

	public WoTMessageListInserter(Node myNode, HighLevelSimpleClient myClient, String myName, WoTIdentityManager myIdentityManager,
			WoTMessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
		mMessageManager = myMessageManager;
		mRandom = mNode.fastWeakRandom;
		start();
	}
	
	@Override
	protected void clearBeingInsertedFlags() {
		WoTMessageManager messageManager = (WoTMessageManager)super.mMessageManager;
		synchronized(messageManager) {
			for(WoTOwnMessageList list : messageManager.getBeingInsertedOwnMessageLists()) {
				try {
					messageManager.onMessageListInsertFailed(list.getURI(), false);
				} catch (NoSuchMessageListException e) {
					Logger.error(this, "SHOULD NOT HAPPEN", e);
				}
			}
		}
	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		return null;
	}

	@Override
	protected Collection<BaseClientPutter> createInsertStorage() {
		return new HashSet<BaseClientPutter>(MAX_PARALLEL_MESSAGELIST_INSERT_COUNT * 2);
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
			for(WoTOwnMessageList list : mMessageManager.getNotInsertedOwnMessageLists()) {
				try {
					/* FIXME: Ensure that after creation of a message list we wait for at least a few minutes so that if the author writes 
					 * more messages they will be put in the same list */
					insertMessageList(list);
				}
				catch(Exception e) {
					Logger.error(this, "Insert of WoTOwnMessageList failed", e);
				}
			}
		}
	}
	
	/**
	 * You have to synchronize on this <code>WoTMessageListInserter</code> and then on the <code>WoTMessageManager</code> when using this function.
	 */
	private void insertMessageList(WoTOwnMessageList list) throws TransformerException, ParserConfigurationException, NoSuchMessageException, IOException, InsertException {
		Bucket tempB = mTBF.makeBucket(4096); /* TODO: set to a reasonable value */
		OutputStream os = null;
		
		try {
			os = tempB.getOutputStream();
			// This is what requires synchronization on the WoTMessageManager: While being marked as "being inserted", message lists cannot be modified anymore,
			// so it must be guranteed that the "being inserted" mark does not change while we encode the XML etc.
			mMessageManager.onMessageListInsertStarted(list);
			
			WoTMessageListXML.encode(mMessageManager, list, os);
			os.close(); os = null;
			tempB.setReadOnly();

			/* We do not specifiy a ClientMetaData with mimetype because that would result in the insertion of an additional CHK */
			InsertBlock ib = new InsertBlock(tempB, null, list.getInsertURI());
			InsertContext ictx = mClient.getInsertContext(true);

			ClientPutter pu = mClient.insert(ib, false, null, false, ictx, this);
			// pu.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS); /* pluginmanager defaults to interactive priority */
			addInsert(pu);
			tempB = null;

			Logger.debug(this, "Started insert of WoTOwnMessageList at request URI " + list.getURI());
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
			Logger.debug(this, "Successfully inserted WoTOwnMessageList at " + state.getURI());
			mMessageManager.onMessageListInsertSucceeded(state.getURI());
		}
		catch(Exception e) {
			Logger.error(this, "WoTOwnMessageList insert succeeded but onSuccess() failed", e);
		}
		finally {
			removeInsert(state);
		}
	}

	@Override
	public synchronized void onFailure(InsertException e, BaseClientPutter state, ObjectContainer container) {
		try {
			if(e.getMode() == InsertException.COLLISION) {
				Logger.error(this, "WoTOwnMessageList insert collided, trying to insert with higher index ...");
				try {
					synchronized(mMessageManager) {
						mMessageManager.onMessageListInsertFailed(state.getURI(), true);
						insertMessageList((WoTOwnMessageList)mMessageManager.getOwnMessageList(MessageList.getIDFromURI(state.getURI())));
					}
				}
				catch(Exception ex) {
					Logger.error(this, "Inserting WoTOwnMessageList with higher index failed", ex);
				}
			} else
				mMessageManager.onMessageListInsertFailed(state.getURI(), false);
		}
		catch(Exception ex) {
			Logger.error(this, "WoTOwnMessageList insert failed", ex);
		}
		finally {
			removeInsert(state);
		}
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
