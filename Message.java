/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.HexUtil;
import freenet.support.Logger;
import freenet.support.StringValidityChecker;

/**
 * @author saces, xor
 *
 */
public class Message implements Comparable<Message> {
	
	/* Attributes, stored in the database */
	
	/**
	 * The URI of this message. Format: SSK@author_ssk_uri/Freetalk|MessageList-index.xml#uuid
	 * The "uuid" in the URI is the part after the "@" in the message ID.
	 */
	protected FreenetURI mURI; /* Not final because for OwnMessages it is set after the MessageList was inserted */
	
	/**
	 * The ID of the message. Format: Hex encoded author routing key + "@" + hex encoded random UUID. 
	 */
	protected final String mID;
	
	protected MessageList mMessageList; /* Not final because OwnMessages are assigned to message lists after a certain delay */
	
	/**
	 * The URI of the thread this message belongs to.
	 * We do not need it to construct the thread-tree from messages, but it boosts performance of thread-tree-construction:
	 * Thread-size (amount of replies) is usually infinitesimal compared to the size of a FTBoard (amount of threads).
	 * We receive messages in random order, therefore we will usually have orphan messages of which we need to find the parents.
	 * If we receive the parent messages of those messages, we will be able to find their orphan children faster if we only need to search in
	 * the thread they belong to and not in the whole FTBoard - which may contain many thousands of messages.
	 */
	protected final FreenetURI mThreadURI;
	
	protected final String mThreadID;
	
	/**
	 * The URI of the message to which this message is a reply. Null if it is a thread.
	 */
	protected final FreenetURI mParentURI;
	
	protected final String mParentID;
	
	/**
	 * The boards to which this message was posted, in alphabetical order.
	 */
	protected final Board[] mBoards; 
	
	protected final Board mReplyToBoard;
	
	protected final FTIdentity mAuthor;

	protected final String mTitle;
	
	/**
	 * The date when the message was written in <strong>UTC time</strong>.
	 */
	protected final Date mDate;
	
	protected final String mText;
	
	/**
	 * The attachments of this message, in the order in which they were received in the original message.
	 */
	protected final Attachment[] mAttachments;
	
	public static class Attachment {
		private final FreenetURI mURI;
		private final int mSize; /* Size in bytes */
		
		public Attachment(FreenetURI myURI, int mySize) {
			mURI = myURI;
			mSize = mySize;
		}
		
		public FreenetURI getURI() {
			return mURI;
		}
		
		public int getSize() {
			return mSize;
		}
	}
	
	/**
	 * The thread to which this message is a reply.
	 */
	private Message mThread = null;
	
	/**
	 * The message to which this message is a reply.
	 */
	private Message mParent = null;
	
	
	/* References to objects of the plugin, not stored in the database. */
	
	protected transient ObjectContainer db;
	
	protected transient MessageManager mMessageManager;
	
	
	/**
	 * Get a list of fields which the database should create an index on.
	 */
	public static String[] getIndexedFields() {
		return new String[] { "mURI", "mID", "mParentID" };
	}

	/**
	 * Constructor for received messages.
	 */
	public Message construct(FreenetURI newURI, String newID, MessageList newMessageList, FreenetURI newThreadURI, FreenetURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, FTIdentity newAuthor, String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		if (newURI == null || newMessageList == null || newBoards == null || newAuthor == null)
			throw new IllegalArgumentException();
		
		if(Arrays.equals(newURI.getRoutingKey(), newAuthor.getRequestURI().getRoutingKey()) == false)
			throw new InvalidParameterException("Trying to create a message with an URI different to the URI of the author: newURI == " + newURI + "; newAuthor.requestURI == " + newAuthor.getRequestURI());
		
		if(newMessageList.getAuthor() != newAuthor)
			throw new InvalidParameterException("Trying to construct a message of " + newAuthor + " with a messagelist which belong to a different author: " + newMessageList.getAuthor());
		
		return new Message(newURI, newID, newMessageList, newThreadURI, newParentURI, newBoards, newReplyToBoard, newAuthor, newTitle, newDate, newText, newAttachments);
	}

