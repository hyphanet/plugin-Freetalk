/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;

import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessage;
import plugins.Freetalk.WoT.WoTMessageFetcher;
import plugins.Freetalk.WoT.WoTMessageInserter;
import plugins.Freetalk.WoT.WoTMessageList;
import plugins.Freetalk.WoT.WoTMessageListInserter;
import plugins.Freetalk.WoT.WoTMessageListXML;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTMessageRating;
import plugins.Freetalk.WoT.WoTMessageURI;
import plugins.Freetalk.WoT.WoTMessageXML;
import plugins.Freetalk.WoT.WoTNewMessageListFetcher;
import plugins.Freetalk.WoT.WoTOldMessageListFetcher;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTOwnMessage;
import plugins.Freetalk.WoT.WoTOwnMessageList;
import plugins.Freetalk.tasks.NewBoardTask;
import plugins.Freetalk.tasks.OwnMessageTask;
import plugins.Freetalk.tasks.PersistentTask;
import plugins.Freetalk.tasks.PersistentTaskManager;
import plugins.Freetalk.tasks.SubscribeToAllBoardsTask;
import plugins.Freetalk.tasks.WoT.IntroduceIdentityTask;
import plugins.Freetalk.ui.FCP.FCPInterface;
import plugins.Freetalk.ui.NNTP.FreetalkNNTPServer;
import plugins.Freetalk.ui.web.WebInterface;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.defragment.Defragment;
import com.db4o.defragment.DefragmentConfig;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;
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
import freenet.support.SizeUtil;
import freenet.support.api.Bucket;
import freenet.support.io.FileUtil;

/**
 * @author xor@freenetproject.org
 * @author saces
 * @author bback
 */
