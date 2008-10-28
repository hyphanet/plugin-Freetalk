/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.WoT;

import java.util.Iterator;

import plugins.FMSPlugin.FMSBoard;
import plugins.FMSPlugin.FMSIdentityManager;
import plugins.FMSPlugin.FMSMessageManager;
import plugins.FMSPlugin.FMSOwnIdentity;

public class FMSMessageManagerWoT extends FMSMessageManager {

	public FMSMessageManagerWoT(FMSIdentityManager newIdentityManager) {
		super(newIdentityManager);
		// TODO Auto-generated constructor stub
	}

	@Override
	public FMSBoard getBoardByName(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<FMSBoard> iterator(FMSOwnIdentity identity) {
		// TODO Auto-generated method stub
		return null;
	}

}
