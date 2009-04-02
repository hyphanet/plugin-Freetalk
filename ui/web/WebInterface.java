/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.util.Iterator;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.clients.http.PageMaker;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.NotFoundPluginHTTPException;
import freenet.pluginmanager.PluginHTTPException;
import freenet.support.api.HTTPRequest;


/**
 * 
 * @author xor, saces
 */
public final class WebInterface implements FredPluginHTTP {
	
	private final Freetalk mFreetalk;
	
	private final PageMaker mPageMaker;
	
	private FTOwnIdentity mOwnIdentity;

	public WebInterface(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
		mPageMaker = mFreetalk.getPluginRespirator().getPageMaker();
		mOwnIdentity = null;
	}
	
	private void setUpMenu() {
		mPageMaker.removeNavigationLink("Home");
		mPageMaker.removeNavigationLink("Boards");
		mPageMaker.removeNavigationLink("Identities");
		mPageMaker.removeNavigationLink("Log out");
		mPageMaker.removeNavigationLink("Back to Freenet");
		
		if(mOwnIdentity == null) {
			mPageMaker.addNavigationLink("/", "Back to Freenet", "Back to nodes home", false, null);
			return;
		}
		
		mPageMaker.addNavigationLink(Freetalk.PLUGIN_URI + "/", "Home", "Freetalk plugin home", false, null);
		mPageMaker.addNavigationLink(Freetalk.PLUGIN_URI + "/messages", "Boards", "View all boards", false, null);
		mPageMaker.addNavigationLink(Freetalk.PLUGIN_URI + "/identities", "Identities", "Manage your own and known identities", false, null);
		mPageMaker.addNavigationLink(Freetalk.PLUGIN_URI + "/LogOut", "Log out", "Log out", false, null);
		mPageMaker.addNavigationLink("/", "Back to Freenet", "Back to nodes home", false, null);
	}
	
	private FTOwnIdentity getLoggedInOwnIdentity() {
		return mOwnIdentity;
	}

	public final String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		/* FIXME 
		String pass = request.getParam("formPassword");
		if(pass != null) {	// FIXME: is this correct? what if the client just does not specify the password so that its null? 
			if ((pass.length() == 0) || !pass.equals(pr.getNode().clientCore.formPassword))
				return Errors.makeErrorPage(this, "Buh! Invalid form password");
		}
		*/

		/* FIXME: ugly hack! remove! */
		String page = request.getPath().substring(Freetalk.PLUGIN_URI.length());
		int endIndex = request.getPath().indexOf('?');
		if(endIndex > 0)
			page = page.substring(0, endIndex);
		
		if(page.equals("/LogOut"))
			mOwnIdentity = null;
		
		setUpMenu();
		
		if(page.equals("/CreateIdentity"))
			return new CreateIdentityWizard(this, request).toHTML();
		
		if(getLoggedInOwnIdentity() == null)
			return new LogInPage(this, null, request).toHTML();
		
		/* Anything below this line assumes that we have a logged in own identity */
		
		if ((page.length() < 1) || ("/".equals(page)))
			return new Welcome(this, getLoggedInOwnIdentity(), request).toHTML();
		
		if ("/identities".equals(page))
			return new IdentityEditor(this, getLoggedInOwnIdentity(), request).toHTML();

		if ("/messages".equals(page))
			return new BoardsPage(this, getLoggedInOwnIdentity(), request).toHTML();
		
		try {
			/* FIXME: Also use getLoggedInOwnIdentity() here? */
			if(page.equals("/showBoard"))
				return new BoardPage(this, getLoggedInOwnIdentity(), request).toHTML();
			
			if(page.equals("/showThread"))
				return new ThreadPage(this, getLoggedInOwnIdentity(), request).toHTML();
		}
		/* TODO: Make this exceptions store the specified non-existant element theirselves */
		catch(NoSuchBoardException e) {
			throw new NotFoundPluginHTTPException("Unknown board " + request.getParam("name"), page);
		}
		catch(NoSuchMessageException e) {
			throw new NotFoundPluginHTTPException("Unknown message " + request.getParam("id"), page);
		}
	

