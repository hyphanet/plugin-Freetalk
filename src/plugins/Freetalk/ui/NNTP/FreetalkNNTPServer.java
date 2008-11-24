/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.FTIdentityManager;
import plugins.Freetalk.FTMessageManager;
import plugins.Freetalk.Freetalk;


/**
 * How to implement this:
 * - Getting a board list: mMessageManager.boardIterator()
 * - Getting messages in a board: mMessageManager.threadIterator()
 * - Getting replies to a message: message.childrenIterator()
 * - Things which might be missing: Functions in FTMessage and FTidentity for getting UIDs which are compatible to NNTP, plus functions
 * in FTMessageManager/FTIdentityManager for retrieving by those UIDs. Ask for them and they will be implemented.
 */
public class FreetalkNNTPServer {

	private FTIdentityManager mIdentityManager;
	
	private FTMessageManager mMessageManager;

	public FreetalkNNTPServer(Freetalk ft) {
		mIdentityManager = ft.getIdentityManager();
		mMessageManager = ft.getMessageManager();
	}
}
