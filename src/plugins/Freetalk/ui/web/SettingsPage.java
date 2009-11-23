/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Config;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import freenet.clients.http.RedirectException;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Global and per identity Freetalk settings.
 * 
 * @author bback
 */
public class SettingsPage extends WebPageImpl {

    public SettingsPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) {
        super(myWebInterface, viewer, request);
    }

    public void make() throws RedirectException {
        if (mOwnIdentity == null) {
            throw new RedirectException(logIn);
        }
        
        if (mRequest.isPartSet("submit")) {
            
            boolean enableNntpServer = mRequest.getPartAsString("EnableNntpServer", 4).equals("true");
            synchronized (mFreetalk.getConfig()) {
                mFreetalk.getConfig().set(Config.NNTP_SERVER_ENABLED, enableNntpServer);
                mFreetalk.getConfig().storeAndCommit();
            }
            
            boolean autoSubscribeBoards = mRequest.getPartAsString("AutoSubscribeBoards", 4).equals("true");
            synchronized (mOwnIdentity) {
                mOwnIdentity.setNntpAutoSubscribeBoards(autoSubscribeBoards);
                ((WoTOwnIdentity) mOwnIdentity).storeAndCommit(); // FIXME: remove ugly cast
            }
            
            HTMLNode aBox = addContentBox(Freetalk.getBaseL10n().getString("SettingsPage.SettingsSaved.Header"));
            aBox.addChild("p", Freetalk.getBaseL10n().getString("SettingsPage.SettingsSaved.Text"));
        }

        HTMLNode settingsBox = addContentBox(Freetalk.getBaseL10n().getString("SettingsPage.SettingsBox.Header"));
        HTMLNode formNode = addFormChild(settingsBox, Freetalk.PLUGIN_URI + "/Settings", "Settings");

        makeOwnSettingsBox(formNode);
        makeGlobalSettingsBox(formNode);
        
        formNode.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "submit", Freetalk.getBaseL10n().getString("SettingsPage.SaveButton")});
    }

    private final void makeOwnSettingsBox(HTMLNode formNode) {

        formNode.addChild("div", "class", "configprefix", Freetalk.getBaseL10n().getString("SettingsPage.UserSettings.Header"));
        
        HTMLNode list = formNode.addChild("ul", "class", "config");
        HTMLNode item = list.addChild("li");
        
        /* *** NNTP - auto-subscribe to boards ********************************************* */

        item.addChild("span", new String[]{ "class", "title", "style" },
                new String[]{ "configshortdesc", booleanDefaultString(false), "cursor: help;" })
                .addChild("#", Freetalk.getBaseL10n().getString("SettingsPage.UserSettings.NNTPAutoSubscribeBoards.Short"));
        
        item.addChild("span", "class", "config");
        item.addChild(addBooleanComboBox(mOwnIdentity.nntpAutoSubscribeBoards(), "AutoSubscribeBoards", false));
        
        item.addChild("span", "class", "configlongdesc").addChild("#", Freetalk.getBaseL10n().getString("SettingsPage.UserSettings.NNTPAutoSubscribeBoards.Long"));
    }

    private final void makeGlobalSettingsBox(HTMLNode formNode) {

        formNode.addChild("div", "class", "configprefix", Freetalk.getBaseL10n().getString("SettingsPage.GlobalSettings.Header"));
        
        HTMLNode list = formNode.addChild("ul", "class", "config");
        HTMLNode item = list.addChild("li");
        
        /* *** NNTP Server enabled ********************************************* */
        
        item.addChild("span", new String[]{ "class", "title", "style" },
                new String[]{ "configshortdesc", booleanDefaultString(true), "cursor: help;" })
                .addChild("#", Freetalk.getBaseL10n().getString("SettingsPage.GlobalSettings.NNTPEnableServer.Short"));
        
        item.addChild("span", "class", "config");
        item.addChild(addBooleanComboBox(mFreetalk.getConfig().getBoolean(Config.NNTP_SERVER_ENABLED), "EnableNntpServer", false));
        
        item.addChild("span", "class", "configlongdesc").addChild("#", Freetalk.getBaseL10n().getString("SettingsPage.GlobalSettings.NNTPEnableServer.Long"));
    }
    
    private String booleanDefaultString(boolean value) {
        return NodeL10n.getBase().getString("ConfigToadlet.defaultIs", new String[] { "default" }, new String[] { value ? nodesL10n("true") : nodesL10n("false") });
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
}
