/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import freenet.keys.FreenetURI;

/**
 * @author saces, xor
 *
 */
public abstract class FMSMessage {
	
	public final FreenetURI mURI;
	
	public final SortedSet<FMSBoard> mBoards; 
	
	public final FMSIdentity mAuthor;

	public final String mTitle;
	
	/**
	 * The date when the message was written in UTC time.
	 */
	public final Date mDate;
	
	public FMSMessage(FreenetURI newURI, SortedSet<FMSBoard> newBoards, FMSIdentity newAuthor, String newTitle, Date newDate) {
		mURI = newURI;
		mBoards = newBoards;
		mAuthor = newAuthor;
		mTitle = newTitle;
		mDate = newDate; // TODO: Check out whether Date provides a function for getting the timezone and throw an Exception if not UTC.
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
	public abstract String getText();
	
	/**
	 * Get the attachments of the message.
	 * @return The attachments of the message.
	 */
	public abstract List<FreenetURI> getAttachments();
}