public final class Freetalk implements FredPlugin, FredPluginFCP, FredPluginL10n, FredPluginBaseL10n, FredPluginThemed, FredPluginThreadless,
	FredPluginVersioned, FredPluginRealVersioned {

	/* Constants */
	
	/**
	 * If set to true, all thread periods will be set to very low values, resulting in very fast message downloading.
	 * It is volatile to prevent compiler warnings about unreachable code.
	 */
	public static volatile boolean FAST_DEBUG_MODE = false;
	
	public static final String PLUGIN_URI = "/Freetalk";
	public static final String PLUGIN_TITLE = "Freetalk";
	public static final String WEB_OF_TRUST_NAME = "WebOfTrust";
	public static final String WOT_PLUGIN_NAME = "plugins.WebOfTrust.WebOfTrust";
	public static final String WOT_PLUGIN_URI = "/WebOfTrust";
	public static final String WOT_CONTEXT = PLUGIN_TITLE;
	public static final String DATABASE_FILENAME = PLUGIN_TITLE + ".db4o";
	public static final int DATABASE_FORMAT_VERSION = 1;

	/* References from the node */
	
	private PluginRespirator mPluginRespirator;

	private static PluginL10n l10n;
	
	private THEME mTheme;

	
	/* The plugin's own references */
	
	private ExtObjectContainer db;
	
	private Configuration mConfig;
	
	private WoTIdentityManager mIdentityManager;
	
	private WoTMessageManager mMessageManager;
	
	private WoTMessageXML mMessageXML;
	
	private WoTMessageListXML mMessageListXML;
	
	private WoTMessageFetcher mMessageFetcher;
	
	private WoTMessageInserter mMessageInserter;
	
	private WoTOldMessageListFetcher mOldMessageListFetcher;
	
	private WoTNewMessageListFetcher mNewMessageListFetcher;
	
	private WoTMessageListInserter mMessageListInserter;
	
	private PersistentTaskManager mTaskManager;

	private WebInterface mWebInterface;
	
	private FCPInterface mFCPInterface;
	
	private FreetalkNNTPServer mNNTPServer;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(Freetalk.class);
	}
	
	
	/**
	 * Constructor for the node.
	 */
	public Freetalk() {
		if(logDEBUG) Logger.debug(this, "Freetalk plugin constructed.");
	}

	/**
	 * Constructor for unit tests.
	 */
	public Freetalk(String databaseFilename) {
		db = openDatabase(new File(databaseFilename));
		
		mConfig = Configuration.loadOrCreate(this, db);
		if(mConfig.getDatabaseFormatVersion() != Freetalk.DATABASE_FORMAT_VERSION)
			throw new RuntimeException("The Freetalk plugin's database format is newer than the Freetalk plugin which is being used.");
		
		mIdentityManager = new WoTIdentityManager(this);
		mMessageManager = new WoTMessageManager(this);
		mTaskManager = new PersistentTaskManager(this, db);
	}

	@Override public void runPlugin(PluginRespirator myPR) {
		try {
		Logger.normal(this, "Plugin starting up...");

		mPluginRespirator = myPR;

		if(logDEBUG) Logger.debug(this, "Opening database...");
		db = openDatabase(new File(getUserDataDirectory(), DATABASE_FILENAME));
		if(logDEBUG) Logger.debug(this, "Database opened.");
		
		mConfig = Configuration.loadOrCreate(this, db);
		if(mConfig.getDatabaseFormatVersion() > Freetalk.DATABASE_FORMAT_VERSION)
			throw new RuntimeException("The Freetalk plugin's database format is newer than the Freetalk plugin which is being used.");
		
		// Create & start the core classes
		
		if(logDEBUG) Logger.debug(this, "Creating identity manager...");
		mIdentityManager = new WoTIdentityManager(this, mPluginRespirator.getNode().executor);
		
		if(logDEBUG) Logger.debug(this, "Creating message manager...");
		mMessageManager = new WoTMessageManager(db, mIdentityManager, this, mPluginRespirator);
		
		if(logDEBUG) Logger.debug(this, "Creating task manager...");
		mTaskManager = new PersistentTaskManager(this, db);
		
		upgradeDatabase();
		
		// We only do this if debug logging is enabled since the integrity verification cannot repair anything anyway,
		// if the user does not read his logs there is no need to check the integrity.
		// TODO: Do this once every few startups and notify the user in the web ui if errors are found.
		if(logDEBUG)
			databaseIntegrityTest(); // Some tests need the Identity-/Message-/TaskManager so we call this after creating them.
		
		if(logDEBUG) Logger.debug(this, "Creating message XML...");
		mMessageXML = new WoTMessageXML();
		
		if(logDEBUG) Logger.debug(this, "Creating message list XML...");
		mMessageListXML = new WoTMessageListXML();
		
		if(logDEBUG) Logger.debug(this, "Creating old-messagelist fetcher...");
		mOldMessageListFetcher = new WoTOldMessageListFetcher(this, "Freetalk WoTOldMessageListFetcher", mMessageListXML);
		
		if(logDEBUG) Logger.debug(this, "Creating new-messagelist fetcher...");
		mNewMessageListFetcher = new WoTNewMessageListFetcher(this, "Freetalk WoTNewMessageListFetcher", mMessageListXML, db);
		mNewMessageListFetcher.start();
		
		if(logDEBUG) Logger.debug(this, "Creating message fetcher...");
		mMessageFetcher = new WoTMessageFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageFetcher",
				this, mIdentityManager, mMessageManager, mMessageXML);
		mMessageFetcher.start();
		
		if(logDEBUG) Logger.debug(this, "Creating message inserter...");
		mMessageInserter = new WoTMessageInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageInserter",
				mIdentityManager, mMessageManager, mMessageXML);
		mMessageInserter.start();

		if(logDEBUG) Logger.debug(this, "Creating message list inserter...");
		mMessageListInserter = new WoTMessageListInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "Freetalk WoTMessageListInserter",
				mIdentityManager, mMessageManager, mMessageListXML);
		mMessageListInserter.start();
		
		// They need each users so they must be started after they exist all three.
		// Further, we must start them after starting the message list fetches: Otherwise the message manager etc. might take locks for ages, effectively
		// preventing this startup function from finishing. So we just start them after all core classes are running and before starting the UI.
		mIdentityManager.start();
		mMessageManager.start();
		mTaskManager.start();
		
		// This must be started after the identity manager because it will need the identity manager to be connected to WoT.
		mOldMessageListFetcher.start();
		
		// Create & start the UI
		
		if(logDEBUG) Logger.debug(this, "Creating Web interface...");
		mWebInterface = new WebInterface(this);

		if(logDEBUG) Logger.debug(this, "Creating FCP interface...");
		mFCPInterface = new FCPInterface(this);

		if (mConfig.getBoolean(Configuration.NNTP_SERVER_ENABLED)) {
    		if(logDEBUG) Logger.debug(this, "Creating NNTP server...");
    		String bindTo = mConfig.getString(Configuration.NNTP_SERVER_BINDTO);
			if (bindTo == null) {
				bindTo = "127.0.0.1";
			}
			String allowedHosts = mConfig.getString(Configuration.NNTP_SERVER_ALLOWED_HOSTS);
			if (allowedHosts == null) {
				allowedHosts = "127.0.0.1";
			}
			mNNTPServer = new FreetalkNNTPServer(this, 1199, bindTo, allowedHosts);
			mNNTPServer.start();
		} else {
            if(logDEBUG) Logger.debug(this, "NNTP server disabled by user...");
		    mNNTPServer = null;
		}

		Logger.normal(this, "Freetalk starting up completed.");
		}
		catch(RuntimeException e) {
			Logger.error(this, "Startup failed!", e);
			terminate();
			throw e;
		}
	}
	
	private File getUserDataDirectory() {
        final File freetalkDirectory = new File(mPluginRespirator.getNode().getUserDir(), PLUGIN_TITLE);
        
        if(!freetalkDirectory.exists() && !freetalkDirectory.mkdir())
        	throw new RuntimeException("Unable to create directory " + freetalkDirectory);
        
        return freetalkDirectory;
	}
	
	@SuppressWarnings("unchecked")
	private com.db4o.config.Configuration getNewDatabaseConfiguration() {
		final com.db4o.config.Configuration cfg = Db4o.newConfiguration();
		
		// Required config options:
		
		cfg.reflectWith(new JdkReflector(getPluginClassLoader())); // Needed because the node uses it's own classloader for plugins
		cfg.exceptionsOnNotStorable(true); // Notify us if we tried to store a class which db4o won't store
		cfg.activationDepth(1); // TODO: Optimization: Check whether 0 is better. All database code was written to also work with 0.
		cfg.updateDepth(1); // This must not be changed: We only activate(this, 1) before store(this).
		Logger.normal(this, "Default activation depth: " + cfg.activationDepth());
        cfg.automaticShutDown(false); // The shutdown hook does auto-commit() but we want to rollback(), we MUST NOT commit half-finished transactions
        
        // Performance config options:
        cfg.callbacks(false); // We don't use callbacks yet. TODO: Investigate whether we might want to use them
        cfg.classActivationDepthConfigurable(false);

        // Registration of indices (also performance)
        
        // Class Persistent canot apply its annotations to itself, we need to configure the index for it manually.
        // We need an index on Persistent because we query all Persistent-objects for startup database validation.
        cfg.objectClass(Persistent.class).indexed(true);
        
        final Class<? extends Persistent>[] persistentClasses = new Class[] {
        	Persistent.class,
        	Board.class,
        	Board.DownloadedMessageLink.class,
        	Configuration.class,
        	FetchFailedMarker.class,
        	IdentityStatistics.class,
        	Message.class,
        	Message.Attachment.class,
        	MessageList.class,
        	MessageList.MessageReference.class,
        	MessageList.MessageFetchFailedMarker.class,
        	MessageList.MessageListFetchFailedMarker.class,
        	MessageRating.class,
        	MessageURI.class,
        	OwnMessage.class,
        	OwnMessageList.class,
        	OwnMessageList.OwnMessageReference.class,
        	SubscribedBoard.class,
        	SubscribedBoard.UnwantedMessageLink.class,
        	SubscribedBoard.BoardMessageLink.class,
        	SubscribedBoard.BoardThreadLink.class,
        	SubscribedBoard.BoardReplyLink.class,
        	PersistentTask.class,
        	OwnMessageTask.class,
        	IntroduceIdentityTask.class,
        	NewBoardTask.class,
        	SubscribeToAllBoardsTask.class,
        	WoTIdentity.class,
        	WoTMessage.class,
        	WoTMessageList.class,
        	WoTMessageRating.class,
        	WoTMessageURI.class,
        	WoTNewMessageListFetcher.FetcherCommand.class,
        	WoTNewMessageListFetcher.StartFetchCommand.class,
        	WoTNewMessageListFetcher.AbortFetchCommand.class,
        	WoTNewMessageListFetcher.UpdateEditionHintCommand.class,
        	WoTOwnIdentity.class,
        	WoTOwnMessage.class,
        	WoTOwnMessageList.class
        };
        
        for(Class<? extends Persistent> clazz : persistentClasses) {
        	boolean classHasIndex = clazz.getAnnotation(Persistent.IndexedClass.class) != null;
        	
        	// TODO: We enable class indexes for all classes to make sure nothing breaks because it is the db4o default, check whether enabling them only
        	// for the classes where we need them does not cause any harm.
        	classHasIndex = true;
        	
        	if(logDEBUG) Logger.debug(this, "Persistent class: " + clazz.getCanonicalName() + "; hasIndex==" + classHasIndex);
        	
        	// TODO: Make very sure that it has no negative side effects if we disable class indices for some classes
        	// Maybe benchmark in comparison to a database which has class indices enabled for ALL classes.
        	cfg.objectClass(clazz).indexed(classHasIndex);
   
        	// Check the class' fields for @IndexedField annotations
        	for(Field field : clazz.getDeclaredFields()) {
        		if(field.getAnnotation(Persistent.IndexedField.class) != null) {
        			if(logDEBUG) Logger.debug(this, "Registering indexed field " + clazz.getCanonicalName() + '.' + field.getName());
        			cfg.objectClass(clazz).objectField(field.getName()).indexed(true);
        		}
        	}
        	
    		// Check whether the class itself has an @IndexedField annotation
    		final Persistent.IndexedField annotation =  clazz.getAnnotation(Persistent.IndexedField.class);
    		if(annotation != null) {
        		for(String fieldName : annotation.names()) {
        			if(logDEBUG) Logger.debug(this, "Registering indexed field " + clazz.getCanonicalName() + '.' + fieldName);
        			cfg.objectClass(clazz).objectField(fieldName).indexed(true);
        		}
    		}
        }
        
        // TODO: We should check whether db4o inherits the indexed attribute to child classes, for example for this one:
        // Unforunately, db4o does not provide any way to query the indexed() property of fields, you can only set it
        // We might figure out whether inheritance works by writing a benchmark.
        
        return cfg;
	}
	
	/**
	 * ATTENTION: This function is duplicated in the Web Of Trust plugin, please backport any changes.
	 */
	private synchronized ExtObjectContainer openDatabase(File file) {
		Logger.normal(this, "Opening database using db4o " + Db4o.version());
		
		if(db != null) 
			throw new RuntimeException("Database is opened already!");
		
		try {
			defragmentDatabase(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

        
		return Db4o.openFile(getNewDatabaseConfiguration(), file.getAbsolutePath()).ext();
	}
	
	/**
	 * ATTENTION: This function is duplicated in the Web Of Trust plugin, please backport any changes.
	 */
	private synchronized void restoreDatabaseBackup(File databaseFile, File backupFile) throws IOException {
		Logger.warning(this, "Trying to restore database backup: " + backupFile.getAbsolutePath());
		
		if(db != null)
			throw new RuntimeException("Database is opened already!");
		
		if(backupFile.exists()) {
			try {
				FileUtil.secureDelete(databaseFile);
			} catch(IOException e) {
				Logger.warning(this, "Deleting of the database failed: " + databaseFile.getAbsolutePath());
			}
			
			if(backupFile.renameTo(databaseFile)) {
				Logger.warning(this, "Backup restored!");
			} else {
				throw new IOException("Unable to rename backup file back to database file: " + databaseFile.getAbsolutePath());
			}

		} else {
			throw new IOException("Cannot restore backup, it does not exist!");
		}
	}

	/**
	 * ATTENTION: This function is duplicated in the Web Of Trust plugin, please backport any changes.
	 */
	private synchronized void defragmentDatabase(File databaseFile) throws IOException {
		Logger.normal(this, "Defragmenting database ...");
		
		if(db != null) 
			throw new RuntimeException("Database is opened already!");
		
		if(mPluginRespirator == null) {
			Logger.normal(this, "No PluginRespirator found, probably running as unit test, not defragmenting.");
			return;
		}
		
		final Random random = mPluginRespirator.getNode().fastWeakRandom;
		
		// Open it first, because defrag will throw if it needs to upgrade the file.
		{
			final ObjectContainer database = Db4o.openFile(getNewDatabaseConfiguration(), databaseFile.getAbsolutePath());
			
			// Db4o will throw during defragmentation if new fields were added to classes and we didn't initialize their values on existing
			// objects before defragmenting. So we just don't defragment if the database format version has changed.
			final boolean canDefragment = Configuration.peekDatabaseFormatVersion(this, database.ext()) == Freetalk.DATABASE_FORMAT_VERSION;

			while(!database.close());
			
			if(!canDefragment) {
				Logger.normal(this, "Not defragmenting, database format version changed!");
				return;
			}
			
			if(!databaseFile.exists()) {
				Logger.error(this, "Database file does not exist after openFile: " + databaseFile.getAbsolutePath());
				return;
			}
		}

		final File backupFile = new File(databaseFile.getAbsolutePath() + ".backup");
		
		if(backupFile.exists()) {
			Logger.error(this, "Not defragmenting database: Backup file exists, maybe the node was shot during defrag: " + backupFile.getAbsolutePath());
			return;
		}	

		final File tmpFile = new File(databaseFile.getAbsolutePath() + ".temp");
		FileUtil.secureDelete(tmpFile);

		/* As opposed to the default, BTreeIDMapping uses an on-disk file instead of in-memory for mapping IDs. 
		/* Reduces memory usage during defragmentation while being slower.
		/* However as of db4o 7.4.63.11890, it is bugged and prevents defragmentation from succeeding for my database, so we don't use it for now. */
		final DefragmentConfig config = new DefragmentConfig(databaseFile.getAbsolutePath(), 
																backupFile.getAbsolutePath()
															//	,new BTreeIDMapping(tmpFile.getAbsolutePath())
															);
		
		/* Delete classes which are not known to the classloader anymore - We do NOT do this because:
		/* - It is buggy and causes exceptions often as of db4o 7.4.63.11890
		/* - Freetalk has always had proper database upgrade code (function upgradeDB()) and does not rely on automatic schema evolution.
		/*   If we need to get rid of certain objects we should do it in the database upgrade code, */
		// config.storedClassFilter(new AvailableClassFilter());
		
		config.db4oConfig(getNewDatabaseConfiguration());
		
		try {
			Defragment.defrag(config);
		} catch (Exception e) {
			Logger.error(this, "Defragment failed", e);
			
			try {
				restoreDatabaseBackup(databaseFile, backupFile);
				return;
			} catch(IOException e2) {
				Logger.error(this, "Unable to restore backup", e2);
				throw new IOException(e);
			}
		}

		final long oldSize = backupFile.length();
		final long newSize = databaseFile.length();

		if(newSize <= 0) {
			Logger.error(this, "Defrag produced an empty file! Trying to restore old database file...");
			
			databaseFile.delete();
			try {
				restoreDatabaseBackup(databaseFile, backupFile);
			} catch(IOException e2) {
				Logger.error(this, "Unable to restore backup", e2);
				throw new IOException(e2);
			}
		} else {
			final double change = 100.0 * (((double)(oldSize - newSize)) / ((double)oldSize));
			FileUtil.secureDelete(tmpFile, random);
			FileUtil.secureDelete(backupFile, random);
			Logger.normal(this, "Defragment completed. "+SizeUtil.formatSize(oldSize)+" ("+oldSize+") -> "
					+SizeUtil.formatSize(newSize)+" ("+newSize+") ("+(int)change+"% shrink)");
		}

	}

	/**
	 * ATTENTION: This function is duplicated in the Web Of Trust plugin, please backport any changes.
	 */
	private void upgradeDatabase() {
		int oldVersion = mConfig.getDatabaseFormatVersion();
		
		// ATTENTION: Make sure that no upgrades are done here which are needed by the constructors of
		// IdentityManager/MessageManager/PersistentTaskManager - they are created before this function is called.
		
		// Skeleton for upgrade code
		/*
		if(oldVersion == blah) {
			Logger.normal(this, "Upgrading database version " + oldVersion);
			
			synchronized(mMessageManager) {
				Logger.normal(this, "Doing stuff");
			
				synchronized(Persistent.transactionLock(db)) {
					try {
						Persistent.checkedCommit(db, this);
					} catch(RuntimeException e) {
						Persistent.checkedRollbackAndThrow(db, this, e);
					}
				}
			}
			
			mConfig.setDatabaseFormatVersion(++oldVersion);
			mConfig.storeAndCommit();
			Logger.normal(this, "Upgraded database to version " + oldVersion);
		}
		*/
		
		if(oldVersion == Freetalk.DATABASE_FORMAT_VERSION)
			return;
		
		throw new RuntimeException("Your database is too outdated to be upgraded automatically, please create a new one by deleting " 
				+ DATABASE_FILENAME + ". Contact the developers if you really need your old data.");
	}
	
	/**
	 * ATTENTION: This function is duplicated in the Web Of Trust plugin, please backport any changes.
	 */
	private void databaseIntegrityTest() {
		Logger.normal(this, "Testing database integrity...");
		synchronized(mIdentityManager) {
		synchronized(mMessageManager) {
		synchronized(mTaskManager) {
			final Query q = db.query();
			q.constrain(Persistent.class);
	
			for(final Persistent p : new Persistent.InitializingObjectSet<Persistent>(this, q)) {
				try {
					p.databaseIntegrityTest();
				} catch(Exception e) {
					try {
						Logger.error(this, "Integrity test failed for " + p, e);
					} catch(Exception toStringException) {
						Logger.error(this, "Integrity test failed for object and toString also failed, toString-Exception below", toStringException);
						Logger.error(this, "Original integrity test failure below", e);
					}
				}
			}
		}
		}
		}
		Logger.normal(this, "Database integrity test finished.");
	}

	/**
	 * ATTENTION: This function is duplicated in the Web Of Trust plugin, please backport any changes.
	 */
	private void closeDatabase() {
		if(db == null) {
			Logger.warning(this, "Terminated already.");
			return;
		}
		
		synchronized(Persistent.transactionLock(db)) {
			try {
				System.gc();
				db.rollback();
				System.gc();
				db.close();
				db = null;
			}
			catch(RuntimeException e) {
				Logger.error(this, "Error while closing database", e);
			}
		}
	}
	
	public synchronized void handleWotConnected() {
		Logger.normal(this, "Connected to WoT plugin.");
		wotConnected = true;
	}
	
	private boolean wotConnected;
	
	public synchronized void handleWotDisconnected() {
		Logger.normal(this, "Disconnected from WoT plugin");
		wotConnected = false;
	}
	
	public synchronized boolean wotConnected() {
		return wotConnected;
	}
	
	public boolean wotOutdated() {
		return false;
	}

	@Override public void terminate() {
		if(logDEBUG) Logger.debug(this, "Terminating Freetalk ...");
		
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
			mOldMessageListFetcher.terminate();
		}
		catch(Exception e) {
			Logger.error(this, "Error during termination.", e);
		}
		
		try {
			mNewMessageListFetcher.stop();
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
		
		if(logDEBUG) Logger.debug(this, "Freetalk plugin terminated.");
	}
	
	@Override public void handle(
			PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		
		mFCPInterface.handle(replysender, params, data, accesstype);
	}
	
	public PluginRespirator getPluginRespirator() {
		return mPluginRespirator;
	}
	
	public Configuration getConfig() {
		return mConfig;
	}
	
	protected ExtObjectContainer getDatabase() {
		return db;
	}
	
	public WoTIdentityManager getIdentityManager() {
		return mIdentityManager;
	}
	
	public WoTMessageInserter getMessageInserter() {
		return mMessageInserter;
	}
	
	public WoTMessageManager getMessageManager() {
		return mMessageManager;
	}
	
	public PersistentTaskManager getTaskManager() {
		return mTaskManager;
	}
	
	public WoTNewMessageListFetcher getNewMessageListFetcher() {
		return mNewMessageListFetcher;
	}
	
	public WoTOldMessageListFetcher getOldMessageListFetcher() {
		return mOldMessageListFetcher;
	}
	
	public WoTMessageFetcher getMessageFetcher() {
		return mMessageFetcher;
	}	

	@Override public String getVersion() {
		return Version.longVersionString;
	}

	@Override public long getRealVersion() {
		return Version.getRealVersion();
	}

    /**
     * This code is only used by FredPluginL10n...
     * 
     * @param arg0
     * @return
     */
    @Override public String getString(String arg0) {
        return getBaseL10n().getString(arg0);
    }

	
    /**
     * This code is only called during startup or when the user
     * selects another language in the UI.
     * @param newLanguage Language to use.
     */
    @Override public void setLanguage(final BaseL10n.LANGUAGE newLanguage) {
        Freetalk.l10n = new PluginL10n(this, newLanguage);
        if(logDEBUG) Logger.debug(this, "Set LANGUAGE to: " + newLanguage.isoCode);
    }

	@Override public void setTheme(THEME newTheme) {
		mTheme = newTheme;
		if(logDEBUG) Logger.debug(this, "Set THEME to: " + mTheme.code);
	}

    /**
     * This is where our L10n files are stored.
     * @return Path of our L10n files.
     */
    @Override public String getL10nFilesBasePath() {
        return "plugins/Freetalk/l10n/";
    }

    /**
     * This is the mask of our L10n files : lang_en.l10n, lang_de.10n, ...
     * @return Mask of the L10n files.
     */
    @Override public String getL10nFilesMask() {
        return "lang_${lang}.l10n";
    }

    /**
     * Override L10n files are stored on the disk, their names should be explicit
     * we put here the plugin name, and the "override" indication. Plugin L10n
     * override is not implemented in the node yet.
     * @return Mask of the override L10n files.
     */
    @Override public String getL10nOverrideFilesMask() {
        return "Freetalk_lang_${lang}.override.l10n";
    }

    /**
     * Get the ClassLoader of this plugin. This is necessary when getting
     * resources inside the plugin's Jar, for example L10n files.
     * @return
     */
    @Override public ClassLoader getPluginClassLoader() {
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
