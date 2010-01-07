/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public final class ErrorPage extends WebPageImpl {
	
	private final String mErrorTitle;
	private final String mErrorMessage;

	public ErrorPage(WebInterface webInterface, FTOwnIdentity viewer, HTTPRequest request, String errorTitle, String errorMessage, BaseL10n _baseL10n) {
		super(webInterface, viewer, request, _baseL10n);
		mErrorTitle = errorTitle;
		mErrorMessage = errorMessage;
		Logger.error(this, "Internal error: " + errorTitle + ", " + errorMessage);
	}

	public final void make() {
		HTMLNode errorBox = addAlertBox(mErrorTitle);
		errorBox.addChild("#", mErrorMessage);
	}
}
