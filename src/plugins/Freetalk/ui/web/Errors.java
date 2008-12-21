/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public final class Errors extends WebPageImpl {
	
	private final String mErrorTitle;
	private final String mErrorMessage;

	public Errors(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request, String errorTitle, String errorMessage) {
		super(myWebInterface, viewer, request);
		mErrorTitle = errorTitle;
		mErrorMessage = errorMessage;
	}

	public final void make() {
		HTMLNode errorBox = getAlertBox(mErrorTitle);
		errorBox.addChild("#", mErrorMessage);
	}
}
