/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
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
import freenet.client.ClientMetadata;
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

public class WoTMessageInserter extends MessageInserter {

	protected static final int STARTUP_DELAY = 1 * 60 * 1000;
	protected static final int THREAD_PERIOD = 5 * 60 * 1000; /* FIXME: tweak before release */
	private Random mRandom;
	
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
		return new ArrayList<BaseClientPutter>(10);
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
		Logger.debug(this, "Message inserter loop running ...");
		abortAllTransfers();
		
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
		Logger.debug(this, "Message inserter loop finished.");
	}
	
	protected void insertMessage(OwnMessage m) throws InsertException, IOException, TransformerException, ParserConfigurationException {
		Bucket tempB = mTBF.makeBucket(2048 + m.getText().length()); /* TODO: set to a reasonable value */
		OutputStream os = tempB.getOutputStream();
		
		try {
			MessageXML.encode(m, os);
			os.close(); os = null;
			tempB.setReadOnly();

			ClientMetadata cmd = new ClientMetadata("text/xml");
			InsertBlock ib = new InsertBlock(tempB, cmd, m.getInsertURI());
			InsertContext ictx = mClient.getInsertContext(true);

			/* FIXME: are these parameters correct? */
			ClientPutter pu = mClient.insert(ib, false, null, false, ictx, this);
			// pu.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS); /* pluginmanager defaults to interactive priority */
			addInsert(pu);
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
	public void onSuccess(BaseClientPutter state) {
		try {
			OwnMessage m = (OwnMessage)mMessageManager.get(state.getURI());
			m.markAsInserted();
		}
		catch(NoSuchMessageException e) {
			Logger.error(this, "Message insert finished but message was deleted: " + state.getURI());
		}
		
		removeInsert(state);
	}
	
	@Override
	public void onFailure(InsertException e, BaseClientPutter state) {
		Logger.error(this, "Message insert failed", e);
		removeInsert(state);
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
