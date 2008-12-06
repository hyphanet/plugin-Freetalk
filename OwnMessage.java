/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

import freenet.keys.FreenetURI;

public class OwnMessage extends Message {
	
	private boolean iWasInserted = false;
	
	private static final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");

	public OwnMessage(FreenetURI newRequestURI, Message newParentThread, Message newParentMessage, Set<Board> newBoards, FTOwnIdentity newAuthor, String newTitle,
			Date newDate, int newIndex, String newText, List<Attachment> newAttachments) {
		super(newRequestURI, newParentThread.getURI(), newParentMessage.getURI(), newBoards, newAuthor, newTitle, newDate, newIndex, newText, newAttachments);
	}

	public FreenetURI getInsertURI() {
		String dayOfInsertion;
		synchronized (mDateFormat) {
			dayOfInsertion = mDateFormat.format(mDate);
		}
		FTOwnIdentity author = (FTOwnIdentity)mAuthor;
		FreenetURI baseURI = author.getInsertURI().setKeyType("SSK");
		baseURI = baseURI.setDocName(Freetalk.PLUGIN_TITLE + "|" + "Message" + "|" + dayOfInsertion + "-" + mIndex + ".xml");
		return baseURI.setMetaString(null);
	}
	
	public synchronized boolean wasInserted() {
		return iWasInserted;
	}
	
	public synchronized void markAsInserted() {
		iWasInserted = true;
	}

}
