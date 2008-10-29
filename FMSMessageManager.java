/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Iterator;

/**
 * @author xor
 *
 */
public interface FMSMessageManager {
	
	public FMSBoard getBoardByName(String name);
	
	/**
	 * Get an iterator of boards which the identity could subscribe to.
	 * @param identity
	 * @return
	 */
	public Iterator<FMSBoard> boardIterator(FMSOwnIdentity identity);
}
