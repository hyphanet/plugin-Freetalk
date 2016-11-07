/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Configuration;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Global and per identity Freetalk settings.
 * 
 * @author bback
 * @author xor (xor@freenetproject.org)
 */
public class SettingsPage extends WebPageImpl {

    public SettingsPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n) {
        super(myWebInterface, viewer, request, _baseL10n);
    }

    @Override public void make() throws RedirectException {
        if (mOwnIdentity == null) {
            throw new RedirectException(logIn);
        }
        
        makeBreadcrumbs();
        
        if (mRequest.isPartSet("submit") && mRequest.getMethod().equals("POST")) {
            
            boolean enableNntpServer = mRequest.getPartAsStringFailsafe("EnableNntpServer", 4).equals("true");
            String nntpServerBindTo = mRequest.getPartAsStringFailsafe("nntpServerBindTo", 1024);
			if ("127.0.0.1".equals(nntpServerBindTo)) {
				nntpServerBindTo = null;
			}
			String nntpServerAllowedHosts = mRequest.getPartAsStringFailsafe("nntpServerAllowedHosts", 1024);
			if ("127.0.0.1".equals(nntpServerAllowedHosts)) {
				nntpServerAllowedHosts = null;
			}
            synchronized (mFreetalk.getConfig()) {
                mFreetalk.getConfig().set(Configuration.NNTP_SERVER_ENABLED, enableNntpServer);
                mFreetalk.getConfig().set(Configuration.NNTP_SERVER_BINDTO, nntpServerBindTo);
                mFreetalk.getConfig().set(Configuration.NNTP_SERVER_ALLOWED_HOSTS, nntpServerAllowedHosts);
                mFreetalk.getConfig().storeAndCommit();
            }
            
            boolean autoSubscribeToNewBoards = mRequest.getPartAsStringFailsafe("AutoSubscribeToNewBoards", 4).equals("true");
            
            boolean autoSubscribeToNNTPBoards = mRequest.getPartAsStringFailsafe("AutoSubscribeToNNTPBoards", 4).equals("true");
            
            boolean allowImageDisplay = mRequest.getPartAsStringFailsafe("DisplayImages", 4).equals("true");
            
            WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
            
            synchronized(identityManager) {
            	try {
            		// Ensure that the identity still exists.
	            	WoTOwnIdentity identity = identityManager.getOwnIdentity(mOwnIdentity.getID());
	            	
	            	synchronized (identity) {
	            		identity.setAutoSubscribeToNewboards(autoSubscribeToNewBoards);
	            		identity.setNntpAutoSubscribeBoards(autoSubscribeToNNTPBoards);
	            		identity.setWantsImageDisplay(allowImageDisplay);
	            		identity.storeAndCommit();
	            	}
            	} catch(Exception e) {
            		new ErrorPage(mWebInterface, mOwnIdentity, mRequest, "Setting own identity options failed", e,
            				l10n()).addToPage(mContentNode);
            	}
            }
            
            HTMLNode aBox = addContentBox(l10n().getString("SettingsPage.SettingsSaved.Header"));
            aBox.addChild("p", l10n().getString("SettingsPage.SettingsSaved.Text"));
        }

        HTMLNode settingsBox = addContentBox(l10n().getString("SettingsPage.SettingsBox.Header"));
        HTMLNode formNode = addFormChild(settingsBox, Freetalk.PLUGIN_URI + "/Settings", "Settings");

        makeOwnSettingsBox(formNode);
        makeGlobalSettingsBox(formNode);
        
        formNode.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", l10n().getString("SettingsPage.SaveButton")});
    }

    private final void makeOwnSettingsBox(HTMLNode formNode) {

        formNode.addChild("div", "class", "configprefix", l10n().getString("SettingsPage.UserSettings.Header"));
        
        HTMLNode list = formNode.addChild("ul", "class", "config");
        HTMLNode item = list.addChild("li");
        
        /* *** Auto-subscribe to new boards ********************************************* */

        item.addChild("span", new String[]{ "class", "title", "style" },
                new String[]{ "configshortdesc", booleanDefaultString(false), "cursor: help;" })
                .addChild("#", l10n().getString("SettingsPage.UserSettings.AutoSubscribeToNewBoards.Short"));
        
        item.addChild("span", "class", "config");
        item.addChild(addBooleanComboBox(mOwnIdentity.wantsAutoSubscribeToNewBoards(), "AutoSubscribeToNewBoards", false));
        
        item.addChild("span", "class", "configlongdesc").addChild("#", l10n().getString("SettingsPage.UserSettings.AutoSubscribeToNewBoards.Long"));
        
        /* *** NNTP - auto-subscribe to boards ********************************************* */
        
        item = list.addChild("li");
        
        item.addChild("span", new String[]{ "class", "title", "style" },
                new String[]{ "configshortdesc", booleanDefaultString(false), "cursor: help;" })
                .addChild("#", l10n().getString("SettingsPage.UserSettings.NNTPAutoSubscribeBoards.Short"));
        
        item.addChild("span", "class", "config");
        item.addChild(addBooleanComboBox(mOwnIdentity.nntpAutoSubscribeBoards(), "AutoSubscribeToNNTPBoards", false));
        
        item.addChild("span", "class", "configlongdesc").addChild("#", l10n().getString("SettingsPage.UserSettings.NNTPAutoSubscribeBoards.Long"));
        
        /* *** Image display ********************************************* */
        
        item = list.addChild("li");
        
        item.addChild("span", new String[]{ "class", "title", "style" },
                new String[]{ "configshortdesc", booleanDefaultString(false), "cursor: help;" })
                .addChild("#", l10n().getString("SettingsPage.UserSettings.AllowImageDisplay.Short"));
        
        item.addChild("span", "class", "config");
        item.addChild(addBooleanComboBox(mOwnIdentity.wantsImageDisplay(), "DisplayImages", false));
        
        item.addChild("span", "class", "configlongdesc").addChild("#", l10n().getString("SettingsPage.UserSettings.AllowImageDisplay.Long"));
    }

    private final void makeGlobalSettingsBox(HTMLNode formNode) {

        formNode.addChild("div", "class", "configprefix", l10n().getString("SettingsPage.GlobalSettings.Header"));
        
        HTMLNode list = formNode.addChild("ul", "class", "config");
        HTMLNode item = list.addChild("li");
        
        /* *** NNTP Server enabled ********************************************* */
        
        item.addChild("span", new String[]{ "class", "title", "style" },
                new String[]{ "configshortdesc", booleanDefaultString(true), "cursor: help;" })
                .addChild("#", l10n().getString("SettingsPage.GlobalSettings.NNTPEnableServer.Short"));
        
        item.addChild("span", "class", "config");
        item.addChild(addBooleanComboBox(mFreetalk.getConfig().getBoolean(Configuration.NNTP_SERVER_ENABLED), "EnableNntpServer", false));
        
        item.addChild("span", "class", "configlongdesc").addChild("#", l10n().getString("SettingsPage.GlobalSettings.NNTPEnableServer.Long"));

		item = list.addChild("li");
		item.addChild("span", new String[] { "class", "title", "style" }, new String[] { "configshortdesc", defaultString("127.0.0.1"), "cursor: help;" }).addChild("#", l10n().getString("SettingsPage.GlobalSettings.NNTPBindTo.Short"));
		String currentValue = mFreetalk.getConfig().getString(Configuration.NNTP_SERVER_BINDTO);
		if (currentValue == null) {
			currentValue = "127.0.0.1";
		}
		item.addChild("input", new String[] { "type", "name", "value" }, new String[] { "text", "nntpServerBindTo", currentValue });
		item.addChild("span", "class", "configlongdesc", l10n().getString("SettingsPage.GlobalSettings.NNTPBindTo.Long"));

		item = list.addChild("li");
		item.addChild("span", new String[] { "class", "title", "style" }, new String[] { "configshortdesc", defaultString("127.0.0.1"), "cursor: help;" }, l10n().getString("SettingsPage.GlobalSettings.NNTPAllowedHosts.Short"));
		String allowedHosts = mFreetalk.getConfig().getString(Configuration.NNTP_SERVER_ALLOWED_HOSTS);
		if (allowedHosts == null) {
			allowedHosts = "127.0.0.1";
		}
		item.addChild("input", new String[] { "type", "name", "value" }, new String[] { "text", "nntpServerAllowedHosts", allowedHosts });
		item.addChild("span", "class", "configlongdesc", l10n().getString("SettingsPage.GlobalSettings.NNTPAllowedHosts.Long"));
    }

	private String booleanDefaultString(boolean value) {
		return defaultString(value ? nodesL10n("true") : nodesL10n("false"));
	}

	private String defaultString(String value) {
		return NodeL10n.getBase().getString("ConfigToadlet.defaultIs", "default", value);
	}

    private static final String nodesL10n(String string) {
        return NodeL10n.getBase().getString("ConfigToadlet." + string);
    }

    private HTMLNode addBooleanComboBox(boolean value, String name, boolean disabled) {
        HTMLNode result;
        if (disabled)
            result = new HTMLNode("select",
                    new String[] { "name", "disabled" },
                    new String[] { name, "disabled" });
        else
            result = new HTMLNode("select", "name", name);

        if (value) {
            result.addChild("option", new String[] { "value", "selected" }, new String[] { "true", "selected" }, nodesL10n("true"));
            result.addChild("option", "value", "false", nodesL10n("false"));
        } else {
            result.addChild("option", "value", "true", nodesL10n("true"));
            result.addChild("option", new String[] { "value", "selected" }, new String[] { "false", "selected" }, nodesL10n("false"));
        }
        
        return result;
    }
    
    private void makeBreadcrumbs() {
        BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
        Welcome.addBreadcrumb(trail);
        SettingsPage.addBreadcrumb(trail);
        mContentNode.addChild(trail.getHTMLNode());
    }

    public static void addBreadcrumb(BreadcrumbTrail trail) {
        trail.addBreadcrumbInfo(trail.getL10n().getString("Breadcrumb.Settings"), Freetalk.PLUGIN_URI + "/Settings");
    }
}
