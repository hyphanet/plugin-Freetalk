/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Date;
import java.util.Iterator;

import freenet.keys.FreenetURI;

/**
 * @author saces, xor
 *
 */
public interface FMSOwnIdentity extends FMSIdentity {
	
	public FreenetURI getInsertURI();

	public Date getLastInsert();
	
	public boolean wantsMessagesFrom(FMSIdentity identity);
	
	public void postMessage(FMSMessage message);
	
	public void subscribeToBoard(FMSBoard board);
	
	public void unsubscribeFromBoard(FMSBoard board);
	
	public Iterator<FMSBoard> subscribedBoardsIterator();
}
