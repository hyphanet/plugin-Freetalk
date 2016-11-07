package plugins.Freetalk.ui.web;

import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * A page which tells the user to load the WoT plugin or upgrade it.
 * 
 * @author xor
 */
public class WoTIsMissingPage extends WebPageImpl {

	private boolean mNeedsNewWoT;
	
	public WoTIsMissingPage(WebInterface myWebInterface, HTTPRequest request, boolean wotIsOutdated, BaseL10n _baseL10n) {
		super(myWebInterface, null, request, _baseL10n);
		mNeedsNewWoT = wotIsOutdated;
	}

	@Override public void make() {
        final String[] l10nLinkSubstitutionInput = new String[] { "link", "/link" };
        final String[] l10nLinkSubstitutionOutput = new String[] { "<a href=\"/plugins\">", "</a>" };
        
        if(mNeedsNewWoT) {
            HTMLNode box = addAlertBox(l10n().getString("WoTIsMissingPage.WotOutdated.Header"));
            HTMLNode aChild = box.addChild("#");
            l10n().addL10nSubstitution(aChild, "WoTIsMissingPage.WotOutdated.Text", l10nLinkSubstitutionInput, l10nLinkSubstitutionOutput); 
        } else {
            HTMLNode box = addAlertBox(l10n().getString("WoTIsMissingPage.WotMissing.Header"));
            HTMLNode aChild = box.addChild("#");
            l10n().addL10nSubstitution(aChild, "WoTIsMissingPage.WotMissing.Text", l10nLinkSubstitutionInput, l10nLinkSubstitutionOutput);
        }
	}
}