		throw new NotFoundPluginHTTPException("Resource not found in Freetalk plugin", page);
	}

	public final String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		String pass = request.getPartAsString("formPassword", 32);
		if (pass == null || (pass.length() == 0) || !pass.equals(mFreetalk.getPluginRespirator().getNode().clientCore.formPassword)) {
			return new Errors(this, null, request, "Error", "Invalid form password.").toHTML();
		}

		String page = request.getPath().substring(Freetalk.PLUGIN_URI.length());

		if (page.length() < 1)
			throw new NotFoundPluginHTTPException("Resource not found", page);
		
		try {
			if(page.equals("/CreateIdentity")) {
				setUpMenu();
				return new CreateIdentityWizard(this, request).toHTML();
			}
			
			/* Anything below this line requires the user to be logged in with a certain own identity */
			
			FTOwnIdentity ownId = mFreetalk.getIdentityManager().getOwnIdentity(request.getPartAsString("OwnIdentityID", 64));
			if(page.equals("/LogIn")) {
				mOwnIdentity = ownId;
				setUpMenu();
				return new Welcome(this, ownId, request).toHTML();
			}
			
			setUpMenu();
			
			if(page.equals("/NewBoard"))
				return new NewBoardPage(this, ownId, request).toHTML();
			
			if(page.equals("/NewThread"))
				return new NewThreadPage(this, ownId, request).toHTML();
			
			if(page.equals("/NewReply")) 
				return new NewReplyPage(this, ownId, request).toHTML();
		}
		catch(NoSuchIdentityException e) {
			throw new NotFoundPluginHTTPException(e.getMessage(), page);
		}
		catch(NoSuchBoardException e) {
			throw new NotFoundPluginHTTPException(e.getMessage(), page);
		}
		catch (NoSuchMessageException e) {
			throw new NotFoundPluginHTTPException(e.getMessage(), page);
		}

		/*
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
		*/
		
		/*
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
			throw new RedirectPluginHTTPException("", mFreetalk.PLUGIN_URI);
		}
		*/

		/*
		if (page.equals("/createownidentity")) {
			List<String> err = new ArrayList<String>();
			String nick = request.getPartAsString("nick", 1024).trim();
			String requestUri = request.getPartAsString("requestURI", 1024);
			String insertUri = request.getPartAsString("insertURI", 1024);
			boolean publish = "true".equals(request.getPartAsString("publishTrustList", 24));

			IdentityEditor.checkNick(err, nick);

			if ((requestUri.length() == 0) && (insertUri.length() == 0)) {
				FreenetURI[] kp = mClient.generateKeyPair("fms");
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
				// FTOwnIdentity oi = new FTOwnIdentity(nick, requestUri, insertUri, publish);
				// IdentityEditor.addNewOwnIdentity(db_config, oi, err);
			}

			if (err.size() == 0) {
				throw new RedirectPluginHTTPException("", mFreetalk.PLUGIN_URI + "/ownidentities");
			}

			return IdentityEditor.makeNewOwnIdentityPage(this, nick, requestUri, insertUri, publish, err);
		}
		*/

		/*
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
				
				//FTIdentity i = new FTIdentity("", requestUri);
				//IdentityEditor.addNewKnownIdentity(db_config, i, err);
				
			}

			if (err.size() == 0) {
				throw new RedirectPluginHTTPException("", mFreetalk.PLUGIN_URI + "/knownidentities");
			}

			return IdentityEditor.makeNewKnownIdentityPage(this, requestUri, err);
		}
		*/

		/*
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
			throw new RedirectPluginHTTPException("", mFreetalk.PLUGIN_URI + "/ownidentities");
			// return IdentityEditor.makeDeleteOwnIdentityPage(fms, requestUri,
			// err);
		}
		*/

		/*
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
			throw new RedirectPluginHTTPException("", mFreetalk.PLUGIN_URI + "/knownidentities");
		}
		*/
		
		throw new NotFoundPluginHTTPException("Resource not found", page);
	}
	
	public final Freetalk getFreetalk() {
		return mFreetalk;
	}

	public final PageMaker getPageMaker() {
		return mPageMaker;
	}
	
	/**
	 * @return The <code>FTOwnIdentity</code> which is currently logged in.
	 */
	public final FTOwnIdentity getOwnIdentity() {
		Iterator<FTOwnIdentity> iter = mFreetalk.getIdentityManager().ownIdentityIterator();
		return iter.hasNext() ? iter.next() : null;
	}
}
