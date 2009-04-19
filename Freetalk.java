/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessageFetcher;
import plugins.Freetalk.WoT.WoTMessageInserter;
import plugins.Freetalk.WoT.WoTMessageListFetcher;
import plugins.Freetalk.WoT.WoTMessageListInserter;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.ui.FCP.FCPInterface;
import plugins.Freetalk.ui.NNTP.FreetalkNNTPServer;
import plugins.Freetalk.ui.web.WebInterface;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.config.Configuration;
import com.db4o.query.Query;
import com.db4o.reflect.jdk.JdkReflector;

import freenet.clients.http.PageMaker.THEME;
import freenet.l10n.L10n.LANGUAGE;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThemed;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.FredPluginWithClassLoader;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;

/**
 * @author saces, xor
 *
 */
public class Freetalk implements FredPlugin, FredPluginFCP, FredPluginHTTP, FredPluginL10n, FredPluginThemed, FredPluginThreadless,
	FredPluginVersioned, FredPluginRealVersioned, FredPluginWithClassLoader {

	/* Constants */
	
	public static final String PLUGIN_URI = "/plugins/plugins.Freetalk.Freetalk";
	public static final String PLUGIN_TITLE = "Freetalk-testing"; /* FIXME REDFLAG: Has to be changed to Freetalk before release! Otherwise messages will disappear */
	public static final String WOT_NAME = "plugins.WoT.WoT";
	public static final String WOT_CONTEXT = "Freetalk";
	public static final String DATABASE_FILE = "freetalk_data.db4o";

	/* References from the node */
	
	private ClassLoader mClassLoader;
	
	private PluginRespirator mPluginRespirator; /* TODO: remove references in other classes so we can make this private */

	private LANGUAGE mLanguage;
	
	private THEME mTheme;

	
	/* The plugin's own references */
	
	private ObjectContainer db;
	
	private WoTIdentityManager mIdentityManager;
	
	private WoTMessageManager mMessageManager;
	
	private WoTMessageFetcher mMessageFetcher;
	
	private WoTMessageInserter mMessageInserter;
	
	private WoTMessageListFetcher mMessageListFetcher;
	
	private WoTMessageListInserter mMessageListInserter;

	private WebInterface mWebInterface;
	
	private FCPInterface mFCPInterface;
	
	private FreetalkNNTPServer mNNTPServer;
	
	
	public void runPlugin(PluginRespirator myPR) {
		Logger.debug(this, "Plugin starting up...");

		mPluginRespirator = myPR;

		Logger.debug(this, "Opening database...");
		db = openDatabase(DATABASE_FILE);
		Logger.debug(this, "Database opened.");

		deleteBrokenObjects();
		
		/* FIXME: Debug code, remove when not needed anymore */
		Logger.debug(this, "Wiping database...");
		ObjectSet<Object> result = db.queryByExample(new Object());
		for (Object o : result)
			db.delete(o);
		db.commit();
		Logger.debug(this, "Database wiped.");
		
		Logger.debug(this, "Creating identity manager...");
		int tries = 0;
		do {
			try {
				++tries;
				mIdentityManager = new WoTIdentityManager(db, mPluginRespirator);
			}
		
			catch(PluginNotFoundException e) {
				if(tries == 10)
					throw new RuntimeException(e);
				Logger.error(this, "WoT plugin not found! Retrying ...");
				try { Thread.sleep(10 * 1000); } catch(InterruptedException ex) {}
			}
		} while(mIdentityManager == null);
		
		Logger.debug(this, "Creating message manager...");
		mMessageManager = new WoTMessageManager(db, mPluginRespirator.getNode().executor, mIdentityManager);
		
		Logger.debug(this, "Creating message fetcher...");
		mMessageFetcher = new WoTMessageFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "FT Message Fetcher", mIdentityManager, mMessageManager);
		
		Logger.debug(this, "Creating message inserter...");
		mMessageInserter = new WoTMessageInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "FT Message Inserter", mIdentityManager, mMessageManager);
		
		Logger.debug(this, "Creating message list fetcher...");
		mMessageListFetcher = new WoTMessageListFetcher(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "FT MessageList Fetcher", mIdentityManager, mMessageManager);
		
		Logger.debug(this, "Creating message list inserter...");
		mMessageListInserter = new WoTMessageListInserter(mPluginRespirator.getNode(), mPluginRespirator.getHLSimpleClient(), "FT MessageList Inserter", mIdentityManager, mMessageManager);

		Logger.debug(this, "Creating webinterface ...");
		mWebInterface = new WebInterface(this);
		
		Logger.debug(this, "Creating FCP interface...");
		mFCPInterface = new FCPInterface(this);
		
		// FIXME: Fix this: https://bugs.freenetproject.org/view.php?id=2977
		//Logger.debug(this, "Starting NNTP server...");
		//mNNTPServer = new FreetalkNNTPServer(mPluginRespirator.getNode(), this, 1199, "127.0.0.1", "127.0.0.1");
		//mNNTPServer = new FreetalkNNTPServer(mPluginRespirator.getNode(), this, 1199, "0.0.0.0", "*");
		
		Logger.debug(this, "Plugin loaded.");
	}
	
	private ObjectContainer openDatabase(String filename) {
		Configuration dbCfg = Db4o.newConfiguration();
		dbCfg.reflectWith(new JdkReflector(mClassLoader));
		dbCfg.exceptionsOnNotStorable(true);
		dbCfg.activationDepth(5); /* FIXME: Figure out a reasonable value */
		
		for(String f : Message.getIndexedFields())
			dbCfg.objectClass(Message.class).objectField(f).indexed(true);
		
		dbCfg.objectClass(MessageList.MessageFetchFailedReference.class).persistStaticFieldValues(); /* Make it store enums */
		
		for(String f : Board.getIndexedFields()) {
			dbCfg.objectClass(Board.class).objectField(f).indexed(true);
		}
		
		for(String f : Board.getBoardMessageLinkIndexedFields()) {
			dbCfg.objectClass(Board.BoardMessageLink.class).objectField(f).indexed(true);
		}

		for(String f :  WoTIdentity.getIndexedFields()) {
			dbCfg.objectClass(WoTIdentity.class).objectField(f).indexed(true);
		}
		
		for(String f :  WoTOwnIdentity.getIndexedFields()) {
			dbCfg.objectClass(WoTOwnIdentity.class).objectField(f).indexed(true);
		}
		
		return Db4o.openFile(dbCfg, filename);
	}
	
	private void closeDatabase(ObjectContainer myDB) {
		/* FIXME: Figure out whether we can use db4o to tell whether this commit() does something. If it does, then log an error, because then
		 * probably we forgot a commit() somewhere. */
		/* FIXME: We should rater rollback() than commit(). But while the plugin is not stable enough it might be necessary to commit() */
		myDB.commit();
		myDB.close();
	}
	
	/**
	 * Debug function.
	 */
	@SuppressWarnings("unchecked")
	private void deleteBrokenObjects() {
		Query q = db.query();
		q.constrain(Message.class);
		q.descend("mAuthor").constrain(null).identity();
		
		ObjectSet<Message> brokenMessages = q.execute();
		for(Message m : brokenMessages) {
			Logger.error(m, "Deleting message with mAuthor == null: " + m.getURI());
			db.delete(m);
		}

		db.commit();
	}

	public void terminate() {
		Logger.debug(this, "Terminating Freetalk ...");
		
		/* We use single try/catch blocks so that failure of termination of one service does not prevent termination of the others */
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
			closeDatabase(db);
		} catch(Exception e) {
			Logger.error(this, "Error while closing database.", e);
		}
		
		Logger.debug(this, "Freetalk plugin terminated.");
	}
	
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		return mWebInterface.handleHTTPGet(request);
	}
	
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return mWebInterface.handleHTTPPost(request);
	}
	
	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		mFCPInterface.handle(replysender, params, data, accesstype);
	}
	
	public PluginRespirator getPluginRespirator() {
		return mPluginRespirator;
	}
	
	public IdentityManager getIdentityManager() {
		return mIdentityManager;
	}	
	
	public MessageManager getMessageManager() {
		return mMessageManager;
	}
	
	public String getVersion() {
		return "r" + Version.svnRevision;
	}
	
	public long getRealVersion() {
		return Version.version;
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
