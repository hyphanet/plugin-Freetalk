/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

/**
 * @author xor
 *
 */
public abstract class FMSMessageManager implements Iterable<FMSBoard> {

	private FMSIdentityManager mIdentityManager;
	
	public FMSMessageManager(FMSIdentityManager newIdentityManager) {
		mIdentityManager = newIdentityManager;
	}
	
	public abstract FMSBoard getBoardByName(String name);  
}
