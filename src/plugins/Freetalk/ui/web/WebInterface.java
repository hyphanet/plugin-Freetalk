/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContainer;
import freenet.clients.http.ToadletContext;
import freenet.node.NodeClientCore;
import freenet.support.api.HTTPRequest;


/**
 * 
 * @author xor (xor@freenetproject.org)
 * @author saces
 */
public class WebInterface {
	
	private final Freetalk mFreetalk;
	
	protected final PageMaker mPageMaker;
	
	private FTOwnIdentity mOwnIdentity;
	
	// Visible
	private final WebInterfaceToadlet homeToadlet;
	private final WebInterfaceToadlet messagesToadlet;
	private final WebInterfaceToadlet identitiesToadlet;
	private final WebInterfaceToadlet logOutToadlet;
	
	// Invisible
	private final WebInterfaceToadlet logInToadlet;
	private final WebInterfaceToadlet createIdentityToadlet;
	private final WebInterfaceToadlet newThreadToadlet;
	private final WebInterfaceToadlet showBoardToadlet;
	private final WebInterfaceToadlet showThreadToadlet;
	private final WebInterfaceToadlet newReplyToadlet;
	private final WebInterfaceToadlet newBoardToadlet;
	
	class HomeWebInterfaceToadlet extends WebInterfaceToadlet {

