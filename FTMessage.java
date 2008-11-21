/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.db4o.ObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;

/**
 * @author saces, xor
 *
 */
public class FTMessage {
	
	/* Attributes, stored in the database */
	
	/**
	 * The URI of this message.
	 */
	private final FreenetURI mURI;	
	
	/**
	 * The URI of the thread this message belongs to.
	 * We do not need it to construct the thread-tree from messages, but it boosts performance of thread-tree-construction:
	 * Thread-size (amount of replies) is usually infinitesimal compared to the size of a FTBoard (amount of threads).
	 * We receive messages in random order, therefore we will usually have orphan messages of which we need to find the parents.
	 * If we receive the parent messages of those messages, we will be able to find their orphan children faster if we only need to search in
	 * the thread they belong to and not in the whole FTBoard - which may contain many thousands of messages.
	 */
	private final FreenetURI mThreadURI;
	
	/**
	 * The URI of the message to which this message is a reply. Null if it is a thread.
	 */
	private final FreenetURI mParentURI;
	
	/**
	 * The boards to which this message was posted, in alphabetical order.
	 */
	private final FTBoard[] mBoards; 
	
	private final FTIdentity mAuthor;

	private final String mTitle;
	
	/**
	 * The date when the message was written in <strong>UTC time</strong>.
	 */
	private final Date mDate;
	
	private final String mText;
	
	/**
	 * The attachments of this message, in the order in which they were received in the original message.
	 */
	private final FreenetURI[] mAttachments;
	
	/**
	 * The thread to which this message is a reply.
	 */
	private FTMessage mThread = null;
	
	/**
	 * The message to which this message is a reply.
	 */
	private FTMessage mParent = null;
	
	
	/* References to objects of the plugin, not stored in the database. */
	
	private transient ObjectContainer db;
	
	
	/**
	 * Get a list of fields which the database should create an index on.
	 */
	public static String[] getIndexedFields() {
		return new String[] { "mURI", "mThreadURI", "mBoards"};
	}
	
	public FTMessage(ObjectContainer myDB, FreenetURI newURI, FreenetURI newThreadURI, FreenetURI newParentURI, Set<FTBoard> newBoards, FTIdentity newAuthor, String newTitle, Date newDate, String newText, List<FreenetURI> newAttachments) {
		assert(myDB != null);
		
		if (newURI == null || newBoards == null || newAuthor == null)
			throw new IllegalArgumentException();
		
		if (newBoards.isEmpty())
			throw new IllegalArgumentException("No boards in message " + newURI);
		
		if (!isTitleValid(newTitle))
			throw new IllegalArgumentException("Invalid message title in message " + newURI);
		
		if (!isTextValid(newText))
			throw new IllegalArgumentException("Invalid message text in message " + newURI);
		
		db = myDB;
		mURI = newURI;
		mThreadURI = newThreadURI;
		mParentURI = newParentURI;
		mBoards = newBoards.toArray(new FTBoard[newBoards.size()]);
		Arrays.sort(mBoards);
		mAuthor = newAuthor;
		mTitle = newTitle;
		mDate = newDate; // TODO: Check out whether Date provides a function for getting the timezone and throw an Exception if not UTC.
		mText = newText;
		mAttachments = newAttachments != null ? newAttachments.toArray(new FreenetURI[newAttachments.size()])
		        : new FreenetURI[0];
	}
	
	/**
	 * Has to be used after loading a FTBoard object from the database to initialize the transient fields.
	 */
	public void initializeTransient(ObjectContainer myDB) {
		db = myDB;
	}
	
	/**
	 * Get the URI of the message.
	 */
	public FreenetURI getURI() {
		return mURI;
	}
	
	/**
	 * Get the FreenetURI of the thread this message belongs to.
	 */
	public FreenetURI getParentThreadURI() {
		return mThreadURI;
	}
	
	/**
	 * Get the FreenetURI to which this message is a reply. Null if the message is a thread.
	 */
	public FreenetURI getParentURI() {
		return mParentURI;
	}
	
	public boolean isThread() {
		return getParentURI() == null;
	}
	
	/**
	 * Get the boards to which this message was posted.
	 * The boards are returned in alphabetical order.
	 */
	public FTBoard[] getBoards() {
		return mBoards;
	}

	/**
	 * Get the author of the message.
	 */
	public FTIdentity getAuthor() {
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
	public FreenetURI[] getAttachments() {
		return mAttachments;
	}
	
	/**
	 * Get the thread to which this message belongs. The transient fields of the returned message will be initialized already.
	 */
	public synchronized FTMessage getThread() {
		mThread.initializeTransient(db);
		return mThread;
	}
	
	public synchronized void setThread(FTMessage newParentThread) {
		assert(mThread == null);
		assert(mThreadURI == null);
		mThread = newParentThread;
		store();
	}

	/**
	 * Get the message to which this message is a reply. The transient fields of the returned message will be initialized already.
	 */
	public synchronized FTMessage getParent() {
		mParent.initializeTransient(db);
		return mParent;
	}

	public synchronized void setParent(FTMessage newParent)  {
		/* TODO: assert(newParent contains at least one board which mBoards contains) */
		mParent = newParent;
		store();
	}
	
	/**
	 * Returns an iterator over the children of the message, sorted descending by date.
	 * The transient fields of the children will be initialized already.
	 */
	public synchronized Iterator<FTMessage> childrenIterator(final FTBoard board) {
		return new Iterator<FTMessage>() {
			private Iterator<FTMessage> iter;
			
			{
				/* TODO: Accelerate this query: configure db4o to keep a per-message date-sorted index of children.
				 * - Not very important for now since threads are usually small. */
				Query q = db.query();
				q.constrain(FTMessage.class);
				q.descend("mBoard").constrain(board.getName());
				q.descend("mParent").constrain(this);
				q.descend("mDate").orderDescending();
				
				iter = q.execute().iterator();
			}

			public boolean hasNext() {
				return iter.hasNext();
			}

			public FTMessage next() {
				FTMessage child = iter.next();
				child.initializeTransient(db);
				return child;
			}

			public void remove() {
				throw new UnsupportedOperationException("Use child.setParent(null) instead.");
			}
		};
	}
	
	/**
	 * Checks whether the title of the message is valid. Validity conditions:
	 * - ...
	 */
	static public boolean isTitleValid(String title) {
		// FIXME: Implement.
		return true;
	}
	
	/**
	 * Checks whether the text of the message is valid. Validity conditions:
	 * - ...
	 */
	static public boolean isTextValid(String text) {
		// FIXME: Implement.
		return true;
	}
	
	/**
	 * Makes the passed title valid in means of <code>isTitleValid()</code>
	 * @see isTitleValid
	 */
	static public String makeTitleValid(String title) {
		// FIXME: Implement.
		return title;
	}

	/**
	 * Makes the passed text valid in means of <code>isTextValid()</code>
	 * @see isTextValid
	 */
	static public String makeTextValid(String text) {
		// FIXME: Implement.
		return text;
	}
	
	public void store() {
		/* FIXME: Check for duplicates */
		db.store(this);
		db.commit();
	}
}
