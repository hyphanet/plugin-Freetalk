/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.lang.reflect.Field;

import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessage;
import plugins.Freetalk.WoT.WoTMessageFetcher;
import plugins.Freetalk.WoT.WoTMessageInserter;
import plugins.Freetalk.WoT.WoTMessageList;
import plugins.Freetalk.WoT.WoTMessageListFetcher;
import plugins.Freetalk.WoT.WoTMessageListInserter;
import plugins.Freetalk.WoT.WoTMessageListXML;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTMessageRating;
import plugins.Freetalk.WoT.WoTMessageURI;
import plugins.Freetalk.WoT.WoTMessageXML;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTOwnMessage;
import plugins.Freetalk.WoT.WoTOwnMessageList;
import plugins.Freetalk.tasks.OwnMessageTask;
import plugins.Freetalk.tasks.PersistentTask;
import plugins.Freetalk.tasks.PersistentTaskManager;
import plugins.Freetalk.tasks.WoT.IntroduceIdentityTask;
import plugins.Freetalk.ui.FCP.FCPInterface;
import plugins.Freetalk.ui.NNTP.FreetalkNNTPServer;
import plugins.Freetalk.ui.web.WebInterface;

import com.db4o.Db4o;
import com.db4o.config.Configuration;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.reflect.jdk.JdkReflector;

import freenet.clients.http.PageMaker.THEME;
import freenet.l10n.BaseL10n;
import freenet.l10n.PluginL10n;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginBaseL10n;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThemed;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

/**
 * @author xor@freenetproject.org
 * @author saces
 * @author bback
 */
