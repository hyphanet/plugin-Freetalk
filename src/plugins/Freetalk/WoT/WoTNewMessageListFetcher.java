/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;

import plugins.Freetalk.FetchFailedMarker;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageListFetcher;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.Persistent.IndexedClass;
import plugins.Freetalk.exceptions.NoSuchIdentityException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.client.FetchContext;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientContext;
import freenet.client.async.USKManager;
import freenet.client.async.USKRetriever;
import freenet.client.async.USKRetrieverCallback;
import freenet.keys.FreenetURI;
import freenet.keys.USK;
import freenet.node.PrioRunnable;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.Logger;
import freenet.support.TrivialTicker;
import freenet.support.api.Bucket;
import freenet.support.codeshortification.IfNull;
import freenet.support.io.Closer;
import freenet.support.io.NativeThread;

/**
 * Permanently subscribes to the {@link WoTMessageList} USKs of all known identities.
 * 
 * TODO: This class was copy-pasted from plugins.WoT.IdentityFetcher and adapted & improved. The changes should be backported.
 *  
 * @author xor (xor@freenetproject.org)
 */
public final class WoTNewMessageListFetcher implements MessageListFetcher, USKRetrieverCallback, PrioRunnable, IdentityManager.ShouldFetchStateChangedCallback {

	private static final long PROCESS_COMMANDS_DELAY = 60 * 1000;
	
	private final Freetalk mFreetalk;
	private final WoTIdentityManager mIdentityManager;
	private final WoTMessageManager mMessageManager;
	private final ExtObjectContainer mDB;
	
	private final HighLevelSimpleClient mClient;
	private final ClientContext mClientContext;
	private final RequestClient mRequestClient;
	private final USKManager mUSKManager;

	/** All current requests */
	/* TODO: We use those HashSets for checking whether we have already have a request for the given identity if someone calls fetch().
	 * This sucks: We always request ALL identities to allow ULPRs so we must assume that those HashSets will not fit into memory
	 * if the WoT becomes large. We should instead ask the node whether we already have a request for the given SSK URI. 
	 * Therefore, we should change the node to provide interfaces which serve the purposes of this HashMap. */
	private final HashMap<String, USKRetriever> mRequests = new HashMap<String, USKRetriever>(1024); /* TODO: profile & tweak */
	
	private final TrivialTicker mTicker;
	
