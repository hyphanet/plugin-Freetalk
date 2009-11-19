package plugins.Freetalk.ui.web;

import plugins.Freetalk.Freetalk;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * A page which tells the user to load the WoT plugin or upgrade it.
 * 
 * @author xor
 */
public class WoTIsMissingPage extends WebPageImpl {

	private boolean mNeedsNewWoT;
	
	public WoTIsMissingPage(WebInterface myWebInterface, HTTPRequest request, boolean wotIsOutdated) {
		super(myWebInterface, null, request);
		mNeedsNewWoT = wotIsOutdated;
	}

	public void make() {
        final String[] l10nLinkSubstitutionInput = new String[] { "link", "/link" };
        final String[] l10nLinkSubstitutionOutput = new String[] { "<a href=\"/plugins\">", "</a>" };
        
        if(mNeedsNewWoT) {
            HTMLNode box = addAlertBox(Freetalk.getBaseL10n().getString("WoTIsMissingPage.WotOutdated.Header"));
            HTMLNode aChild = box.addChild("#");
            Freetalk.getBaseL10n().addL10nSubstitution(aChild, "WoTIsMissingPage.WotOutdated.Text", l10nLinkSubstitutionInput, l10nLinkSubstitutionOutput);
            // FIXME: add "by going to the plugins page and pressing 'Reload'" after we have WoT in the official plugins list 
        } else {
            HTMLNode box = addAlertBox(Freetalk.getBaseL10n().getString("WoTIsMissingPage.WotMissing.Header"));
            HTMLNode aChild = box.addChild("#");
            Freetalk.getBaseL10n().addL10nSubstitution(aChild, "WoTIsMissingPage.WotMissing.Text", l10nLinkSubstitutionInput, l10nLinkSubstitutionOutput);
        }
	}
}
