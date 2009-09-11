/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.FTOwnIdentity;
import freenet.support.api.HTTPRequest;

public abstract class TaskPage extends WebPageImpl {

	protected final String mTaskID;

	public TaskPage(WebInterface myWebInterface, FTOwnIdentity myViewer, HTTPRequest request) {
		super(myWebInterface, myViewer, request);
		
		mTaskID = mRequest.getPartAsString("TaskID", 64);
	}

	public TaskPage(WebInterface myWebInterface, FTOwnIdentity myViewer, String myTaskID) {
		super(myWebInterface, myViewer, null);
		mTaskID = myTaskID;
	}

}