		protected HomeWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new Welcome(webInterface, mOwnIdentity, req);
		}
		
	}
	
	class MessagesWebInterfaceToadlet extends WebInterfaceToadlet {

		protected MessagesWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new BoardsPage(webInterface, mOwnIdentity, req);
		}
		
	}
	
	class IdentitiesWebInterfaceToadlet extends WebInterfaceToadlet {

		protected IdentitiesWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new IdentityEditor(webInterface, mOwnIdentity, req);
		}
		
	}
	
	protected final URI logIn;
	
	class LogOutWebInterfaceToadlet extends WebInterfaceToadlet {

		protected LogOutWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			setLoggedInOwnIdentity(null);
			throw new RedirectException(logIn);
		}
		
	}
	
	class LogInWebInterfaceToadlet extends WebInterfaceToadlet {

		protected LogInWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			try {
				if(req.getMethod().equals("GET"))
					setLoggedInOwnIdentity(mFreetalk.getIdentityManager().getOwnIdentity(req.getParam("OwnIdentityID")));
				else
					setLoggedInOwnIdentity(mFreetalk.getIdentityManager().getOwnIdentity(req.getPartAsString("OwnIdentityID", 64)));
				return new Welcome(webInterface, getLoggedInOwnIdentity(), req);
			}
			catch(NoSuchIdentityException e) {
				/* Ignore and continue as if the user did not specify an identity, he will end up with a LogInPage */
			}
			return new LogInPage(webInterface, mOwnIdentity, req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return homeToadlet;
		}
		
	}
	
	class CreateIdentityWebInterfaceToadlet extends WebInterfaceToadlet {

		protected CreateIdentityWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new CreateIdentityWizard(webInterface, req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return identitiesToadlet;
		}
		
	}
	
	class NewThreadWebInterfaceToadlet extends WebInterfaceToadlet {

		protected NewThreadWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			if(mOwnIdentity == null)
				throw new RedirectException(logIn);
			try {
				return new NewThreadPage(webInterface, mOwnIdentity, req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, mOwnIdentity, req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
				
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
	}
	
	class ShowBoardWebInterfaceToadlet extends WebInterfaceToadlet {

		protected ShowBoardWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			if(mOwnIdentity == null)
				throw new RedirectException(logIn);
			try {
				return new BoardPage(webInterface, mOwnIdentity, req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, mOwnIdentity, req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
				
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
	}
	
	class ShowThreadWebInterfaceToadlet extends WebInterfaceToadlet {

		protected ShowThreadWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			if(mOwnIdentity == null)
				throw new RedirectException(logIn);
			try {
				return new ThreadPage(webInterface, mOwnIdentity, req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, mOwnIdentity, req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
			} catch (NoSuchMessageException e) {
				return new ErrorPage(webInterface, mOwnIdentity, req, "Unknown message "+req.getParam("id"), "Unknown message "+req.getParam("id"));
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
	}
	
	class NewReplyWebInterfaceToadlet extends WebInterfaceToadlet {

		protected NewReplyWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			if(mOwnIdentity == null)
				throw new RedirectException(logIn);
			try {
				return new NewReplyPage(webInterface, mOwnIdentity, req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, mOwnIdentity, req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
			} catch (NoSuchMessageException e) {
				return new ErrorPage(webInterface, mOwnIdentity, req, "Unknown message "+req.getParam("id"), "Unknown message "+req.getParam("id"));
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
	}
	
	class NewBoardWebInterfaceToadlet extends WebInterfaceToadlet {

		protected NewBoardWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new NewBoardPage(webInterface, mOwnIdentity, req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
	}

	public WebInterface(Freetalk myFreetalk) {
		try {
			logIn = new URI(Freetalk.PLUGIN_URI+"/LogIn");
		} catch (URISyntaxException e) {
			throw new Error(e);
		}
		
		mFreetalk = myFreetalk;
		mPageMaker = mFreetalk.getPluginRespirator().getPageMaker();
		mOwnIdentity = null;
		ToadletContainer container = mFreetalk.getPluginRespirator().getToadletContainer();
		
		mPageMaker.addNavigationCategory(Freetalk.PLUGIN_URI+"/", "Freetalk", "Message boards", mFreetalk);
		
		// Visible pages
		
		container.register(homeToadlet = new HomeWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, ""), "Freetalk", Freetalk.PLUGIN_URI+"/", true, "Home", "Home page", false, null);
		container.register(messagesToadlet = new MessagesWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "messages"), "Freetalk", Freetalk.PLUGIN_URI+"/messages", true, "Boards", "View all boards", false, messagesToadlet);
		container.register(identitiesToadlet = new IdentitiesWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "identities"), "Freetalk", Freetalk.PLUGIN_URI+"/identities", true, "Identities", "Manage your own and known identities", false, identitiesToadlet);
		container.register(logOutToadlet = new LogOutWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "LogOut"), "Freetalk", Freetalk.PLUGIN_URI+"/LogOut", true, "Log out", "Log out", false, logOutToadlet);
		
		// Invisible pages
		container.register(logInToadlet = new LogInWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "LogIn"), null, Freetalk.PLUGIN_URI + "/LogIn", true, false);
		container.register(createIdentityToadlet = new CreateIdentityWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "CreateIdentity"), null, Freetalk.PLUGIN_URI + "/CreateIdentity", true, false);
		container.register(newThreadToadlet = new NewThreadWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "NewThread"), null, Freetalk.PLUGIN_URI + "/NewThread", true, false);
		container.register(showBoardToadlet = new ShowBoardWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "showBoard"), null, Freetalk.PLUGIN_URI + "/showBoard", true, false);
		container.register(showThreadToadlet = new ShowThreadWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "showThread"), null, Freetalk.PLUGIN_URI + "/showThread", true, false);
		container.register(newReplyToadlet = new NewReplyWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "NewReply"), null, Freetalk.PLUGIN_URI + "/NewReply", true, false);
		container.register(newBoardToadlet = new NewBoardWebInterfaceToadlet(null, this, mFreetalk.getPluginRespirator().getNode().clientCore, "NewBoard"), null, Freetalk.PLUGIN_URI + "/NewBoard", true, false);
	}

	private void setLoggedInOwnIdentity(FTOwnIdentity user) {
		mOwnIdentity = user;
	}
	
	private FTOwnIdentity getLoggedInOwnIdentity() {
		return mOwnIdentity;
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
	
	public void terminate() {
		ToadletContainer container = mFreetalk.getPluginRespirator().getToadletContainer();
		for(Toadlet t : new Toadlet[] { 
				homeToadlet,
				messagesToadlet,
				identitiesToadlet,
				logOutToadlet,
				logInToadlet,
				createIdentityToadlet,
				newThreadToadlet,
				showBoardToadlet,
				showThreadToadlet,
				newReplyToadlet,
				newBoardToadlet
		}) container.unregister(t);
		mPageMaker.removeNavigationCategory("Freetalk");
	}

}
