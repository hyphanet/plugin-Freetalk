/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.Iterator;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;

/**
 * @author saces, xor
 *
 */
public interface FTOwnIdentity extends FTIdentity {
	
	public FreenetURI getInsertURI();
	
	public boolean wantsMessagesFrom(FTIdentity identity);
	
	public void postMessage(ObjectContainer db, FTMessage message);
	
	public void subscribeToBoard(ObjectContainer db, FTBoard board);
	
	public void unsubscribeFromBoard(ObjectContainer db, FTBoard board);
	
	public Iterator<FTBoard> subscribedBoardsIterator();
}
