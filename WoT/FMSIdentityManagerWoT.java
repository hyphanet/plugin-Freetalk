/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import plugins.FMSPlugin.FMSIdentityManager;
import plugins.WoT.WoT;

import com.db4o.ObjectContainer;

import freenet.support.Executor;

/**
 * An identity manager which uses the identities from the WoT plugin.
 * 
 * @author xor
 *
 */
public class FMSIdentityManagerWoT extends FMSIdentityManager {
	
	private WoT mWoT;

	/**
	 * @param executor
	 */
	public FMSIdentityManagerWoT(ObjectContainer myDB, Executor executor, WoT newWoT) {
		super(myDB, executor);
		mWoT = newWoT;
	}
}
