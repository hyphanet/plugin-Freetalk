/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Iterator;

import freenet.keys.FreenetURI;

/**
 * @author saces, xor
 *
 */
public interface FTOwnIdentity extends FTIdentity {
	
	public FreenetURI getInsertURI();
	
	public boolean wantsMessagesFrom(FTIdentity identity);
	
	public void subscribeToBoard(Board board);
	
	public void unsubscribeFromBoard(Board board);
	
	public Iterator<Board> subscribedBoardsIterator();
	
}
