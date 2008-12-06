/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

import freenet.keys.FreenetURI;

public final class OwnMessage extends Message {
	
	private boolean iWasInserted = false;
	
	private static final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");

	public OwnMessage(Message newParentThread, Message newParentMessage, Set<Board> newBoards, Board newReplyToBoard, FTOwnIdentity newAuthor, String newTitle,
			Date newDate, int newIndex, String newText, List<Attachment> newAttachments) {
		super(generateRequestURI(newAuthor, newDate, newIndex), newParentThread.getURI(), newParentMessage.getURI(), newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newIndex, newText, newAttachments);
	}
	
	private static FreenetURI generateURI(FreenetURI baseURI, FTIdentity author, Date date, int index) {
		String dayOfInsertion;
		synchronized (mDateFormat) {
			dayOfInsertion = mDateFormat.format(date);
		}
		baseURI = baseURI.setKeyType("SSK");
		baseURI = baseURI.setDocName(Freetalk.PLUGIN_TITLE + "|" + "Message" + "|" + dayOfInsertion + "-" + index + ".xml");
		return baseURI.setMetaString(null);
	}
	
	private static FreenetURI generateRequestURI(FTOwnIdentity author, Date date, int index) {
		return generateURI(author.getRequestURI(), author, date, index);
	}

	public FreenetURI getInsertURI() {
		return generateURI(((FTOwnIdentity)mAuthor).getInsertURI(), mAuthor, mDate, mIndex);
	}
	
	public synchronized boolean wasInserted() {
		return iWasInserted;
	}
	
	public synchronized void markAsInserted() {
		iWasInserted = true;
	}

}
