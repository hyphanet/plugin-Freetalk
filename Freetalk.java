/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import plugins.Freetalk.WoT.FTIdentityManagerWoT;
import plugins.Freetalk.WoT.FTMessageManagerWoT;
import plugins.Freetalk.ui.Errors;
import plugins.Freetalk.ui.IdentityEditor;
import plugins.Freetalk.ui.Messages;
import plugins.Freetalk.ui.Status;
import plugins.Freetalk.ui.Welcome;
import plugins.WoT.WoT;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.config.Configuration;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.clients.http.PageMaker.THEME;
import freenet.keys.FreenetURI;
import freenet.l10n.L10n.LANGUAGE;
import freenet.pluginmanager.DownloadPluginHTTPException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginThemed;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.NotFoundPluginHTTPException;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.RedirectPluginHTTPException;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.api.HTTPUploadedFile;
import freenet.support.io.TempBucketFactory;

/**
 * @author saces
 *
 */
public class Freetalk implements FredPlugin, FredPluginFCP, FredPluginHTTP, FredPluginL10n, FredPluginThemed, FredPluginThreadless, FredPluginVersioned {

	/* Constants */
	
	public static final String PLUGIN_URI = "/plugins/plugins.Freetalk.Freetalk";
	public static final String PLUGIN_TITLE = "Freetalk";
	public static final String WOT_NAME = "plugins.WoT.WoT";
	public static final String DATABASE_FILE = "freetalk_data.db4o";

	/* References from the node */
	
	public PluginRespirator pr;

	public PageMaker pm;

	private LANGUAGE language;
	
	private THEME theme;

	private HighLevelSimpleClient client;
	
	private TempBucketFactory tbf;
	
	/* The plugin's own references */
	
	public ObjectContainer db;
	
	private FTIdentityManagerWoT mIdentityManager;
	
	private FTMessageManagerWoT mMessageManager;
	
	
	public void runPlugin(PluginRespirator myPR) {
		
		Logger.debug(this, "Freetalk plugin starting up...");

		pr = myPR;
		
		client = pr.getHLSimpleClient();

		Configuration dbCfg = Db4o.newConfiguration();
		for(String f : FTMessage.getIndexedFields())
			dbCfg.objectClass(FTMessage.class).objectField(f).indexed(true);
		dbCfg.objectClass(FTMessage.class).cascadeOnUpdate(true);
		// TODO: decide about cascade on delete. 
		for(String f : FTBoard.getIndexedFields())
			dbCfg.objectClass(FTBoard.class).objectField(f).indexed(true);
		
		db = Db4o.openFile(dbCfg, DATABASE_FILE);

		/* DEBUG: Wipe database on startup */
		ObjectSet<Object> result = db.queryByExample(new Object());
		for (Object o : result) db.delete(o);
		db.commit();

		tbf = pr.getNode().clientCore.tempBucketFactory;
		
		mIdentityManager = new FTIdentityManagerWoT(db, pr.getNode().executor, (WoT)getWoTPlugin() );
		
		mMessageManager = new FTMessageManagerWoT(db, pr.getNode().executor, mIdentityManager);
		
		pm = pr.getPageMaker();
		pm.addNavigationLink(PLUGIN_URI + "/", "Home", "Freetalk plugin home", false, null);
		pm.addNavigationLink(PLUGIN_URI + "/ownidentities", "Own Identities", "Manage your own identities", false, null);
		pm.addNavigationLink(PLUGIN_URI + "/knownidentities", "Known Identities", "Manage others identities", false, null);
		pm.addNavigationLink(PLUGIN_URI + "/messages", "Messages", "View Messages", false, null);
		pm.addNavigationLink(PLUGIN_URI + "/status", "Dealer status", "Show what happens in background", false, null);
		pm.addNavigationLink("/", "Fproxy", "Back to nodes home", false, null);
		
		Logger.debug(this, "Freetalk plugin loaded.");
	}

