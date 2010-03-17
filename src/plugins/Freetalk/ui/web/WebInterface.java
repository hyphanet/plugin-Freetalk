/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessage;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager.IntroductionPuzzle;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
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
import freenet.l10n.BaseL10n;
import freenet.node.NodeClientCore;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;


/**
 * @author xor (xor@freenetproject.org)
 * @author saces
 * @author bback
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
	private final WebInterfaceToadlet settingsToadlet;
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
	private final WebInterfaceToadlet cssToadlet;

	/**
	 * Forward current l10n data.
	 * 
	 * @return current l10n data
	 */
	public BaseL10n l10n() {
	    return mFreetalk.getBaseL10n();
	}
	
	class HomeWebInterfaceToadlet extends WebInterfaceToadlet {

		protected HomeWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
			
			if(!mSessionManager.sessionExists(context))
				throw new RedirectException(logIn);
			
			return new Welcome(webInterface, getLoggedInOwnIdentity(context), req, l10n());
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
			return new BoardsPage(webInterface, getLoggedInOwnIdentity(context), req, l10n());
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
			return new SelectBoardsPage(webInterface, getLoggedInOwnIdentity(context), req, l10n());
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
			return new IdentityEditor(webInterface, getLoggedInOwnIdentity(context), req, l10n());
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
		}
	}
	
	class SettingsWebInterfaceToadlet extends WebInterfaceToadlet {
	    
	    protected SettingsWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
	        super(client, wi, core, pageTitle);
	    }
	    
	    @Override
	    WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
	        if(!mFreetalk.wotConnected())
	            return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
	        return new SettingsPage(webInterface, getLoggedInOwnIdentity(context), req, l10n());
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
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

			writeTemporaryRedirect(ctx, "Login successful, redirecting to home page", Freetalk.PLUGIN_URI + "/");
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());

			return new LogInPage(webInterface , req, l10n());
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
		WebPage makeWebPage(HTTPRequest request, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, request, mFreetalk.wotOutdated(), l10n());

			// TODO: These casts are ugly.
			final WoTOwnIdentity own = (WoTOwnIdentity)webInterface.getLoggedInOwnIdentity(context);
			WebPage errorPage = null;
			
			try {
				WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
				WoTMessageManager messageManager = (WoTMessageManager)mFreetalk.getMessageManager();
				
				synchronized(identityManager) {
					boolean removeRating = request.getPartAsString("RemoveRating", 16).equals("true");
					
					try {						
						synchronized (messageManager) {
						synchronized(own) {
							WoTMessage message = (WoTMessage)messageManager.get(request.getPartAsString("MessageID", 128));
							if(removeRating)
								messageManager.deleteMessageRating(messageManager.getMessageRating(own, message));
							else
								messageManager.rateMessage(own, message, Byte.parseByte(request.getPartAsString("TrustChange", 5)));
						}
						}
					} catch (NoSuchMessageException e) {
						errorPage = new ErrorPage(webInterface, own, request, "Rating the message failed", e, l10n());
					}
					
				}
			} catch(Exception e) {
				errorPage = new ErrorPage(webInterface, webInterface.getLoggedInOwnIdentity(context), request, "Rating the message failed", e, l10n());
			}
			try {
				// FIXME: The current WebPageImpl does not support adding one page to another page outside of make() !
				if(errorPage != null)
					return  errorPage;
				
				WebPage result = new ThreadPage(webInterface, own, request, l10n());
				return result;
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return homeToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && mSessionManager.sessionExists(ctx);
		}
	}
	
	class CreateIdentityWebInterfaceToadlet extends WebInterfaceToadlet {

		protected CreateIdentityWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
			return new CreateIdentityWizard(webInterface, req, l10n());
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());

			try {
				return new NewThreadPage(webInterface, getLoggedInOwnIdentity(context), req, l10n());
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown board "+req.getParam("name"), e, l10n());
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());

			try {
				return new BoardPage(webInterface, getLoggedInOwnIdentity(context), req, l10n());
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown board "+req.getParam("name"), e, l10n());
				
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());

			try {
				return new ThreadPage(webInterface, getLoggedInOwnIdentity(context), req, l10n());
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown board "+req.getParam("name"), e, l10n());
			} catch (NoSuchMessageException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown message "+req.getParam("id"), e, l10n());
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());

			try {
				return new NewReplyPage(webInterface, getLoggedInOwnIdentity(context), req, l10n());
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown board "+req.getParam("name"), e, l10n());
			} catch (NoSuchMessageException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(context), req, "Unknown message "+req.getParam("id"), e, l10n());
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
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
			return new NewBoardPage(webInterface, getLoggedInOwnIdentity(context), req, l10n());
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
			return new Welcome(webInterface, getLoggedInOwnIdentity(context), req, l10n());
		}
	}
	
	class IntroduceIdentityWebInterfaceToadlet extends WebInterfaceToadlet {

		protected IntroduceIdentityWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated(), l10n());
			
			return new IntroduceIdentityPage(webInterface, (WoTOwnIdentity)webInterface.getLoggedInOwnIdentity(context), req, l10n());
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
	/**
	 * Web interface toadlet that delivers CSS files from the “css” path
	 * relative to this source file.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public class CSSWebInterfaceToadlet extends WebInterfaceToadlet {

		/**
		 * Creates a new CSS web interface toadlet.
		 *
		 * @param highLevelSimpleClient
		 *            The high-level simple client
		 * @param webInterface
		 *            The web interface
		 * @param nodeClientCore
		 *            The node client core
		 * @param pageTitle
		 *            The title of the page
		 */
		protected CSSWebInterfaceToadlet(HighLevelSimpleClient highLevelSimpleClient, WebInterface webInterface, NodeClientCore nodeClientCore, String pageTitle) {
			super(highLevelSimpleClient, webInterface, nodeClientCore, pageTitle);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void handleMethodGET(URI uri, HTTPRequest httpRequest, ToadletContext context) throws ToadletContextClosedException, IOException, RedirectException {
			InputStream cssInputStream = null;
			ByteArrayOutputStream cssBufferOutputStream = null;
			Bucket cssBucket = null;
			byte[] cssBuffer = new byte[0];
			try {
				String cssFilename = uri.getPath();
				cssFilename = cssFilename.substring((Freetalk.PLUGIN_URI + "/css/").length());
				URLConnection cssUrlConnection = getClass().getResource("/plugins/Freetalk/ui/web/css/" + cssFilename).openConnection();
				cssUrlConnection.setUseCaches(false);
				cssInputStream = cssUrlConnection.getInputStream();
				if (cssInputStream != null) {
					cssBufferOutputStream = new ByteArrayOutputStream();
					byte[] buffer = new byte[65536];
					int read;
					while ((read = cssInputStream.read(buffer)) != -1) {
						cssBufferOutputStream.write(buffer, 0, read);
					}
					cssBuffer = cssBufferOutputStream.toByteArray();
				}
				cssBucket = BucketTools.makeImmutableBucket(core.tempBucketFactory, cssBuffer);
				FilterOutput filterOutput = ContentFilter.filter(cssBucket, core.tempBucketFactory, "text/css", uri, null, null);
				writeReply(context, 200, "text/css", "OK", cssBucket);
			} finally {
				Closer.close(cssBucket);
				Closer.close(cssInputStream);
				Closer.close(cssBufferOutputStream);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		WebPage makeWebPage(HTTPRequest httpRequest, ToadletContext context) {
			/* will not be reached. */
			return null;
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
		
		mPageMaker.addNavigationCategory(Freetalk.PLUGIN_URI+"/", "WebInterface.DiscussionMenuName", "WebInterface.DiscussionMenuName.Tooltip", mFreetalk, 1);
		
		NodeClientCore clientCore = mFreetalk.getPluginRespirator().getNode().clientCore;
		
		// Visible pages
		homeToadlet = new HomeWebInterfaceToadlet(null, this, clientCore, "");
		logInToadlet = new LogInWebInterfaceToadlet(null, this, clientCore, "LogIn");
		subscribedBoardsToadlet = new SubscribedBoardsWebInterfaceToadlet(null, this, clientCore, "SubscribedBoards");
		selectBoardsToadlet = new SelectBoardsWebInterfaceToadlet(null, this, clientCore, "SelectBoards");
		identitiesToadlet = new IdentitiesWebInterfaceToadlet(null, this, clientCore, "identities");
		settingsToadlet = new SettingsWebInterfaceToadlet(null, this, clientCore, "Settings");
		logOutToadlet = new LogOutWebInterfaceToadlet(null, this, clientCore, "LogOut");

		container.register(homeToadlet, "WebInterface.DiscussionMenuName", Freetalk.PLUGIN_URI+"/", true, "WebInterface.DiscussionMenuItem.Home", "WebInterface.DiscussionMenuItem.Home.Tooltip", false, homeToadlet);
		container.register(logInToadlet, "WebInterface.DiscussionMenuName", Freetalk.PLUGIN_URI+"/LogIn", true, "WebInterface.DiscussionMenuItem.LogIn", "WebInterface.DiscussionMenuItem.LogIn.Tooltip", false, logInToadlet);
		container.register(subscribedBoardsToadlet, "WebInterface.DiscussionMenuName", Freetalk.PLUGIN_URI+"/SubscribedBoards", true, "WebInterface.DiscussionMenuItem.SubscribedBoards", "WebInterface.DiscussionMenuItem.SubscribedBoards.Tooltip", false, subscribedBoardsToadlet);
		container.register(selectBoardsToadlet, "WebInterface.DiscussionMenuName", Freetalk.PLUGIN_URI+"/SelectBoards", true, "WebInterface.DiscussionMenuItem.SelectBoards", "WebInterface.DiscussionMenuItem.SelectBoards.Tooltip", false, selectBoardsToadlet);
		container.register(identitiesToadlet, "WebInterface.DiscussionMenuName", Freetalk.PLUGIN_URI+"/identities", true, "WebInterface.DiscussionMenuItem.Identities", "WebInterface.DiscussionMenuItem.Identities.Tooltip", false, identitiesToadlet);
		container.register(settingsToadlet, "WebInterface.DiscussionMenuName", Freetalk.PLUGIN_URI+"/Settings", true, "WebInterface.DiscussionMenuItem.Settings", "WebInterface.DiscussionMenuItem.Settings.Tooltip", false, settingsToadlet);
		container.register(logOutToadlet, "WebInterface.DiscussionMenuName", Freetalk.PLUGIN_URI+"/LogOut", true, "WebInterface.DiscussionMenuItem.LogOut", "WebInterface.DiscussionMenuItem.LogOut.Tooltip", false, logOutToadlet);
		
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
		cssToadlet = new CSSWebInterfaceToadlet(null, this, clientCore, "CSS");
		
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
		container.register(cssToadlet, null, Freetalk.PLUGIN_URI + "/css/", true, false);
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
				settingsToadlet,
				logOutToadlet,
				logInToadlet,
				createIdentityToadlet,
				newThreadToadlet,
				showBoardToadlet,
				showThreadToadlet,
				newReplyToadlet,
				newBoardToadlet,
				getPuzzleToadlet,
				introduceIdentityToadlet,
				cssToadlet,
		}) container.unregister(t);
		mPageMaker.removeNavigationCategory("WebInterface.DiscussionMenuName");
	}
}