	protected Message(FreenetURI newURI, String newID, MessageList newMessageList, FreenetURI newThreadURI, FreenetURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, FTIdentity newAuthor, String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		assert(newURI == null || Arrays.equals(newURI.getRoutingKey(), newAuthor.getRequestURI().getRoutingKey()));
		
		verifyID(newAuthor, newID);
		
		if(newParentURI != null && newThreadURI == null) 
			Logger.error(this, "Message with parent URI but without thread URI created: " + newURI);
		
		if (newBoards.isEmpty())
			throw new InvalidParameterException("No boards in message " + newURI);
		
		if (newReplyToBoard != null && !newBoards.contains(newReplyToBoard)) {
			Logger.error(this, "Message created with replyToBoard not being in newBoards: " + newURI);
			newBoards.add(newReplyToBoard);
		}

		if (!isTitleValid(newTitle))
			throw new InvalidParameterException("Invalid message title in message " + newURI);
		
		if (!isTextValid(newText))
			throw new InvalidParameterException("Invalid message text in message " + newURI);
	
		mURI = newURI;
		mMessageList = newMessageList;
		mAuthor = newAuthor;
		mID = newID;
		mThreadURI = newThreadURI;
		mThreadID = mThreadURI != null ? getIDFromURI(mThreadURI) : null;
		mParentURI = newParentURI;
		mParentID = mParentURI != null ? getIDFromURI(mParentURI) : null;
		mBoards = newBoards.toArray(new Board[newBoards.size()]);
		Arrays.sort(mBoards);		
		mReplyToBoard = newReplyToBoard;
		mTitle = newTitle;
		mDate = newDate; // TODO: Check out whether Date provides a function for getting the timezone and throw an Exception if not UTC.
		mText = newText;
		mAttachments = newAttachments == null ? null : newAttachments.toArray(new Attachment[newAttachments.size()]);
	}
	
	/**
	 * Has to be used after loading a FTBoard object from the database to initialize the transient fields.
	 */
	public void initializeTransient(ObjectContainer myDB, MessageManager myMessageManager) {
		assert(myDB != null);
		assert(myMessageManager != null);
		db = myDB;
		mMessageManager = myMessageManager;
	}
	
	public static String generateRandomID(FTIdentity author) {
		return HexUtil.bytesToHex(author.getRequestURI().getRoutingKey()) + "@" + UUID.randomUUID();
	}
	
	public static String getIDFromURI(FreenetURI uri) {
		String uuid = uri.getDocName().split("[#]")[1];
		return HexUtil.bytesToHex(uri.getRoutingKey()) + "@" + uuid;
	}
	
	/**
	 * Verifies that the given message ID begins with the routing key of the author.
	 * @throws InvalidParameterException If the ID is not valid. 
	 */
	public static void verifyID(FTIdentity author, String id) throws InvalidParameterException {
		if(id.startsWith(HexUtil.bytesToHex(author.getRequestURI().getRoutingKey())) == false)
			throw new InvalidParameterException("Illegal id:" + id);
	}
	
	/**
	 * Get the URI of the message.
	 */
	public FreenetURI getURI() { /* Not synchronized because only OwnMessage might change the URI */
		return mURI;
	}
	
	public String getID() { /* Not synchronized because only OwnMessage might change the ID */
		return mID;
	}
	
	/**
	 * Get the FreenetURI of the thread this message belongs to.
	 * @throws NoSuchMessageException 
	 */
	public FreenetURI getParentThreadURI() throws NoSuchMessageException {
		if(mThreadURI == null)
			throw new NoSuchMessageException();
		
		return mThreadURI;
	}
	
	public String getParentThreadID() throws NoSuchMessageException {
		if(mThreadID == null)
			throw new NoSuchMessageException();
		
		
		return mThreadID;
	}
	
