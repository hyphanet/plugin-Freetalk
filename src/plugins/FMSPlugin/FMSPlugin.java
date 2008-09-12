/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import plugins.FMSPlugin.ui.Backup;
import plugins.FMSPlugin.ui.Errors;
import plugins.FMSPlugin.ui.IdentityEditor;
import plugins.FMSPlugin.ui.Messages;
import plugins.FMSPlugin.ui.Status;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.config.Configuration;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.clients.http.PageMaker.THEME;
import freenet.keys.FreenetURI;
import freenet.l10n.L10n.LANGUAGE;
import freenet.pluginmanager.FredPluginToadlet;
import freenet.pluginmanager.NotFoundPluginHTTPException;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.RedirectPluginHTTPException;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * @author saces
 *
 */
public class FMSPlugin implements FredPluginToadlet {

	public static String SELF_URI = "/plugins/plugins.FMSPlugin.FMSPlugin";
	public static String SELF_TITLE = "FMS clone";
	//private static String WOT_NAME = "plugins.WoT.WoTplugin";

	public final String MESSAGEBASE = "fms";

	private PluginRespirator pr;

	private PageMaker pm;

	private LANGUAGE language;

	private HighLevelSimpleClient client;

	private FMSDealer dealer;

	private ObjectContainer db_config;
	private ObjectContainer db_cache;

	private FMS fms;

	public void runPlugin(PluginRespirator pr2) {

		Logger.error(this, "Start");

		pr = pr2;

		pm = pr.getPageMaker();
		pm.addNavigationLink("/", "Fproxy", "Back to Fpoxy", false, null);
		pm.addNavigationLink(SELF_URI + "/status", "Dealer status", "Show what happens in background", false, null);
		pm.addNavigationLink(SELF_URI + "/ownidentities", "Own Identities", "Manage your own identities", false, null);
		pm.addNavigationLink(SELF_URI + "/knownidentities", "Known Identities", "Manage others identities", false, null);
		pm.addNavigationLink(SELF_URI + "/messages", "Messages", "View Messages", false, null);

		client = pr.getHLSimpleClient();

		Configuration config_config = Db4o.newConfiguration();
		config_config.objectClass(FMSOwnIdentity.class).objectField("requestUri").indexed(true);
		config_config.objectClass(FMSIdentity.class).objectField("requestUri").indexed(true);
		db_config = Db4o.openFile(config_config, "fms_conf.db4o");

		Configuration cache_config = Db4o.newConfiguration();
		db_cache = Db4o.openFile(cache_config, "fms_cache.db4o");

		// while develop wipe cache on startup
		ObjectSet<Object> result = db_cache.queryByExample(new Object());
		if (result.size() > 0) {
			for (Object o : result) {
				db_cache.delete(o);
			}
			db_cache.commit();
		}

		dealer = new FMSDealer(pr.getNode().executor);

		fms = new FMS(pr.getNode().clientCore.tempBucketFactory, pm, pr, db_config, db_cache);
		
	}

	public void terminate() {
		dealer.killMe();
		db_config.close();
		db_cache.close();
	}

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {

		if (request.isParameterSet("formPassword")) {
			String pass = request.getParam("formPassword");
			if ((pass.length() == 0) || !pass.equals(pr.getNode().clientCore.formPassword)) {
				return Errors.makeErrorPage(fms, "Buh! Invalid form password");
			}
		}

		String page = request.getPath().substring(SELF_URI.length());
		if ((page.length() < 1) || ("/".equals(page)))
			return Status.makeStartStopPage(fms);

		if ("/status".equals(page)) {
			return Status.makeStatusPage(fms);
		}

		if ("/ownidentities".equals(page))
			return IdentityEditor.makeOwnIdentitiesPage(fms, request);

		if ("/knownidentities".equals(page))
			return IdentityEditor.makeKnownIdentitiesPage(fms, request);

		if ("/messages".equals(page))
			return Messages.makeMessagesPage(fms, request);

		throw new NotFoundPluginHTTPException("Resource not found in FMSPlugin", page);
	}

