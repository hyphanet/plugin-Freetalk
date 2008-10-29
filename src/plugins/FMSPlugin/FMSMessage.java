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
	
	/**
	 * The URI of this message.
	 */
	private final FreenetURI mURI;	
	
	/**
	 * The URI to which this message is a reply. Null if it is a thread.
	 */
	private final FreenetURI mParentURI;
	
	/**
	 * The boards to which this message was posted, in alphabetical order.
	 */
	private final ArrayList<FMSBoard> mBoards; 
	
	private final FMSIdentity mAuthor;

	private final String mTitle;
	
	/**
	 * The date when the message was written in <strong>UTC time</strong>.
	 */
	private final Date mDate;
	
	private final String mText;
	
	/**
	 * The attachments of this message, in the order in which they were received in the original message.
	 */
	private final ArrayList<FreenetURI> mAttachments;
	
	public FMSMessage(FreenetURI newURI, FreenetURI newParentURI, Set<FMSBoard> newBoards, FMSIdentity newAuthor, String newTitle, Date newDate, String newText, List<FreenetURI> newAttachments) {
		if (newURI == null || newBoards == null || newAuthor == null)
			throw new IllegalArgumentException();
		
		if (newBoards.isEmpty())
			throw new IllegalArgumentException("No boards in message " + newURI);
		
		if (!isTitleValid(newTitle))
			throw new IllegalArgumentException("Invalid message title in message " + newURI);
		
		if (!isTextValid(newText))
			throw new IllegalArgumentException("Invalid message text in message " + newURI);
		
		mURI = newURI;
		mParentURI = newParentURI;
		mBoards = new ArrayList<FMSBoard>(newBoards);
		Collections.sort(mBoards);
		mAuthor = newAuthor;
		mTitle = newTitle;
		mDate = newDate; // TODO: Check out whether Date provides a function for getting the timezone and throw an Exception if not UTC.
		mText = newText;
		mAttachments = newAttachments!=null ? new ArrayList<FreenetURI>(newAttachments) : new ArrayList<FreenetURI>();
	}
	
	/**
	 * Get the URI of the message.
	 */
	public FreenetURI getURI() {
		return mURI;
	}
	
	/**
	 * Get an iterator of the boards to which this message was posted.
	 * The boards are returned in alphabetical order.
	 */
	public Iterator<FMSBoard> getBoardIterator() {
		return mBoards.iterator();
	}

	/**
	 * Get the author of the message.
	 */
	public FMSIdentity getAuthor() {
		return mAuthor;
	}

	/**
	 * Get the title of the message.
	 */
	public String getTitle() {
		return mTitle;
	}
	
	/**
	 * Get the date when the message was written in <strong>UTC time</strong>.
	 */
	public Date getDate() {
		return mDate;
	}
	
	/**
	 * Get the text of the message.
	 */
	public String getText() {
		return mText;
	}
	
	/**
	 * Get the attachments of the message, in the order in which they were received.
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
	
	/**
	 * Get the index value for IndexableUpdatableSortedLinkedListItem.
	 */
	public Object indexValue() {
		return mURI;
	}
	
	/**
	 * Checks whether the title of the message is valid. Validity conditions:
	 * - ...
	 */
	static boolean isTitleValid(String title) {
		// FIXME: Implement.
		return true;
	}
	
	/**
	 * Checks whether the text of the message is valid. Validity conditions:
	 * - ...
	 */
	static boolean isTextValid(String text) {
		// FIXME: Implement.
		return true;
	}
	
	/**
	 * Makes the passed title valid in means of <code>isTitleValid()</code>
	 * @see isTitleValid
	 */
	static String makeTitleValid(String title) {
		// FIXME: Implement.
		return title;
	}

	/**
	 * Makes the passed text valid in means of <code>isTextValid()</code>
	 * @see isTextValid
	 */
	static String makeTextValid(String text) {
		// FIXME: Implement.
		return text;
	}
}
