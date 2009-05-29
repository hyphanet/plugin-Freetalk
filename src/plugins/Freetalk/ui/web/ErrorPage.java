/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class ErrorPage extends WebPageImpl {
	
	private final String mErrorTitle;
	private final String mErrorMessage;

	public ErrorPage(WebInterface webInterface, FTOwnIdentity viewer, HTTPRequest request, String errorTitle, String errorMessage) {
		super(webInterface, viewer, request);
		mErrorTitle = errorTitle;
		mErrorMessage = errorMessage;
	}

	public final void make() {
		HTMLNode errorBox = addAlertBox(mErrorTitle);
		errorBox.addChild("#", mErrorMessage);
	}
}
