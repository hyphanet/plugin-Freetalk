/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Map.Entry;

import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessageFetcher;
import plugins.Freetalk.WoT.WoTMessageInserter;
import plugins.Freetalk.WoT.WoTMessageListFetcher;
import plugins.Freetalk.WoT.WoTMessageListInserter;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.tasks.PersistentTaskManager;
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
public class Freetalk implements FredPlugin, FredPluginFCP, FredPluginL10n, FredPluginBaseL10n, FredPluginThemed, FredPluginThreadless,
	FredPluginVersioned, FredPluginRealVersioned {

	/* Constants */
	
	/** If set to true, all thread periods will be set to very low values, resulting in very fast message downloading. */
	public static final boolean FAST_DEBUG_MODE = false; // FIXME: Set to false before release!
	
	public static final String PLUGIN_URI = "/Freetalk";
	public static final String PLUGIN_TITLE = "Freetalk-testing"; /* FIXME REDFLAG: Has to be changed to Freetalk before release! Otherwise messages will disappear */
	public static final String WOT_NAME = "plugins.WoT.WoT";
	public static final String WOT_CONTEXT = "Freetalk";
	public static final String DATABASE_FILENAME = "freetalk-testing-13.db4o";
	public static final int DATABASE_FORMAT_VERSION = -87;

	/* References from the node */
	
	private PluginRespirator mPluginRespirator;

	private static PluginL10n l10n;
	
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
		
		Logger.debug(this, "Creating message fetcher...");
		mMessageFetcher = new WoTMessageFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageFetcher", mIdentityManager, mMessageManager);
		mMessageFetcher.start();
		
		Logger.debug(this, "Creating message inserter...");
		mMessageInserter = new WoTMessageInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageInserter", mIdentityManager, mMessageManager);
		mMessageInserter.start();
		
		Logger.debug(this, "Creating message list fetcher...");
		mMessageListFetcher = new WoTMessageListFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageListFetcher", mIdentityManager, mMessageManager);
		mMessageListFetcher.start();
		
		Logger.debug(this, "Creating message list inserter...");
		mMessageListInserter = new WoTMessageListInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageListInserter", mIdentityManager, mMessageManager);
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
	
	private ExtObjectContainer openDatabase(String filename) {
		Configuration dbCfg = Db4o.newConfiguration();
		dbCfg.reflectWith(new JdkReflector(getPluginClassLoader()));
		dbCfg.exceptionsOnNotStorable(true);
		dbCfg.activationDepth(5); /* FIXME: Figure out a reasonable value */
		
        // TURN OFF SHUTDOWN HOOK.
        // The shutdown hook does auto-commit. We do NOT want auto-commit: if a
        // transaction hasn't commit()ed, it's not safe to commit it.
        dbCfg.automaticShutDown(false);
        
        for(Entry<Class<? extends Persistent>, String[]> entry : Persistent.getIndexedFields().entrySet()) {
        	Class<? extends Persistent> currentClass = entry.getKey();
        	
        	for(String indexedField : entry.getValue()) { // FIXME: Test whether this actually works!
        		dbCfg.objectClass(currentClass).objectField(indexedField).indexed(true);
        	}
        }
		
		
		return Db4o.openFile(dbCfg, filename).ext();
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
		return Version.getMarketingVersion();
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