	public void terminate() {
		/* FIXME: Signal the identity manager and message manager to shutdown */
		db.close();
		Logger.debug(this, "Freetalk plugin terminated.");
	}

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {

		if (request.isParameterSet("formPassword")) {
			String pass = request.getParam("formPassword");
			if ((pass.length() == 0) || !pass.equals(pr.getNode().clientCore.formPassword)) {
				return Errors.makeErrorPage(this, "Buh! Invalid form password");
			}
		}

		String page = request.getPath().substring(PLUGIN_URI.length());
		if ((page.length() < 1) || ("/".equals(page)))
			return Welcome.makeWelcomePage(this);

		if ("/status".equals(page)) {
			return Status.makeStatusPage(this);
		}
		
		if ("/ownidentities".equals(page))
			return IdentityEditor.makeOwnIdentitiesPage(this, request);

		if ("/knownidentities".equals(page))
			return IdentityEditor.makeKnownIdentitiesPage(this, request);

		if ("/messages".equals(page))
			return Messages.makeMessagesPage(this, request);

		throw new NotFoundPluginHTTPException("Resource not found in FreetalkPlugin", page);
	}
	
	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Hello", "Nice try ;)");
		sfs.putOverwrite("Sorry", "Not implemeted yet :(");
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		String pass = request.getPartAsString("formPassword", 32);
		if ((pass.length() == 0) || !pass.equals(pr.getNode().clientCore.formPassword)) {
			return Errors.makeErrorPage(this, "Buh! Invalid form password");
		}

		String page = request.getPath().substring(PLUGIN_URI.length());

		if (page.length() < 1)
			throw new NotFoundPluginHTTPException("Resource not found", page);

		if (page.equals("/exportDB")) {
			StringWriter sw = new StringWriter();
			try {
				Backup.exportConfigDb(db, sw);
			} catch (IOException e) {
				Logger.error(this, "Error While exporting database!", e);
				return Errors.makeErrorPage(this, "Server BuhBuh! " + e.getMessage());
			}
			throw new DownloadPluginHTTPException(sw.toString().getBytes(), "fms-kidding.xml", "fms-clone/db-backup");
		}
		
		if (page.equals("/importDB")) {
			HTTPUploadedFile file = request.getUploadedFile("filename");
			if (file == null || file.getFilename().trim().length() == 0) {
				return Errors.makeErrorPage(this, "No file to import selected!");
			}
			try {
				Backup.importConfigDb(db, file.getData().getInputStream());
			} catch (Exception e) {
				Logger.error(this, "Error While importing db from: " + file.getFilename(), e);
				return Errors.makeErrorPage(this, "Error While importing db from: " + file.getFilename() + e.getMessage());
			}
			throw new RedirectPluginHTTPException("", PLUGIN_URI);
		}

		if (page.equals("/createownidentity")) {
			List<String> err = new ArrayList<String>();
			String nick = request.getPartAsString("nick", 1024).trim();
			String requestUri = request.getPartAsString("requestURI", 1024);
			String insertUri = request.getPartAsString("insertURI", 1024);
			boolean publish = "true".equals(request.getPartAsString("publishTrustList", 24));

			IdentityEditor.checkNick(err, nick);

			if ((requestUri.length() == 0) && (insertUri.length() == 0)) {
				FreenetURI[] kp = client.generateKeyPair("fms");
				insertUri = kp[0].toString();
				requestUri = kp[1].toString();
				err.add("URI was empty, I generated one for you.");
				return IdentityEditor.makeNewOwnIdentityPage(this, nick, requestUri, insertUri, publish, err);
			}

			IdentityEditor.checkInsertURI(err, insertUri);
			IdentityEditor.checkRequestURI(err, requestUri);

			if (err.size() == 0) {
				// FIXME: use identity manager to implement this
				throw new UnsupportedOperationException();
				/*
				FTOwnIdentity oi = new FTOwnIdentity(nick, requestUri, insertUri, publish);
				IdentityEditor.addNewOwnIdentity(db_config, oi, err);
				*/
			}

			if (err.size() == 0) {
				throw new RedirectPluginHTTPException("", PLUGIN_URI + "/ownidentities");
			}

			return IdentityEditor.makeNewOwnIdentityPage(this, nick, requestUri, insertUri, publish, err);
		}

		if (page.equals("/addknownidentity")) {
			List<String> err = new ArrayList<String>();

			String requestUri = request.getPartAsString("requestURI", 1024);

			if (requestUri.length() == 0) {
				err.add("Are you jokingly? URI was empty.");
				return IdentityEditor.makeNewKnownIdentityPage(this, requestUri, err);
			}

			IdentityEditor.checkRequestURI(err, requestUri);

			if (err.size() == 0) {
				// FIXME: use identity manager to implement this
				throw new UnsupportedOperationException();
				/*
				FTIdentity i = new FTIdentity("", requestUri);
				IdentityEditor.addNewKnownIdentity(db_config, i, err);
				*/
			}

			if (err.size() == 0) {
				throw new RedirectPluginHTTPException("", PLUGIN_URI + "/knownidentities");
			}

			return IdentityEditor.makeNewKnownIdentityPage(this, requestUri, err);
		}

		if (page.equals("/deleteOwnIdentity")) {
			List<String> err = new ArrayList<String>();

			String requestUri = request.getPartAsString("identity", 1024);
			if (requestUri.length() == 0) {
				err.add("Are you jokingly? URI was empty.");
				return IdentityEditor.makeDeleteOwnIdentityPage(this, requestUri, err);
			}

			if (request.isPartSet("confirmed")) {
				IdentityEditor.deleteIdentity(this, requestUri, err);
			} else {
				err.add("Please confirm.");
			}

			if (err.size() > 0) {
				return IdentityEditor.makeDeleteOwnIdentityPage(this, requestUri, err);
			}
			throw new RedirectPluginHTTPException("", PLUGIN_URI + "/ownidentities");
			// return IdentityEditor.makeDeleteOwnIdentityPage(fms, requestUri,
			// err);
		}

		if (page.equals("/deleteIdentity")) {
			List<String> err = new ArrayList<String>();

			String requestUri = request.getPartAsString("identity", 1024);
			if (requestUri.length() == 0) {
				err.add("Are you jokingly? URI was empty.");
				return IdentityEditor.makeDeleteKnownIdentityPage(this, requestUri, err);
			}

			if (request.isPartSet("confirmed")) {
				IdentityEditor.deleteIdentity(this, requestUri, err);
			} else {
				err.add("Please confirm.");
			}

			if (err.size() > 0) {
				return IdentityEditor.makeDeleteKnownIdentityPage(this, requestUri, err);
			}
			throw new RedirectPluginHTTPException("", PLUGIN_URI + "/knownidentities");
		}
		throw new NotFoundPluginHTTPException("Resource not found", page);
	}

	public String getVersion() {
		return "α r" + Version.svnRevision;
	}

	public String getString(String key) {
		// Logger.error(this, "Request translation for "+key);
		return key;
	}

	public void setLanguage(LANGUAGE newLanguage) {
		language = newLanguage;
		Logger.error(this, "Set LANGUAGE to: " + language.isoCode);
	}

	public void setTheme(THEME newTheme) {
		theme= newTheme;
		Logger.error(this, "Set THEME to: " + theme.code);
	}
	
	public FredPluginFCP getWoTPlugin() {
		return pr.getNode().pluginManager.getFCPPlugin(Freetalk.WOT_NAME);
	}

	public long countIdentities() {
		return db.queryByExample(FTIdentity.class).size() - countOwnIdentities();
	}

	public long countOwnIdentities() {
		return db.queryByExample(FTOwnIdentity.class).size();
	}
	

	final public HTMLNode getPageNode() {
		return pm.getPageNode(Freetalk.PLUGIN_TITLE, null);
	}

}
