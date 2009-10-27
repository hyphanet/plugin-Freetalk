/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager.IntroductionPuzzle;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NotTrustedException;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.clients.http.RedirectException;
import freenet.clients.http.SessionManager;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContainer;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.clients.http.SessionManager.Session;
import freenet.clients.http.filter.ContentFilter;
import freenet.clients.http.filter.ContentFilter.FilterOutput;
import freenet.node.NodeClientCore;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;


/**
 * 
 * @author xor (xor@freenetproject.org)
 * @author saces
 */
public final class WebInterface {
	
	private final Freetalk mFreetalk;
	
	private final PageMaker mPageMaker;
	
	private final SessionManager mSessionManager;
	
	// Visible
	private final WebInterfaceToadlet homeToadlet;
	private final WebInterfaceToadlet subscribedBoardsToadlet;
	private final WebInterfaceToadlet selectBoardsToadlet;
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
	private final WebInterfaceToadlet changeTrustToadlet;
	private final WebInterfaceToadlet getPuzzleToadlet;
	private final WebInterfaceToadlet introduceIdentityToadlet;
	
	class HomeWebInterfaceToadlet extends WebInterfaceToadlet {

		protected HomeWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new Welcome(webInterface, getLoggedInOwnIdentity(context), req);
		}

		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
		}

	}
	
	class SubscribedBoardsWebInterfaceToadlet extends WebInterfaceToadlet {

		protected SubscribedBoardsWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new BoardsPage(webInterface, getLoggedInOwnIdentity(context), req);
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
		}
		
	}
	
	class SelectBoardsWebInterfaceToadlet extends WebInterfaceToadlet {

		protected SelectBoardsWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new SelectBoardsPage(webInterface, getLoggedInOwnIdentity(context), req);
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
		}
		
	}
	
	class IdentitiesWebInterfaceToadlet extends WebInterfaceToadlet {

		protected IdentitiesWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new IdentityEditor(webInterface, getLoggedInOwnIdentity(context), req);
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
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
			mSessionManager.deleteSession(context);
			throw new RedirectException(logIn);
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
		}
		
	}
	
	public class LogInWebInterfaceToadlet extends WebInterfaceToadlet {

		protected LogInWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		/** Log an user in from a POST and redirect to the BoardsPage */
		@Override
		public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
			String pass = request.getPartAsString("formPassword", 32);
			if ((pass.length() == 0) || !pass.equals(core.formPassword)) {
				writeHTMLReply(ctx, 403, "Forbidden", "Invalid form password.");
				return;
			}

			try {
				FTOwnIdentity ownIdentity = mFreetalk.getIdentityManager().getOwnIdentity(request.getPartAsString("OwnIdentityID", 64));
				mSessionManager.createSession(ownIdentity.getID(), ctx);
			} catch(NoSuchIdentityException e) {
				throw new RedirectException(logIn);
			}

			writeTemporaryRedirect(ctx, "Login successful, redirecting to the board overview", Freetalk.PLUGIN_URI + "/SubscribedBoards");
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());

			return new LogInPage(webInterface , req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return homeToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && !mSessionManager.sessionExists(ctx);
		}
		
	}

	public class ChangeTrustWebInterfaceToadlet extends WebInterfaceToadlet {

		protected ChangeTrustWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
			String pass = request.getPartAsString("formPassword", 32);
			if ((pass.length() == 0) || !pass.equals(core.formPassword)) {
				writeHTMLReply(ctx, 403, "Forbidden", "Invalid form password.");
				return;
			}
			try {
				// TODO: These casts are ugly.
				WoTOwnIdentity own = (WoTOwnIdentity)getLoggedInOwnIdentity(ctx);
				WoTIdentity other = (WoTIdentity)mFreetalk.getIdentityManager().getIdentity(request.getPartAsString("OtherIdentityID", 64));
				int change = Integer.parseInt(request.getPartAsString("TrustChange", 5));
				
				int trust;
				try {
					trust = own.getTrustIn(other);
				} catch (NotTrustedException e) {
					trust = 0;
				}
				own.setTrust(other, trust+change, "Freetalk web interface");

				try {
					Message message = mFreetalk.getMessageManager().get(request.getPartAsString("MessageID", 128));
					own.setAssessed(message, true);
					own.storeAndCommit();
				} catch (NoSuchMessageException e) {
				}
			} catch(Exception e) {
				// FIXME: provide error message
			}

			writeTemporaryRedirect(ctx, "Changing trust succesful, redirecting to thread", Freetalk.PLUGIN_URI + "/showThread?board=" + request.getPartAsString("BoardName", 64) + "&id=" + request.getPartAsString("ThreadID", 128));
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			// not expected to make it here
			return new Welcome(webInterface, getLoggedInOwnIdentity(context), req);
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

			try {
				return new NewThreadPage(webInterface, getLoggedInOwnIdentity(context), req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return subscribedBoardsToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
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

			try {
				return new BoardPage(webInterface, getLoggedInOwnIdentity(context), req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
				
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return subscribedBoardsToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
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

			try {
				return new ThreadPage(webInterface, getLoggedInOwnIdentity(context), req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
			} catch (NoSuchMessageException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown message "+req.getParam("id"), "Unknown message "+req.getParam("id"));
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return subscribedBoardsToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
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

			try {
				return new NewReplyPage(webInterface, getLoggedInOwnIdentity(context), req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
			} catch (NoSuchMessageException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown message "+req.getParam("id"), "Unknown message "+req.getParam("id"));
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return subscribedBoardsToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
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
			return new NewBoardPage(webInterface, getLoggedInOwnIdentity(context), req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return subscribedBoardsToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
		}
		
	}
	
	public class GetPuzzleWebInterfaceToadlet extends WebInterfaceToadlet {

		protected GetPuzzleWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {
			
			// ATTENTION: The same code is used in WoT's WebInterface.java. Please synchronize any changes which happen there.
			
			WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
			
			Bucket dataBucket = null;
			FilterOutput output = null;
			
			try {
				IntroductionPuzzle puzzle = identityManager.getIntroductionPuzzle(req.getParam("PuzzleID"));
				
				// TODO: Store the list of allowed mime types in a constant. Also consider that we might have introduction puzzles with "Type=Audio" in the future.
				if(!puzzle.MimeType.equalsIgnoreCase("image/jpeg") &&
				  	!puzzle.MimeType.equalsIgnoreCase("image/gif") && 
				  	!puzzle.MimeType.equalsIgnoreCase("image/png")) {
					
					throw new Exception("Mime type '" + puzzle.MimeType + "' not allowed for introduction puzzles.");
				}
				
				dataBucket = BucketTools.makeImmutableBucket(core.tempBucketFactory, puzzle.Data);
				output = ContentFilter.filter(dataBucket, core.tempBucketFactory, puzzle.MimeType, uri, null, null);
				writeReply(ctx, 200, output.type, "OK", output.data);
			}
			catch(Exception e) {
				sendErrorPage(ctx, 404, "Introduction puzzle not available", e.getMessage());
				Logger.error(this, "GetPuzzle failed", e);
			}
			finally {
				if(output != null)
					Closer.close(output.data);
				Closer.close(dataBucket);
			}
		}
		
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			// not expected to make it here
			return new Welcome(webInterface, getLoggedInOwnIdentity(context), req);
		}
	}
	
	class IntroduceIdentityWebInterfaceToadlet extends WebInterfaceToadlet {

		protected IntroduceIdentityWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			
			return new IntroduceIdentityPage(webInterface, (WoTOwnIdentity)webInterface.getLoggedInOwnIdentity(context), req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return introduceIdentityToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
		}
		
	}
	
	private FTOwnIdentity getLoggedInOwnIdentity(ToadletContext context) throws RedirectException {
		try {
			Session session = mSessionManager.useSession(context);
			return mFreetalk.getIdentityManager().getOwnIdentity(session.getUserID());
		} catch(NoSuchIdentityException e) { // Should not happen.
			throw new RuntimeException(e);
		}
	}
	

	public WebInterface(Freetalk myFreetalk) {
		URI myURI;
		
		try {
			myURI = new URI(Freetalk.PLUGIN_URI);
			logIn = new URI(Freetalk.PLUGIN_URI+"/LogIn");
		} catch (URISyntaxException e) {
			throw new Error(e);
		}
		
		mFreetalk = myFreetalk;
		mPageMaker = mFreetalk.getPluginRespirator().getPageMaker();

		ToadletContainer container = mFreetalk.getPluginRespirator().getToadletContainer();
		
		mSessionManager = new SessionManager(myURI, logIn);
		
		mPageMaker.addNavigationCategory(Freetalk.PLUGIN_URI+"/", "Discussion", "Message boards", mFreetalk);
		
		NodeClientCore clientCore = mFreetalk.getPluginRespirator().getNode().clientCore;
		
		// Visible pages
		logInToadlet = new LogInWebInterfaceToadlet(null, this, clientCore, "LogIn");
		homeToadlet = new HomeWebInterfaceToadlet(null, this, clientCore, "");
		subscribedBoardsToadlet = new SubscribedBoardsWebInterfaceToadlet(null, this, clientCore, "SubscribedBoards");
		selectBoardsToadlet = new SelectBoardsWebInterfaceToadlet(null, this, clientCore, "SelectBoards");
		identitiesToadlet = new IdentitiesWebInterfaceToadlet(null, this, clientCore, "identities");
		logOutToadlet = new LogOutWebInterfaceToadlet(null, this, clientCore, "LogOut");
		
		container.register(logInToadlet, "Discussion", Freetalk.PLUGIN_URI+"/", true, "Log in", "Log in", false, logInToadlet);
		container.register(homeToadlet, "Discussion", Freetalk.PLUGIN_URI+"/Home", true, "Home", "Home page", false, homeToadlet);
		container.register(subscribedBoardsToadlet, "Discussion", Freetalk.PLUGIN_URI+"/SubscribedBoards", true, "Your Boards", "View your subscribed boards", false, subscribedBoardsToadlet);
		container.register(selectBoardsToadlet, "Discussion", Freetalk.PLUGIN_URI+"/SelectBoards", true, "Select Boards", "Chose the boards which you want to read", false, selectBoardsToadlet);
		container.register(identitiesToadlet, "Discussion", Freetalk.PLUGIN_URI+"/identities", true, "Identities", "Manage your own and known identities", false, identitiesToadlet);
		container.register(logOutToadlet, "Discussion", Freetalk.PLUGIN_URI+"/LogOut", true, "Log out", "Log out", false, logOutToadlet);
		
		// Invisible pages
		createIdentityToadlet = new CreateIdentityWebInterfaceToadlet(null, this, clientCore, "CreateIdentity");
		newThreadToadlet = new NewThreadWebInterfaceToadlet(null, this, clientCore, "NewThread");
		showBoardToadlet = new ShowBoardWebInterfaceToadlet(null, this, clientCore, "showBoard");
		showThreadToadlet = new ShowThreadWebInterfaceToadlet(null, this, clientCore, "showThread");
		newReplyToadlet = new NewReplyWebInterfaceToadlet(null, this, clientCore, "NewReply");
		newBoardToadlet = new NewBoardWebInterfaceToadlet(null, this, clientCore, "NewBoard");
		changeTrustToadlet = new ChangeTrustWebInterfaceToadlet(null, this, clientCore, "ChangeTrust");
		getPuzzleToadlet = new GetPuzzleWebInterfaceToadlet(null, this, clientCore, "GetPuzzle");
		introduceIdentityToadlet = new IntroduceIdentityWebInterfaceToadlet(null, this, clientCore, "IntroduceIdentity");
		
		container.register(logInToadlet, null, Freetalk.PLUGIN_URI + "/LogIn", true, false);
		container.register(createIdentityToadlet, null, Freetalk.PLUGIN_URI + "/CreateIdentity", true, false);
		container.register(newThreadToadlet, null, Freetalk.PLUGIN_URI + "/NewThread", true, false);
		container.register(showBoardToadlet, null, Freetalk.PLUGIN_URI + "/showBoard", true, false);
		container.register(showThreadToadlet, null, Freetalk.PLUGIN_URI + "/showThread", true, false);
		container.register(newReplyToadlet, null, Freetalk.PLUGIN_URI + "/NewReply", true, false);
		container.register(newBoardToadlet, null, Freetalk.PLUGIN_URI + "/NewBoard", true, false);
		container.register(changeTrustToadlet, null, Freetalk.PLUGIN_URI + "/ChangeTrust", true, false);
		container.register(getPuzzleToadlet, null, Freetalk.PLUGIN_URI + "/GetPuzzle", true, false);
		container.register(introduceIdentityToadlet, null, Freetalk.PLUGIN_URI + "/IntroduceIdentity", true, false);
	}
	
	
	public final Freetalk getFreetalk() {
		return mFreetalk;
	}

	public final PageMaker getPageMaker() {
		return mPageMaker;
	}
	
	public void terminate() {
		ToadletContainer container = mFreetalk.getPluginRespirator().getToadletContainer();
		for(Toadlet t : new Toadlet[] { 
				homeToadlet,
				subscribedBoardsToadlet,
				selectBoardsToadlet,
				identitiesToadlet,
				logOutToadlet,
				logInToadlet,
				createIdentityToadlet,
				newThreadToadlet,
				showBoardToadlet,
				showThreadToadlet,
				newReplyToadlet,
				newBoardToadlet,
				getPuzzleToadlet,
				introduceIdentityToadlet
		}) container.unregister(t);
		mPageMaker.removeNavigationCategory("Discussion");
	}

}
