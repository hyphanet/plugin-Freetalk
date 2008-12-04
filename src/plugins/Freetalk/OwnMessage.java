/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.List;
import java.util.Set;

import freenet.keys.FreenetURI;

public class OwnMessage extends Message {
	
	private boolean iWasInserted = false;

	public OwnMessage(FreenetURI newURI, FreenetURI newThreadURI, FreenetURI newParentURI, Set<Board> newBoards, FTOwnIdentity newAuthor,
			String newTitle, Date newDate, String newText, List<FreenetURI> newAttachments) {
		super(newURI, newThreadURI, newParentURI, newBoards, newAuthor, newTitle, newDate, newText, newAttachments);
	}
	
	public FreenetURI getInsertURI() {
		return null;
	}
	
	public synchronized boolean wasInserted() {
		return iWasInserted;
	}
	
	public synchronized void markAsInserted() {
		iWasInserted = true;
	}

}