	public String handleHTTPPut(HTTPRequest request) throws PluginHTTPException {
		throw new RedirectPluginHTTPException("", SELF_URI);
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		String pass = request.getPartAsString("formPassword", 32);
		if ((pass.length() == 0) || !pass.equals(pr.getNode().clientCore.formPassword)) {
			return Errors.makeErrorPage(fms, "Buh! Invalid form password");
		}

		String page = request.getPath().substring(SELF_URI.length());

		if (page.length() < 1)
			throw new NotFoundPluginHTTPException("Resource not found", page);
		// return makeStartStopPage();

		if (page.equals("/impexport")) {
			if (!request.isPartSet("filename")) {
				return Errors.makeErrorPage(fms, "Invalid Request on »impexport«: missing filename");
			}
			String fileName = request.getPartAsString("filename", 1024);
			if (request.isPartSet("importall")) {
				try {
					Backup.importConfigDb(db_config, fileName);
				} catch (Exception e) {
					Logger.error(this, "Error While importing from: " + fileName, e);
					return Errors.makeErrorPage(fms, "Server BuhBuh! " + e.getMessage());
				}
				throw new RedirectPluginHTTPException("", SELF_URI);
			}
			if (request.isPartSet("exportall")) {
				try {
					Backup.exportConfigDb(db_config, fileName);
				} catch (IOException e) {
					Logger.error(this, "Error While exporting to: " + fileName, e);
					return Errors.makeErrorPage(fms, "Server BuhBuh! " + e.getMessage());
				}
				Logger.error(this, "Succesful export to: " + fileName);
				throw new RedirectPluginHTTPException("", SELF_URI);
			}
			return Errors.makeErrorPage(fms, "Invalid Request on »ownIdentities«");
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
				return IdentityEditor.makeNewOwnIdentityPage(fms, nick, requestUri, insertUri, publish, err);
			}

			IdentityEditor.checkInsertURI(err, insertUri);
			IdentityEditor.checkRequestURI(err, requestUri);

			if (err.size() == 0) {
				FMSOwnIdentity oi = new FMSOwnIdentity(nick, requestUri, insertUri, publish);
				IdentityEditor.addNewOwnIdentity(db_config, oi, err);
			}

			if (err.size() == 0) {
				throw new RedirectPluginHTTPException("", SELF_URI + "/ownidentities");
			}

			return IdentityEditor.makeNewOwnIdentityPage(fms, nick, requestUri, insertUri, publish, err);
		}

		if (page.equals("/addknownidentity")) {
			List<String> err = new ArrayList<String>();

			String requestUri = request.getPartAsString("requestURI", 1024);

			if (requestUri.length() == 0) {
				err.add("Are you jokingly? URI was empty.");
				return IdentityEditor.makeNewKnownIdentityPage(fms, requestUri, err);
			}

			IdentityEditor.checkRequestURI(err, requestUri);

			if (err.size() == 0) {
				FMSIdentity i = new FMSIdentity("", requestUri);
				IdentityEditor.addNewKnownIdentity(db_config, i, err);
			}

			if (err.size() == 0) {
				throw new RedirectPluginHTTPException("", SELF_URI + "/knownidentities");
			}

			return IdentityEditor.makeNewKnownIdentityPage(fms, requestUri, err);
		}

		if (page.equals("/deleteOwnIdentity")) {
			List<String> err = new ArrayList<String>();

			String requestUri = request.getPartAsString("identity", 1024);
			if (requestUri.length() == 0) {
				err.add("Are you jokingly? URI was empty.");
				return IdentityEditor.makeDeleteOwnIdentityPage(fms, requestUri, err);
			}

			if (request.isPartSet("confirmed")) {
				IdentityEditor.deleteIdentity(fms, requestUri, err);
			} else {
				err.add("Please confirm.");
			}

			if (err.size() > 0) {
				return IdentityEditor.makeDeleteOwnIdentityPage(fms, requestUri, err);
			}
			throw new RedirectPluginHTTPException("", SELF_URI + "/ownidentities");
			// return IdentityEditor.makeDeleteOwnIdentityPage(fms, requestUri,
			// err);
		}

		if (page.equals("/deleteIdentity")) {
			List<String> err = new ArrayList<String>();

			String requestUri = request.getPartAsString("identity", 1024);
			if (requestUri.length() == 0) {
				err.add("Are you jokingly? URI was empty.");
				return IdentityEditor.makeDeleteKnownIdentityPage(fms, requestUri, err);
			}

			if (request.isPartSet("confirmed")) {
				IdentityEditor.deleteIdentity(fms, requestUri, err);
			} else {
				err.add("Please confirm.");
			}

			if (err.size() > 0) {
				return IdentityEditor.makeDeleteKnownIdentityPage(fms, requestUri, err);
			}
			throw new RedirectPluginHTTPException("", SELF_URI + "/knownidentities");
		}
		throw new NotFoundPluginHTTPException("Resource not found", page);
	}

	public String getVersion() {
		return "tp r" + Version.svnRevision;
	}

	public String getString(String key) {
		// language.;
		// Logger.error(this, "Request translation for "+key);
		return key;
	}

	public void setLanguage(LANGUAGE newLanguage) {
		Logger.error(this, "Set LANGUAGE to: " + newLanguage.isoCode);
		language = newLanguage;
	}

	public void setTheme(THEME theme) {
		// TODO Auto-generated method stub
		
	}
}