public final class Freetalk implements FredPlugin, FredPluginFCP, FredPluginL10n, FredPluginBaseL10n, FredPluginThemed, FredPluginThreadless,
	FredPluginVersioned, FredPluginRealVersioned {

	/* Constants */
	
	/** If set to true, all thread periods will be set to very low values, resulting in very fast message downloading. */
	public static final boolean FAST_DEBUG_MODE = false; // FIXME: Set to false before release!
	
	public static final String PLUGIN_URI = "/Freetalk";
	public static final String PLUGIN_TITLE = "Freetalk-testing"; /* FIXME REDFLAG: Has to be changed to Freetalk before release! Otherwise messages will disappear */
	public static final String WOT_NAME = "plugins.WoT.WoT";
	public static final String WOT_CONTEXT = "Freetalk";
	public static final String DATABASE_FILENAME = "freetalk-testing-14.db4o";
	public static final int DATABASE_FORMAT_VERSION = -86;

	/* References from the node */
	
	private PluginRespirator mPluginRespirator;

	private static PluginL10n l10n;
	
	private THEME mTheme;

	
	/* The plugin's own references */
	
	private ExtObjectContainer db;
	
	private Config mConfig;
	
	private WoTIdentityManager mIdentityManager;
	
	private WoTMessageManager mMessageManager;
	
	private WoTMessageXML mMessageXML;
	
	private WoTMessageListXML mMessageListXML;
	
	private WoTMessageFetcher mMessageFetcher;
	
	private WoTMessageInserter mMessageInserter;
	
	private WoTMessageListFetcher mMessageListFetcher;
	
	private WoTMessageListInserter mMessageListInserter;
	
	private PersistentTaskManager mTaskManager;

	private WebInterface mWebInterface;
	
	private FCPInterface mFCPInterface;
	
	private FreetalkNNTPServer mNNTPServer;

	/**
	 * Default constructor, used by the node, do not remove it.
	 */
	public Freetalk() {
		
	}

	public void runPlugin(PluginRespirator myPR) {
		try {
		Logger.debug(this, "Plugin starting up...");

		mPluginRespirator = myPR;

		Logger.debug(this, "Opening database...");
		db = openDatabase(DATABASE_FILENAME);
		Logger.debug(this, "Database opened.");
		
		mConfig = Config.loadOrCreate(this, db);
		if(mConfig.getInt(Config.DATABASE_FORMAT_VERSION) > Freetalk.DATABASE_FORMAT_VERSION)
			throw new RuntimeException("The WoT plugin's database format is newer than the WoT plugin which is being used.");
		
		upgradeDatabase();
		
		Logger.debug(this, "Creating Web interface...");
		mWebInterface = new WebInterface(this);
		
		Logger.debug(this, "Creating identity manager...");
		mIdentityManager = new WoTIdentityManager(this, mPluginRespirator.getNode().executor);
		
		Logger.debug(this, "Creating message manager...");
		mMessageManager = new WoTMessageManager(db, mIdentityManager, this, mPluginRespirator);
		
		Logger.debug(this, "Creating task manager...");
		mTaskManager = new PersistentTaskManager(db, this);
		
		mIdentityManager.start();
		mMessageManager.start();
		mTaskManager.start();
		
		Logger.debug(this, "Creating message XML...");
		mMessageXML = new WoTMessageXML();
		
		Logger.debug(this, "Creating message list XML...");
		mMessageListXML = new WoTMessageListXML();
		
		Logger.debug(this, "Creating message fetcher...");
		mMessageFetcher = new WoTMessageFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageFetcher",
				mIdentityManager, mMessageManager, mMessageXML);
		mMessageFetcher.start();
		
		Logger.debug(this, "Creating message inserter...");
		mMessageInserter = new WoTMessageInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageInserter",
				mIdentityManager, mMessageManager, mMessageXML);
		mMessageInserter.start();
		
		Logger.debug(this, "Creating message list fetcher...");
		mMessageListFetcher = new WoTMessageListFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageListFetcher",
				mIdentityManager, mMessageManager, mMessageListXML);
		mMessageListFetcher.start();
		
		Logger.debug(this, "Creating message list inserter...");
		mMessageListInserter = new WoTMessageListInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageListInserter",
				mIdentityManager, mMessageManager, mMessageListXML);
		mMessageListInserter.start();

		Logger.debug(this, "Creating FCP interface...");
		mFCPInterface = new FCPInterface(this);

		if (mConfig.getBoolean(Config.NNTP_SERVER_ENABLED)) {
    		Logger.debug(this, "Creating NNTP server...");
    		String bindTo = mConfig.getString(Config.NNTP_SERVER_BINDTO);
			if (bindTo == null) {
				bindTo = "127.0.0.1";
			}
			String allowedHosts = mConfig.getString(Config.NNTP_SERVER_ALLOWED_HOSTS);
			if (allowedHosts == null) {
				allowedHosts = "127.0.0.1";
			}
			mNNTPServer = new FreetalkNNTPServer(this, 1199, bindTo, allowedHosts);
			mNNTPServer.start();
		} else {
            Logger.debug(this, "NNTP server disabled by user...");
		    mNNTPServer = null;
		}

		Logger.debug(this, "Plugin loaded.");
		}
		catch(RuntimeException e) {
			Logger.error(this, "Startup failed!", e);
			terminate();
			throw e;
		}
	}
	
	@SuppressWarnings("unchecked")
	private ExtObjectContainer openDatabase(String filename) {
		Configuration cfg = Db4o.newConfiguration();
		
		// Required config options:
		
		cfg.reflectWith(new JdkReflector(getPluginClassLoader())); // Needed because the node uses it's own classloader for plugins
		cfg.exceptionsOnNotStorable(true); // Notify us if we tried to store a class which db4o won't store
		cfg.activationDepth(5); // TODO: Decrease to 1 after we have explicit activation everywhere.
        cfg.automaticShutDown(false); // The shutdown hook does auto-commit() but we want to rollback(), we MUST NOT commit half-finished transactions
        
        // Performance config options:
        cfg.callbacks(false); // We don't use callbacks yet. TODO: Investigate whether we might want to use them
        cfg.classActivationDepthConfigurable(false);
        cfg.reserveStorageSpace(1 * 1024 * 1024);
        cfg.databaseGrowthSize(1 * 1024 * 1024);
      
        // Registration of indices (also performance)
        
        final Class<? extends Persistent>[] persistentClasses = new Class[] {
        	Board.class,
        	Board.BoardMessageLink.class,
        	Config.class,
        	FetchFailedMarker.class,
        	Message.class,
        	MessageList.class,
        	MessageList.MessageReference.class,
        	MessageList.MessageFetchFailedMarker.class,
        	MessageList.MessageListFetchFailedMarker.class,
        	MessageRating.class,
        	MessageURI.class,
        	OwnMessage.class,
        	OwnMessageList.class,
        	SubscribedBoard.class,
        	SubscribedBoard.MessageReference.class,
        	SubscribedBoard.BoardThreadLink.class,
        	SubscribedBoard.BoardReplyLink.class,
        	PersistentTask.class,
        	OwnMessageTask.class,
        	IntroduceIdentityTask.class,
        	WoTIdentity.class,
        	WoTMessage.class,
        	WoTMessageList.class,
        	WoTMessageRating.class,
        	WoTMessageURI.class,
        	WoTOwnIdentity.class,
        	WoTOwnMessage.class,
        	WoTOwnMessageList.class
        };
        
        for(Class clazz : persistentClasses) {
        	boolean classHasIndex = clazz.getAnnotation(Persistent.Indexed.class) != null;
        	
        	Logger.debug(this, "Peristent class: " + clazz.getCanonicalName() + "; hasIndex==" + classHasIndex);
        	
        	// TODO: Make very sure that it has no negative side effects if we disable class indices for some classes
        	// Maybe benchmark in comparison to a database which has class indices enabled for ALL classes.
        	//cfg.objectClass(clazz).indexed(classHasIndex);
        	cfg.objectClass(clazz).indexed(true);
   
        	for(Field field : clazz.getDeclaredFields()) {
        		if(field.getAnnotation(Persistent.Indexed.class) != null) {
        			Logger.debug(this, "Registering indexed field " + clazz.getCanonicalName() + '.' + field.getName());
        			cfg.objectClass(clazz).objectField(field.getName()).indexed(true);
        		}
        	}
        }
        
        // TODO: We should check whether db4o inherits the indexed attribute to child classes, for example for this one:
        // Unforunately, db4o does not provide any way to query the indexed() property of fields, you can only set it
        // We might figure out whether inheritance works by writing a benchmark.
		
		return Db4o.openFile(cfg, filename).ext();
	}
	
	/**
	 * Concstructor for being used by unit tests only.
	 */
	public Freetalk(ExtObjectContainer myDB) {
		db = myDB;
		mIdentityManager = new WoTIdentityManager(this);
		mMessageManager = new WoTMessageManager(this);
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
				System.gc();
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
		
		if (mNNTPServer != null) {
    		try {
		        mNNTPServer.terminate();
    		}
    		catch(Exception e) {
    			Logger.error(this, "Error during termination.", e);
    		}
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
	
	protected ExtObjectContainer getDatabase() {
		return db;
	}
	
	public WoTIdentityManager getIdentityManager() {
		return mIdentityManager;
	}	
	
	public WoTMessageManager getMessageManager() {
		return mMessageManager;
	}
	
	public PersistentTaskManager getTaskManager() {
		return mTaskManager;
	}

	public String getVersion() {
		return Version.longVersionString;
	}
	
	public long getRealVersion() {
		return Version.getRealVersion();
	}

    /**
     * This code is only used by FredPluginL10n...
     * 
     * @param arg0
     * @return
     */
    public String getString(String arg0) {
        return getBaseL10n().getString(arg0);
    }

	
    /**
     * This code is only called during startup or when the user
     * selects another language in the UI.
     * @param newLanguage Language to use.
     */
    public void setLanguage(final BaseL10n.LANGUAGE newLanguage) {
        Freetalk.l10n = new PluginL10n(this, newLanguage);
        Logger.debug(this, "Set LANGUAGE to: " + newLanguage.isoCode);
    }

	public void setTheme(THEME newTheme) {
		mTheme = newTheme;
		Logger.error(this, "Set THEME to: " + mTheme.code);
	}

    /**
     * This is where our L10n files are stored.
     * @return Path of our L10n files.
     */
    public String getL10nFilesBasePath() {
        return "plugins/Freetalk/l10n/";
    }

    /**
     * This is the mask of our L10n files : lang_en.l10n, lang_de.10n, ...
     * @return Mask of the L10n files.
     */
    public String getL10nFilesMask() {
        return "lang_${lang}.l10n";
    }

    /**
     * Override L10n files are stored on the disk, their names should be explicit
     * we put here the plugin name, and the "override" indication. Plugin L10n
     * override is not implemented in the node yet.
     * @return Mask of the override L10n files.
     */
    public String getL10nOverrideFilesMask() {
        return "Freetalk_lang_${lang}.override.l10n";
    }

    /**
     * Get the ClassLoader of this plugin. This is necessary when getting
     * resources inside the plugin's Jar, for example L10n files.
     * @return
     */
    public ClassLoader getPluginClassLoader() {
        return Freetalk.class.getClassLoader();
    }
    
    /**
     * BaseL10n object can be accessed statically to get L10n data from anywhere.
     *
     * @return L10n object.
     */
    public BaseL10n getBaseL10n() {
        return Freetalk.l10n.getBase();
    }
}