	private final WoTMessageListXML mXML;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(WoTNewMessageListFetcher.class);
	}
	
	
	
	public WoTNewMessageListFetcher(Freetalk myFreetalk, String myName, WoTMessageListXML myMessageListXML, ExtObjectContainer myDB) {
		mFreetalk = myFreetalk;
		
		mIdentityManager = mFreetalk.getIdentityManager();
		mMessageManager = mFreetalk.getMessageManager();
		mDB = myDB;
		
		mClient = mFreetalk.getPluginRespirator().getHLSimpleClient();
		mClientContext = mFreetalk.getPluginRespirator().getNode().clientCore.clientContext;
		mRequestClient = mMessageManager.mRequestClient;
		mUSKManager = mFreetalk.getPluginRespirator().getNode().clientCore.uskManager;
		
		mTicker = new TrivialTicker(mFreetalk.getPluginRespirator().getNode().executor);
		
		mXML = myMessageListXML;
		
		deleteAllCommands();
		mIdentityManager.registerShouldFetchStateChangedCallback(this);
	}

	public static abstract class FetcherCommand extends Persistent {
		
		@IndexedField
		private final String mIdentityID;


		protected FetcherCommand(String myIdentityID) {
			mIdentityID = myIdentityID;
		}
		
		@Override
		public void databaseIntegrityTest() throws Exception {
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
			IfNull.thenThrow(mIdentityID, "mIdentityID");
		}
		
		protected String getIdentityID() {
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
			return mIdentityID;
		}
		
		protected void storeWithoutCommit() {
			super.storeWithoutCommit(1);
		}
		
		protected void deleteWithoutCommit() {
			super.deleteWithoutCommit(1);
		}

	}
	
	@IndexedClass
	public static final class StartFetchCommand extends FetcherCommand {

		protected StartFetchCommand(WoTIdentity identity) {
			super(identity.getID());
		}
		
		protected StartFetchCommand(String identityID) {
			super(identityID);
		}
		
	}
	
	@IndexedClass
	public static final class AbortFetchCommand extends FetcherCommand {

		protected AbortFetchCommand(WoTIdentity identity) {
			super(identity.getID());
		}
		
	}
	
	@IndexedClass
	public static final class UpdateEditionHintCommand extends FetcherCommand {

		protected UpdateEditionHintCommand(WoTIdentity identity) {
			super(identity.getID());
		}
		
		protected UpdateEditionHintCommand(String identityID) {
			super(identityID);
		}
		
	}
	
	private static final class NoSuchCommandException extends Exception {
		private static final long serialVersionUID = 1L;
	}
	
	private static final class DuplicateCommandException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	private FetcherCommand getCommand(Class<? extends FetcherCommand> commandType, WoTIdentity identity) throws NoSuchCommandException {
		return getCommand(commandType, identity.getID());
	}
	
	private FetcherCommand getCommand(Class<? extends FetcherCommand> commandType, String identityID) throws NoSuchCommandException {
		final Query q = mDB.query();
		q.constrain(commandType);
		q.descend("mIdentityID").constrain(identityID);
		final ObjectSet<FetcherCommand> result = new Persistent.InitializingObjectSet<FetcherCommand>(mFreetalk, q);
		
		switch(result.size()) {
			case 1: return result.next();
			case 0: throw new NoSuchCommandException();
			default: throw new DuplicateCommandException();
		}
	}
	
	private ObjectSet<FetcherCommand> getCommands(Class<? extends FetcherCommand> commandType) {
		final Query q = mDB.query();
		q.constrain(commandType);
		return new Persistent.InitializingObjectSet<FetcherCommand>(mFreetalk, q);
	}
	
	private synchronized void deleteAllCommands() {
		synchronized(Persistent.transactionLock(mDB)) {
			try {
				if(logDEBUG) Logger.debug(this, "Deleting all commands ...");
				
				int amount = 0;
				
				for(FetcherCommand command : getCommands(FetcherCommand.class)) {
					command.deleteWithoutCommit();
					++amount;
				}
				
				if(logDEBUG) Logger.debug(this, "Deleted " + amount + " commands.");
				
				Persistent.checkedCommit(mDB, this);
			}
			catch(RuntimeException e) {
				Persistent.checkedRollbackAndThrow(mDB, this, e);
			}
		}
	}
	
	private void storeStartFetchCommandWithoutCommit(WoTIdentity identity) {
		storeStartFetchCommandWithoutCommit(identity.getID());
	}
	
	private void storeStartFetchCommandWithoutCommit(String identityID) {
		if(logDEBUG) Logger.debug(this, "Start fetch command received for " + identityID);
		
		try {
			getCommand(AbortFetchCommand.class, identityID).deleteWithoutCommit();
			if(logDEBUG) Logger.debug(this, "Deleting abort fetch command for " + identityID);
		}
		catch(NoSuchCommandException e) { }
		
		try {
			getCommand(StartFetchCommand.class, identityID);
			if(logDEBUG) Logger.debug(this, "Start fetch command already in queue!");
		}
		catch(NoSuchCommandException e) {
			final StartFetchCommand c = new StartFetchCommand(identityID);
			c.initializeTransient(mFreetalk);
			c.storeWithoutCommit();
			scheduleCommandProcessing();
		}
	}
	
	private void storeAbortFetchCommandWithoutCommit(WoTIdentity identity) {
		if(logDEBUG) Logger.debug(this, "Abort fetch command received for " + identity);
		
		try {
			getCommand(StartFetchCommand.class, identity).deleteWithoutCommit();
			if(logDEBUG) Logger.debug(this, "Deleting start fetch command for " + identity);
		}
		catch(NoSuchCommandException e) { }
		
		try {
			getCommand(AbortFetchCommand.class, identity);
			if(logDEBUG) Logger.debug(this, "Abort fetch command already in queue!");
		}
		catch(NoSuchCommandException e) {
			final AbortFetchCommand c = new AbortFetchCommand(identity);
			c.initializeTransient(mFreetalk);
			c.storeWithoutCommit();
			scheduleCommandProcessing();
		}
	}
	
	private void storeUpdateEditionHintCommandWithoutCommit(String identityID) {
		if(logDEBUG) Logger.debug(this, "Update edition hint command received for " + identityID);
		
		try {
			getCommand(AbortFetchCommand.class, identityID);
			Logger.error(this, "Update edition hint command is useless, an abort fetch command is queued!");
		}
		catch(NoSuchCommandException e1) {
			try {
				getCommand(UpdateEditionHintCommand.class, identityID);
				if(logDEBUG) Logger.debug(this, "Update edition hint command already in queue!");
			}
			catch(NoSuchCommandException e2) {
				final UpdateEditionHintCommand c = new UpdateEditionHintCommand(identityID);
				c.initializeTransient(mFreetalk);
				c.storeWithoutCommit();
				scheduleCommandProcessing();
			}
		}
	}
	
	private void scheduleCommandProcessing() {
		mTicker.queueTimedJob(this, "FT NewMessageListFetcher", PROCESS_COMMANDS_DELAY, false, true);
	}
	
	public int getPriority() {
		return NativeThread.LOW_PRIORITY;
	}
	
	public void start() { 
		if(logDEBUG) Logger.debug(this, "Starting new-messagelist-fetches of all identities...");
		synchronized(this) {
		synchronized(mIdentityManager) {
			for(WoTIdentity identity : mIdentityManager.getAllIdentities()) {
				// The connection to WoT might not exist yet so we just fetch all identities.
				// The identity manager will abort the fetches of obsolete identities while garbage collecting them
				//if(mIdentityManager.anyOwnIdentityWantsMessagesFrom(identity)) {
					try {
						fetch(identity);
					}
					catch(Exception e) {
						Logger.error(this, "Fetching identity failed!", e);
					}
				//}
			}
		}
		}
	}
	
	public void run() {
		synchronized(this) {
		synchronized(mIdentityManager) { // Lock needed because we do getIdentityByID() in fetch()
		synchronized(mMessageManager) { // For getting latest edition numbers. TODO: Maybe cache them in the identity
		synchronized(Persistent.transactionLock(mDB)) {
			try  {
				if(logDEBUG) Logger.debug(this, "Processing commands ...");
				
				for(FetcherCommand command : getCommands(AbortFetchCommand.class)) {
					try {
						abortFetch(command.getIdentityID());
						command.deleteWithoutCommit();
					} catch(Exception e) {
						Logger.error(this, "Aborting fetch failed", e);
					}
				}
				
				for(FetcherCommand command : getCommands(StartFetchCommand.class)) {
					try {
						fetch(command.getIdentityID());
						command.deleteWithoutCommit();
					} catch (Exception e) {
						Logger.error(this, "Fetching identity failed", e);
					}
					
				}
				
				for(FetcherCommand command : getCommands(UpdateEditionHintCommand.class)) {
					try {
						updateEditionHint(command.getIdentityID());
						command.deleteWithoutCommit();
					} catch (Exception e) { 
						Logger.error(this, "Updating edition hint failed", e);
					}
				}
				
				if(logDEBUG) Logger.debug(this, "Processing finished.");
				
				Persistent.checkedCommit(mDB, this);
			} catch(RuntimeException e) {
				Persistent.checkedRollback(mDB, this, e);
			}
		}
		}
		}
		}
	}
	
	private synchronized void fetch(String identityID) throws Exception {
		try {
			synchronized(mIdentityManager) {
				WoTIdentity identity = mIdentityManager.getIdentity(identityID);
				fetch(identity);
			}
		} catch (NoSuchIdentityException e) {
			Logger.normal(this, "Fetching identity failed, it was deleted already.", e);
		}
	}

	/**
	 * Subscribes to the {@link WoTMessageListUSK} of the given identity, using the latest unavailable message list index.
	 * If the identity is already being fetched, logs an error and does nothing.
	 */
	private synchronized void fetch(WoTIdentity identity) throws Exception {
		if(mRequests.get(identity.getID()) != null) {
			Logger.error(this, "Fetch already exists for identity " + identity);
			return;
		}

		final USK usk;
		final long editionHint;
		
		synchronized(mMessageManager) { // Don't acquire the lock twice
			usk = USK.create(WoTMessageList.generateURI(identity, mMessageManager.getUnavailableNewMessageListIndex(identity)));
			editionHint = mMessageManager.getNewMessageListIndexEditionHint(identity);
		}
			
		final USKRetriever retriever = fetch(usk);
		mRequests.put(identity.getID(), retriever);
		updateEditionHint(retriever, editionHint);
	}
	
	public int getRunningFetchCount() {
		return mRequests.size();
	}
	
	private synchronized void abortFetch(String identityID) {
		USKRetriever retriever = mRequests.remove(identityID);

		if(retriever == null) {
			Logger.error(this, "Aborting fetch failed (no fetch found) for identity " + identityID);
			return;
		}
			
		if(logDEBUG) Logger.debug(this, "Aborting fetch for identity " + identityID);
		abortFetch(retriever);
	}
	
	/**
	 * Has to be called when the edition hint of the given identity was updated. Tells the USKManager about the new hint.
	 * @throws Exception 
	 */
	private synchronized void updateEditionHint(String identityID) throws Exception {
		try {
			final WoTIdentity identity = mIdentityManager.getIdentity(identityID);
			final USKRetriever retriever = mRequests.get(identityID);
				
			if(retriever == null)
				throw new NoSuchIdentityException("updateEdtitionHint() called for an identity which is not being fetched: " + identityID);

			final long editionHint = mMessageManager.getNewMessageListIndexEditionHint(identity);
				
			if(logDEBUG) Logger.debug(this, "Updating edition hint to " + editionHint + " for " + identityID);
			updateEditionHint(retriever, editionHint);
		} catch (NoSuchIdentityException e) {
			Logger.error(this, "Updating edition hint failed, the identity was deleted already.", e);
		}
	}
	
	/**
	 * Fetches the given USK and returns the new USKRetriever. Does not check whether there is already a fetch for that USK.
	 */
	private USKRetriever fetch(USK usk) throws MalformedURLException {
		FetchContext fetchContext = mClient.getFetchContext();
		fetchContext.maxSplitfileBlockRetries = -1; // retry forever
		fetchContext.maxNonSplitfileRetries = -1; // retry forever
		fetchContext.maxOutputLength = WoTMessageListXML.MAX_XML_SIZE;
		if(logDEBUG) Logger.debug(this, "Subscribing to WoTMessageList queue " + usk); 
		return mUSKManager.subscribeContent(usk, this, true, fetchContext, RequestStarter.UPDATE_PRIORITY_CLASS, mRequestClient);
	}
	
	private void abortFetch(USKRetriever retriever) {
		retriever.cancel(mClientContext);
		mUSKManager.unsubscribeContent(retriever.getOriginalUSK(), retriever, true);
	}
	
	private void updateEditionHint(USKRetriever retriever, long newHint) {
		mUSKManager.hintUpdate(retriever.getOriginalUSK(), newHint, mClientContext);
	}

	/**
	 * Stops all running requests.
	 */
	public synchronized void stop() {
		if(logDEBUG) Logger.debug(this, "Trying to stop all requests");
		
		mTicker.shutdown();
		
		USKRetriever[] retrievers = mRequests.values().toArray(new USKRetriever[mRequests.size()]);		
		int counter = 0;		 
		for(USKRetriever r : retrievers) {
			r.cancel(mClientContext);
			mUSKManager.unsubscribeContent(r.getOriginalUSK(), r, true);
			 ++counter;
		}
		mRequests.clear();
		
		if(logDEBUG) Logger.debug(this, "Stopped " + counter + " current requests");
	}
	
	public short getPollingPriorityNormal() {
		return RequestStarter.UPDATE_PRIORITY_CLASS;
	}

	public short getPollingPriorityProgress() {
		return RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS;
	}
	

	/**
	 * Called by the {@link IdentityManager} when the should-fetch state of an identity changed.
	 * This happens when a new identity is added or an existing one is deleted.
	 * 
	 * Schedules start-fetch/abort-fetch commands.
	 */
	public void onShouldFetchStateChanged(Identity messageAuthor, boolean oldShouldFetch, boolean newShouldFetch) {
		if(oldShouldFetch == newShouldFetch) {
			throw new IllegalArgumentException("oldShouldFetch==newShouldFetch==" + newShouldFetch);
		}
		
		if(newShouldFetch == true) {
			storeStartFetchCommandWithoutCommit((WoTIdentity)messageAuthor);
		} else {
			storeAbortFetchCommandWithoutCommit((WoTIdentity)messageAuthor);
		}
	}

	/**
	 * Called when a {@link WoTMessageList} was successfully fetched.
	 */
	public void onFound(USK origUSK, long edition, FetchResult result) {
		final FreenetURI uri = origUSK.getURI().setSuggestedEdition(edition);
		
		Logger.normal(this, "Fetched WoTMessageList: " + uri);

		Bucket bucket = null;
		InputStream inputStream = null;
		
		try {
			bucket = result.asBucket();
			inputStream = bucket.getInputStream();
			
			synchronized(mIdentityManager) {
				final WoTIdentity identity = (WoTIdentity)mIdentityManager.getIdentityByURI(uri);
				
				synchronized(mMessageManager) {
					try {
						WoTMessageList list = mXML.decode(mFreetalk, identity, uri, inputStream);
						mMessageManager.onMessageListReceived(list);
					}
					catch (Exception e) {
						Logger.error(this, "Parsing failed for MessageList " + uri, e);
						mMessageManager.onMessageListFetchFailed(identity, uri, FetchFailedMarker.Reason.ParsingFailed);
					}
				}
			}
		}
		catch (NoSuchIdentityException e) {
			Logger.normal(this, "Identity was deleted already, ignoring MessageList " + uri);
		}
		catch (IOException e) {
			Logger.error(this, "getInputStream() failed.", e);
		}
		finally {
			Closer.close(inputStream);
			Closer.close(bucket);
		}
	}

}
