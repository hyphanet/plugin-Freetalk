/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import freenet.l10n.BaseL10n;
import freenet.support.api.HTTPRequest;

public abstract class TaskPage extends WebPageImpl {

	protected final String mTaskID;

	public TaskPage(WebInterface myWebInterface, FTOwnIdentity myViewer, HTTPRequest request, BaseL10n _baseL10n) {
		super(myWebInterface, myViewer, request, _baseL10n);
		
		mTaskID = mRequest.getPartAsString("TaskID", 64);
	}

	public TaskPage(WebInterface myWebInterface, FTOwnIdentity myViewer, String myTaskID, BaseL10n _baseL10n) {
		super(myWebInterface, myViewer, null, _baseL10n);
		mTaskID = myTaskID;
	}
}