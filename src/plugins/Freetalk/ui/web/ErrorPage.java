/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import plugins.Freetalk.OwnIdentity;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public final class ErrorPage extends WebPageImpl {
	
	private final String mErrorTitle;
	private final String mErrorMessage;

	public ErrorPage(WebInterface webInterface, OwnIdentity viewer, HTTPRequest request, String errorTitle, Throwable t, BaseL10n _baseL10n) {
		super(webInterface, viewer, request, _baseL10n);
		mErrorTitle = errorTitle;
		
		String errorMessage = t != null ? t.getLocalizedMessage() : null;
		if(errorMessage == null || errorMessage.equals("")) {
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			final PrintStream ps = new PrintStream(bos);
			t.printStackTrace(ps);
			errorMessage = bos.toString();
			ps.close();
		}
		
		mErrorMessage = errorMessage;
		Logger.error(this, "Internal error", t);
	}

	@Override public final void make() {
		HTMLNode errorBox = addAlertBox(mErrorTitle);
		errorBox.addChild("#", mErrorMessage);
	}
}
