/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import freenet.keys.FreenetURI;
import freenet.support.IndexableUpdatableSortedLinkedListItem;
import freenet.support.UpdatableSortedLinkedListItemImpl;

/**
 * @author saces, xor
 *
 */
public abstract class FMSMessage extends UpdatableSortedLinkedListItemImpl implements IndexableUpdatableSortedLinkedListItem {
	
	private final FreenetURI mURI;
	
	private final ArrayList<FMSBoard> mBoards; 
	
	private final FMSIdentity mAuthor;

	private final String mTitle;
	
	/**
	 * The date when the message was written in UTC time.
	 */
	private final Date mDate;
	
	private final String mText;
	
	private final ArrayList<FreenetURI> mAttachments;
	
	public FMSMessage(FreenetURI newURI, Set<FMSBoard> newBoards, FMSIdentity newAuthor, String newTitle, Date newDate, String newText, List<FreenetURI> newAttachments) {
		mURI = newURI;
		mBoards = new ArrayList<FMSBoard>(newBoards);
		Collections.sort(mBoards);
		mAuthor = newAuthor;
		mTitle = newTitle;
		mDate = newDate; // TODO: Check out whether Date provides a function for getting the timezone and throw an Exception if not UTC.
		mText = newText;
		mAttachments = new ArrayList<FreenetURI>(newAttachments);
	}
	
	/**
	 * Get the URI of the message.
	 * @return The URI of the message.
	 */
	public FreenetURI getURI() {
		return mURI;
	}
	
	public Iterator<FMSBoard> getBoardIterator() {
		return mBoards.iterator();
	}

	/**
	 * Get the author of the message.
	 * @return The author of the message.
	 */
	public FMSIdentity getAuthor() {
		return mAuthor;
	}

	/**
	 * Get the title of the message.
	 * @return The title of the message.
	 */
	public String getTitle() {
		return mTitle;
	}
	
	/**
	 * Get the date when the message was written.
	 * @return The date when the message was written in UTC time.
	 */
	public Date getDate() {
		return mDate;
	}
	
	/**
	 * Get the text of the message.
	 * @return The text of the message.
	 */
	public String getText() {
		return mText;
	}
	
	/**
	 * Get the attachments of the message.
	 * @return The attachments of the message.
	 */
	public ArrayList<FreenetURI> getAttachments() {
		return mAttachments;
	}

	/**
	 * Compare by Date to the other FMSMessage.
	 * @param o An object of type FMSMessage 
	 */
	public int compareTo(Object o) {
		FMSMessage m = (FMSMessage)o;
		return mDate.compareTo(m.getDate());
	}
	
	public Object indexValue() {
		return mURI;
	}
}