	/**
	 * Get the FreenetURI to which this message is a reply. Null if the message is a thread.
	 */
	public FreenetURI getParentURI() throws NoSuchMessageException {
		if(mParentURI == null)
			throw new NoSuchMessageException();
		
		return mParentURI;
	}
	
	public String getParentID() throws NoSuchMessageException {
		if(mParentID == null)
			throw new NoSuchMessageException();
		
		return mParentID;
	}
	
	public boolean isThread() {
		return mParentURI == null;
	}
	
	/**
	 * Get the boards to which this message was posted. The boards are returned in alphabetical order.
	 * The transient fields of the returned boards are initialized already.
	 */
	public Board[] getBoards() {
		for(Board b : mBoards)
			b.initializeTransient(db, mMessageManager);
		return mBoards;
	}
	
	public Board getReplyToBoard() {
		return mReplyToBoard;
	}

	/**
	 * Get the author of the message.
	 */
	public FTIdentity getAuthor() {
		mAuthor.initializeTransient(db, mMessageManager.getIdentityManager());
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
	public Attachment[] getAttachments() {
		return mAttachments;
	}
	
	/**
	 * Get the thread to which this message belongs. The transient fields of the returned message will be initialized already.
	 */
	public synchronized Message getThread() throws NoSuchMessageException {
		if(mThread == null)
			throw new NoSuchMessageException();
		mThread.initializeTransient(db, mMessageManager);
		return mThread;
	}
	
	public synchronized void setThread(Message newParentThread) {
		assert(mThread == null);
		assert(mThreadURI != null);
		assert(newParentThread.getURI().equals(mThreadURI));
		mThread = newParentThread;
		store();
	}

	/**
	 * Get the message to which this message is a reply. The transient fields of the returned message will be initialized already.
	 */
	public synchronized Message getParent() throws NoSuchMessageException {
		if(mParent == null)
			throw new NoSuchMessageException();
		mParent.initializeTransient(db, mMessageManager);
		return mParent;
	}

	public synchronized void setParent(Message newParent)  {
		assert(newParent.getURI().equals(mParentURI));
		/* TODO: assert(newParent contains at least one board which mBoards contains) */
		mParent = newParent;
		store();
	}
	
	/**
	 * Returns an iterator over the children of the message, sorted descending by date.
	 * The transient fields of the children will be initialized already.
	 */
	@SuppressWarnings("unchecked")
	public synchronized Iterator<Message> childrenIterator(final Board targetBoard) {
		return new Iterator<Message>() {
			private Iterator<Message> iter;
			private Board board;
			private Message next;
			
			{
				board = targetBoard;
				
				/* TODO: Accelerate this query: configure db4o to keep a per-message date-sorted index of children.
				 * - Not very important for now since threads are usually small. */
				Query q = db.query();
				q.constrain(Message.class);
				q.descend("mParent").constrain(this);
				q.descend("mDate").orderDescending();
				
				iter = q.execute().iterator();
				next = iter.hasNext() ? iter.next() : null;
			}

			public boolean hasNext() {
				while(next != null) {
					if(Arrays.binarySearch(next.getBoards(), board) >= 0)
						return true;
				}
				
				return false;
			}

			public Message next() {
				if(!hasNext()) { /* We have to call hasNext() to ignore messages which do not belong to the selected board */
					assert(false); /* However, the users of the function should do this for us */
					throw new NoSuchElementException();
				}
				
				Message child = next;
				next = iter.hasNext() ? iter.next() : null;
				child.initializeTransient(db, mMessageManager);
				return child;
			}

			public void remove() {
				throw new UnsupportedOperationException("Use child.setParent(null) instead.");
			}
		};
	}
	
	/**
	 * Checks whether the title of the message is valid. Validity conditions:
	 * - No line breaks, tabs, or any other control characters.
	 * - No invalid characters.
	 * - No invalid formatting (unpaired direction or annotation characters.)
	 */
	static public boolean isTitleValid(String title) {
		return (StringValidityChecker.containsNoInvalidCharacters(title)
				&& StringValidityChecker.containsNoLinebreaks(title)
				&& StringValidityChecker.containsNoControlCharacters(title)
				&& StringValidityChecker.containsNoInvalidFormatting(title));
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
		// FIXME: the newline handling here is based on the RFC 822
		// format (newline + linear white space = single space).  If
		// necessary, we could move that part of the cleaning-up to
		// ui.NNTP.ArticleParser, but the same algorithm should work
		// fine in the general case.

		StringBuilder result = new StringBuilder();
		boolean replacingNewline = false;
		int dirCount = 0;
		boolean inAnnotatedText = false;
		boolean inAnnotation = false;

		for (int i = 0; i < title.length(); ) {
			int c = title.codePointAt(i);
			i += Character.charCount(c);

			if (c == '\r' || c == '\n') {
				if (!replacingNewline) {
					replacingNewline = true;
					result.append(' ');
				}
			}
			else if (c == '\t' || c == ' ') {
				if (!replacingNewline)
					result.append(' ');
			}
			else if (c == 0x202A	// LEFT-TO-RIGHT EMBEDDING
					 || c == 0x202B		// RIGHT-TO-LEFT EMBEDDING
					 || c == 0x202D		// LEFT-TO-RIGHT OVERRIDE
					 || c == 0x202E) {	// RIGHT-TO-LEFT OVERRIDE
				dirCount++;
				result.appendCodePoint(c);
			}
			else if (c == 0x202C) {	// POP DIRECTIONAL FORMATTING
				if (dirCount > 0) {
					dirCount--;
					result.appendCodePoint(c);
				}
			}
			else if (c == 0xFFF9) {	// INTERLINEAR ANNOTATION ANCHOR
				if (!inAnnotatedText && !inAnnotation) {
					result.appendCodePoint(c);
					inAnnotatedText = true;
				}
			}
			else if (c == 0xFFFA) {	// INTERLINEAR ANNOTATION SEPARATOR
				if (inAnnotatedText) {
					result.appendCodePoint(c);
					inAnnotatedText = false;
					inAnnotation = true;
				}
			}
			else if (c == 0xFFFB) { // INTERLINEAR ANNOTATION TERMINATOR
				if (inAnnotation) {
					result.appendCodePoint(c);
					inAnnotation = false;
				}
			}
			else if ((c & 0xFFFE) == 0xFFFE) {
				// invalid character, ignore
			}
			else {
				replacingNewline = false;

				switch (Character.getType(c)) {
				case Character.CONTROL:
				case Character.SURROGATE:
				case Character.LINE_SEPARATOR:
				case Character.PARAGRAPH_SEPARATOR:
					break;

				default:
					result.appendCodePoint(c);
				}
			}
		}

		if (inAnnotatedText) {
			result.appendCodePoint(0xFFFA);
			result.appendCodePoint(0xFFFB);
		}
		else if (inAnnotation) {
			result.appendCodePoint(0xFFFB);
		}

		while (dirCount > 0) {
			result.appendCodePoint(0x202C);
			dirCount--;
		}

		return result.toString();
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
		/* FIXME: Check for duplicates. Also notice that an OwnMessage which is equal might exist */
		
		if(db.ext().isStored(this) && !db.ext().isActive(this))
			throw new RuntimeException("Trying to store a non-active Message object");
		
		if(mAuthor == null)
			throw new RuntimeException("Trying to store a message with mAuthor == null");
		
		db.store(mURI);
		if(mThreadURI != null)
			db.store(mThreadURI);
		if(mParentURI != null)
			db.store(mParentURI);
		// db.store(mBoards); /* Not stored because it is a primitive for db4o */
		// db.store(mDate); /* Not stored because it is a primitive for db4o */
		// db.store(mAttachments); /* Not stored because it is a primitive for db4o */
		db.store(this);
		db.commit();
	}

	public int compareTo(Message other) {
		return mDate.compareTo(other.mDate);
	}

}
