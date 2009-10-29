/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import plugins.Freetalk.MessageList.MessageFetchFailedMarker;
import plugins.Freetalk.MessageList.MessageListFetchFailedMarker;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessageFetcher;
import plugins.Freetalk.WoT.WoTMessageInserter;
import plugins.Freetalk.WoT.WoTMessageListFetcher;
import plugins.Freetalk.WoT.WoTMessageListInserter;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.tasks.PersistentTaskManager;
import plugins.Freetalk.ui.FCP.FCPInterface;
import plugins.Freetalk.ui.NNTP.FreetalkNNTPServer;
import plugins.Freetalk.ui.web.WebInterface;

import com.db4o.Db4o;
import com.db4o.config.Configuration;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.reflect.jdk.JdkReflector;

import freenet.clients.http.PageMaker.THEME;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThemed;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.FredPluginWithClassLoader;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

/**
 * @author xor@freenetproject.org
 * @author saces
 */
public class Freetalk implements FredPlugin, FredPluginFCP, FredPluginL10n, FredPluginThemed, FredPluginThreadless,
	FredPluginVersioned, FredPluginRealVersioned, FredPluginWithClassLoader {

	/* Constants */
	
	/** If set to true, all thread periods will be set to very low values, resulting in very fast message downloading. */
	public static final boolean FAST_DEBUG_MODE = false; // FIXME: Set to false before release!
	
	public static final String PLUGIN_URI = "/Freetalk";
	public static final String PLUGIN_TITLE = "Freetalk-testing"; /* FIXME REDFLAG: Has to be changed to Freetalk before release! Otherwise messages will disappear */
	public static final String WOT_NAME = "plugins.WoT.WoT";
	public static final String WOT_CONTEXT = "Freetalk";
	public static final String DATABASE_FILENAME = "freetalk-testing-9.db4o";
	public static final int DATABASE_FORMAT_VERSION = -91;

	/* References from the node */
	
	private ClassLoader mClassLoader;
	
	private PluginRespirator mPluginRespirator; /* TODO: remove references in other classes so we can make this private */

	private LANGUAGE mLanguage;
	
	private THEME mTheme;

	
	/* The plugin's own references */
	
	private ExtObjectContainer db;
	
	private Config mConfig;
	
	private WoTIdentityManager mIdentityManager;
	
	private WoTMessageManager mMessageManager;
	
	private WoTMessageFetcher mMessageFetcher;
	
	private WoTMessageInserter mMessageInserter;
	
	private WoTMessageListFetcher mMessageListFetcher;
	
	private WoTMessageListInserter mMessageListInserter;
	
	private PersistentTaskManager mTaskManager;

	private WebInterface mWebInterface;
	
	private FCPInterface mFCPInterface;
	
	private FreetalkNNTPServer mNNTPServer;


	public void runPlugin(PluginRespirator myPR) {
		Logger.debug(this, "Plugin starting up...");

		mPluginRespirator = myPR;

		Logger.debug(this, "Opening database...");
		db = openDatabase(DATABASE_FILENAME);
		Logger.debug(this, "Database opened.");
		
		mConfig = Config.loadOrCreate(this, db);
		if(mConfig.getInt(Config.DATABASE_FORMAT_VERSION) > Freetalk.DATABASE_FORMAT_VERSION)
			throw new RuntimeException("The WoT plugin's database format is newer than the WoT plugin which is being used.");
		
		upgradeDatabase();
		
//		/* FIXME: Debug code, remove when not needed anymore */
//		Logger.debug(this, "Wiping database...");
//		ObjectSet<Object> result = db.queryByExample(new Object());
//		for (Object o : result)
//			db.delete(o);
//		db.commit(); Logger.debug(this, "COMMITED.");
//		Logger.debug(this, "Database wiped.");
		
		Executor executor = mPluginRespirator.getNode().executor;
		
		Logger.debug(this, "Creating Web interface...");
		mWebInterface = new WebInterface(this);
		
		Logger.debug(this, "Creating identity manager...");
		mIdentityManager = new WoTIdentityManager(db, this);
		
		Logger.debug(this, "Creating message manager...");
		mMessageManager = new WoTMessageManager(db, mIdentityManager, this, mPluginRespirator);
		
		Logger.debug(this, "Creating task manager...");
		mTaskManager = new PersistentTaskManager(db, this);
		
		executor.execute(mIdentityManager, "FT Identity Manager");
		executor.execute(mMessageManager, "FT Message Manager");
		executor.execute(mTaskManager, "FT PersistentTaskManager");
		
		Logger.debug(this, "Creating message fetcher...");
		mMessageFetcher = new WoTMessageFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "FT Message Fetcher", mIdentityManager, mMessageManager);
		mMessageFetcher.start();
		
		Logger.debug(this, "Creating message inserter...");
		mMessageInserter = new WoTMessageInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "FT Message Inserter", mIdentityManager, mMessageManager);
		mMessageInserter.start();
		
		Logger.debug(this, "Creating message list fetcher...");
		mMessageListFetcher = new WoTMessageListFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "FT MessageList Fetcher", mIdentityManager, mMessageManager);
		mMessageListFetcher.start();
		
		Logger.debug(this, "Creating message list inserter...");
		mMessageListInserter = new WoTMessageListInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "FT MessageList Inserter", mIdentityManager, mMessageManager);
		mMessageListInserter.start();

		Logger.debug(this, "Creating FCP interface...");
		mFCPInterface = new FCPInterface(this);
		
		// FIXME: Fix this: https://bugs.freenetproject.org/view.php?id=2977
		//Logger.debug(this, "Starting NNTP server...");
		//mNNTPServer = new FreetalkNNTPServer(mPluginRespirator.getNode(), this, 1199, "127.0.0.1", "127.0.0.1");
		//mNNTPServer = new FreetalkNNTPServer(mPluginRespirator.getNode(), this, 1199, "0.0.0.0", "*");
		
		Logger.debug(this, "Plugin loaded.");
	}
	
	private ExtObjectContainer openDatabase(String filename) {
		Configuration dbCfg = Db4o.newConfiguration();
		dbCfg.reflectWith(new JdkReflector(mClassLoader));
		dbCfg.exceptionsOnNotStorable(true);
		dbCfg.activationDepth(5); /* FIXME: Figure out a reasonable value */
		
		// TODO: Replace all these loops with one single loop which uses reflection!
		
		for(String f : Message.getIndexedFields())
			dbCfg.objectClass(Message.class).objectField(f).indexed(true);
		
		for(String f : MessageList.getIndexedFields())
			dbCfg.objectClass(MessageList.class).objectField(f).indexed(true);
		
		for(String f: MessageList.MessageReference.getIndexedFields())
			dbCfg.objectClass(MessageList.MessageReference.class).objectField(f).indexed(true);
		
		dbCfg.objectClass(FetchFailedMarker.class).persistStaticFieldValues(); /* Make it store enums */
		
		for(String f : FetchFailedMarker.getIndexedFields())
			dbCfg.objectClass(FetchFailedMarker.class).objectField(f).indexed(true);
		
		for(String f : MessageFetchFailedMarker.getIndexedFields())
			dbCfg.objectClass(MessageFetchFailedMarker.class).objectField(f).indexed(true);
		
		for(String f : MessageListFetchFailedMarker.getIndexedFields())
			dbCfg.objectClass(MessageListFetchFailedMarker.class).objectField(f).indexed(true);
			
		
		for(String f : Board.getIndexedFields()) {
			dbCfg.objectClass(Board.class).objectField(f).indexed(true);
		}
		
		for(String f : SubscribedBoard.getMessageReferenceIndexedFields()) {
			dbCfg.objectClass(SubscribedBoard.BoardReplyLink.class).objectField(f).indexed(true);
		}
		
		for(String f : SubscribedBoard.getBoardReplyLinkIndexedFields()) {
			dbCfg.objectClass(SubscribedBoard.BoardReplyLink.class).objectField(f).indexed(true);
		}		
		
		for(String f : SubscribedBoard.getBoardThreadLinkIndexedFields()) {
			dbCfg.objectClass(SubscribedBoard.BoardReplyLink.class).objectField(f).indexed(true);
		}

		for(String f :  WoTIdentity.getIndexedFields()) {
			dbCfg.objectClass(WoTIdentity.class).objectField(f).indexed(true);
		}
		
		for(String f :  WoTOwnIdentity.getIndexedFields()) {
			dbCfg.objectClass(WoTOwnIdentity.class).objectField(f).indexed(true);
		}
		
		return Db4o.openFile(dbCfg, filename).ext();
	}

	private void upgradeDatabase() {
		int oldVersion = mConfig.getInt(Config.DATABASE_FORMAT_VERSION);
		
		if(oldVersion == Freetalk.DATABASE_FORMAT_VERSION)
			return;
		
		throw new RuntimeException("Your database is too outdated to be upgraded automatically, please create a new one by deleting " 
				+ DATABASE_FILENAME + ". Contact the developers if you really need your old data.");
	}

	private void closeDatabase() {
		synchronized(db.lock()) {
			try {
				db.rollback();
				db.close();
				db = null;
			}
			catch(RuntimeException e) {
				Logger.error(this, "Error while closing database", e);
			}
		}
	}
	
	public synchronized void handleWotConnected() {
		Logger.debug(this, "Connected to WoT plugin.");
		wotConnected = true;
	}
	
	private boolean wotConnected;
	
	public synchronized void handleWotDisconnected() {
		Logger.debug(this, "Disconnected from WoT plugin");
		wotConnected = false;
	}
	
	public synchronized boolean wotConnected() {
		return wotConnected;
	}
	
	public boolean wotOutdated() {
		return false;
	}

	public void terminate() {
		Logger.debug(this, "Terminating Freetalk ...");
		
		/* We use single try/catch blocks so that failure of termination of one service does not prevent termination of the others */
		try {
			mWebInterface.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}
		
		try {
			mNNTPServer.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}
		
        try {
            mFCPInterface.terminate();
        }
        catch(Exception e) {
            Logger.error(this, "Error during termination.", e);
        }
		
		/* WebInterface is stateless and does not need to be terminated */
		
        try {
        	mTaskManager.terminate();
        }
        catch(Exception e) {
        	Logger.error(this, "Error during termination.", e);	
        }
        
		try {
			mMessageListInserter.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}
		
		try {
			mMessageListFetcher.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}
		
		try {
			mMessageInserter.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}
		
		try {
			mMessageFetcher.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}
		
		try {
			mMessageManager.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}
		
		try {
			mIdentityManager.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}

		try {
			closeDatabase();
		} catch(Exception e) {
			Logger.error(this, "Error while closing database.", e);
		}
		
		Logger.debug(this, "Freetalk plugin terminated.");
	}
	
	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		mFCPInterface.handle(replysender, params, data, accesstype);
	}
	
	public PluginRespirator getPluginRespirator() {
		return mPluginRespirator;
	}
	
	public Config getConfig() {
		return mConfig;
	}
	
	public IdentityManager getIdentityManager() {
		return mIdentityManager;
	}	
	
	public MessageManager getMessageManager() {
		return mMessageManager;
	}
	
	public PersistentTaskManager getTaskManager() {
		return mTaskManager;
	}
	
	public String getVersion() {
		return Version.getMarketingVersion();
	}
	
	public long getRealVersion() {
		return Version.getRealVersion();
	}

	public String getString(String key) {
		// Logger.error(this, "Request translation for "+key);
		return key;
	}

	/**
	 * Called by the node during the loading of the plugin. The <code>ClassLoader</code> which was used by the node is passed to db4o
	 * by Freetalk. Db4o needs to know the <code>ClassLoader</code> which was used to create the classes of the objects it is supposed to store.
	 */
	public void setClassLoader(ClassLoader myClassLoader) {
		mClassLoader = myClassLoader;
	}
	
	public void setLanguage(LANGUAGE newLanguage) {
		mLanguage = newLanguage;
		Logger.debug(this, "Set LANGUAGE to: " + mLanguage.isoCode);
	}

	public void setTheme(THEME newTheme) {
		mTheme = newTheme;
		Logger.error(this, "Set THEME to: " + mTheme.code);
	}

}
