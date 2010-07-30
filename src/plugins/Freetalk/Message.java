/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Persistent.IndexedField;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.Logger;
import freenet.support.StringValidityChecker;

/**
 * A Freetalk message. This class is supposed to be used by the UI directly for reading messages. The UI can obtain message objects by querying
 * them from a <code>MessageManager</code>. A message has the usual attributes (author, boards to which it is posted, etc.) one would assume.
 * There are two unique ways to reference a message: It's URI and it's message ID. The URI is to be given to the user if he wants to tell other
 * people about the message, the message ID is to be used for querying the database for a message in a fast way.
 * 
 * Activation policy: Class Message does automatic activation on its own.
 * This means that objects of class Message can be activated to a depth of only 1 when querying them from the database.
 * All methods automatically activate the object to any needed higher depth.
 * 
 * @author xor (xor@freenetproject.org)
 * @author saces
 */
// @IndexedClass // I can't think of any query which would need to get all Message objects.
@IndexedField(names = {"mCreationDate"})
public abstract class Message extends Persistent {
    
    /* Public constants */
	
	// TODO: Get rid of the String.length() limit and only use the byte[].length limit. 
    
    public final static int MAX_MESSAGE_TITLE_TEXT_LENGTH = 256;     // String.length()
    public final static int MAX_MESSAGE_TITLE_BYTE_LENGTH = 256;
    
    public final static int MAX_MESSAGE_TEXT_LENGTH = 64*1024;
    public final static int MAX_MESSAGE_TEXT_BYTE_LENGTH  = 64*1024; // byte[].length
    
    public final static int MAX_BOARDS_PER_MESSAGE = 16;
	
	/* Attributes, stored in the database */
	
	/**
	 * The URI of this message.
	 */
	protected MessageURI mURI; /* Not final because for OwnMessages it is set after the MessageList was inserted */
	
	/**
	 * The physical URI of the message. Null until the message was inserted and the URI is known.
	 */
	protected FreenetURI mFreenetURI; /* Not final because for OwnMessages it is set after the Message was inserted */
	
	/**
	 * The ID of the message. Format: Hex encoded author routing key + "@" + hex encoded random UUID. 
	 */
	@IndexedField /* IndexedField because it is our primary key */
	protected final String mID;
	
	protected MessageList mMessageList; /* Not final because OwnMessages are assigned to message lists after a certain delay */
	
	/**
	 * The URI of the thread this message belongs to.
	 * Notice that the message referenced by this URI might NOT be a thread itself - you are allowed to reply to a message saying that the message is a thread
	 * even though it is a reply to a different thread. Doing this is called forking a thread.
	 */
	protected final MessageURI mThreadURI;
	
	/**
	 * The parent thread ID which was calculated from {@link mThreadURI}
	 */
	@IndexedField /* IndexedField for being able to query all messages of a thread */
	protected final String mThreadID;
	
	/**
	 * The URI of the message to which this message is a reply. Null if it is a thread.
	 */
	protected final MessageURI mParentURI;
	
	/**
	 * The parent message ID which was calculated from {@link mParentURI} 
	 */
	@IndexedField /* IndexedField for being able to get all replies to a message */
	protected final String mParentID;
	
	/**
	 * The boards to which this message was posted, in alphabetical order.
	 */
	protected final Board[] mBoards; 
	
	protected final Board mReplyToBoard;
	
	protected final Identity mAuthor;

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
		private final long mSize; /* Size in bytes */
		
		// TODO: Store mime type and maybe some hashes.
		
		public Attachment(FreenetURI myURI, long mySize) {
			mURI = myURI;
			mSize = mySize;
		}
		
		public FreenetURI getURI() {
			return mURI;
		}
		
		public long getSize() {
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
	
	/**
	 * Whether this message was linked into all it's boards. For an explanation of this flag please read the documentation of {@link wasLinkedIn}.
	 */
	@IndexedField /* IndexedField because the message manager needs to query for all messages which have not been linked in */
	private boolean mWasLinkedIn = false;
	
	
	protected Message(MessageURI newURI, FreenetURI newFreenetURI, String newID, MessageList newMessageList, MessageURI newThreadURI, MessageURI newParentURI, Set<Board> newBoards, Board newReplyToBoard, Identity newAuthor, String newTitle, Date newDate, String newText, List<Attachment> newAttachments) throws InvalidParameterException {
		if(newURI != null && Arrays.equals(newURI.getFreenetURI().getRoutingKey(), newAuthor.getRequestURI().getRoutingKey()) == false)
			throw new InvalidParameterException("The URI of the given message does not match the author's URI: " + newURI);
		
		verifyID(newAuthor, newID);
		
		if(newMessageList != null && newMessageList.getAuthor() != newAuthor)
			throw new InvalidParameterException("The author of the given message list is not the author of this message: " + newURI);
		
		try {
			if(newFreenetURI != null)
				newMessageList.getReference(newFreenetURI);
		}
		catch(NoSuchMessageException e) {
			throw new InvalidParameterException("The given message list does not contain this message: " + newURI);
		}

		if (newBoards.isEmpty())
			throw new InvalidParameterException("No boards in message " + newURI);
		
		if (newBoards.size() > MAX_BOARDS_PER_MESSAGE)
			throw new InvalidParameterException("Too many boards in message " + newURI);
		
		if (newReplyToBoard != null && !newBoards.contains(newReplyToBoard)) {
			Logger.error(this, "Message created with replyToBoard not being in newBoards: " + newURI);
			newBoards.add(newReplyToBoard);
		}
		
		mURI = newURI != null ? newURI.clone() : null;
		mFreenetURI = newFreenetURI != null ? newFreenetURI.clone() : null;
		mMessageList = newMessageList;
		mAuthor = newAuthor;
		mID = newID;
		
    	// There are 4 possible combinations:
    	// Thread URI specified, parent URI specified: We are replying to the given message in the given thread.
    	// Thread URI specified, parent URI not specified: We are replying to the given thread directly, parent URI will be set to thread URI.
    	// Thread URI not specified, parent URI not specified: We are creating a new thread.
    	// Thread URI not specified, parent URI specified: Invalid, we throw an exception.
    	// 
		// The last case is invalid because the thread URI of a message is the primary information which decides in which thread it is displayed
    	// and you can link replies into multiple threads by replying to them with different thread URIs... so if there is only a parent URI and
    	// no thread URI we cannot decide to which thread the message belongs because the parent might belong to multiple thread.
		
		if(newParentURI != null && newThreadURI == null) 
			Logger.error(this, "Message with parent URI but without thread URI created: " + newURI);
		
		mParentURI = newParentURI != null ? newParentURI.clone() : (newThreadURI != null ? newThreadURI.clone() : null);
		mParentID = mParentURI != null ? mParentURI.getMessageID() : null;

		/* If the given thread URI is null, the message will be a thread */ 
		mThreadURI = newThreadURI != null ? newThreadURI.clone() : null;
		mThreadID = newThreadURI != null ? newThreadURI.getMessageID() : null;
		
		mBoards = newBoards.toArray(new Board[newBoards.size()]);
		Arrays.sort(mBoards);		
		mReplyToBoard = newReplyToBoard;
		mTitle = makeTitleValid(newTitle);
		
		if(newDate.after(getFetchDate())) {
			Logger.warning(this, "Received bogus message date: Now = " + getFetchDate() + "; message date = " + newDate + "; message=" + mURI);
			mDate = getFetchDate();
		} else {
			mDate = newDate; // TODO: Check out whether Date provides a function for getting the timezone and throw an Exception if not UTC.
		}
		
		mText = makeTextValid(newText);
		
		if (!isTitleValid(mTitle))
			throw new InvalidParameterException("Invalid message title in message " + newURI);
		
		if (!isTextValid(mText))
			throw new InvalidParameterException("Invalid message text in message " + newURI);
		
		mAttachments = newAttachments == null ? null : newAttachments.toArray(new Attachment[newAttachments.size()]);
	}
	
	/**
	 * Get the author ID from the given message ID
	 * @throws InvalidParameterException If the message ID is invalid.
	 */
	public static final String getAuthorIDFromMessageID(String messageID) throws InvalidParameterException {
		final String tokens[] = messageID.split("[@]");
		
		if(tokens.length != 2)
			throw new InvalidParameterException("Invalid message ID: " + messageID);
		
		// TODO: Further verification
		
		return tokens[1];
	}
	
	/**
	 * Verifies that the given message ID begins with the routing key of the author.
	 * @throws InvalidParameterException If the ID is not valid. 
	 */
	public static final void verifyID(Identity author, String id) throws InvalidParameterException {
		final String authorID = getAuthorIDFromMessageID(id);
		
		if(authorID.equals(Base64.encode(author.getRequestURI().getRoutingKey())) == false)
			throw new InvalidParameterException("Illegal id:" + id);
	}
	
	
	/**
	 * Get the URI of the message.
	 */
	public MessageURI getURI() { /* Not synchronized because only OwnMessage might change the URI */
		checkedActivate(3); // It's likely that the URI will be used so we fully activate it.
		assert(mURI != null);
		
		mURI.initializeTransient(mFreetalk);
		return mURI;
	}
	
	/**
	 * Gets the FreenetURI where this message is actually stored, i.e. the CHK URI of the message.
	 */
	protected FreenetURI getFreenetURI() {
		checkedActivate(2);
		assert(mFreenetURI != null);
		
		return mFreenetURI;
	}

	public final String getID() { /* Not synchronized because only OwnMessage might change the ID */
		return mID;
	}
	
	public final synchronized MessageList getMessageList() {
		checkedActivate(2);
		assert(mMessageList != null);
		
		mMessageList.initializeTransient(mFreetalk);
		return mMessageList;
	}
	
	protected final synchronized void clearMessageList() {
		checkedActivate(2);
		mMessageList = null;
		storeWithoutCommit();
	}
	
	/**
	 * Get the MessageURI of the thread this message belongs to.
	 * @throws NoSuchMessageException 
	 */
	public final synchronized MessageURI getThreadURI() throws NoSuchMessageException {
		checkedActivate(2);
		
		if(mThreadURI == null)
			throw new NoSuchMessageException();
		
		return mThreadURI;
	}

	/**
	 * Get the ID of the thread this message belongs to. Should not be used by the user interface for querying the database as the parent
	 * thread might not have been downloaded yet. Use getThread() instead.
	 * 
	 * Notice that the message referenced by this ID might NOT be a thread itself - you are allowed to reply to a message saying that the message is a thread
	 * even though it is a reply to a different thread. Doing this is called forking a thread.
	 * 
	 * @return The ID of the message's parent thread.
	 * @throws NoSuchMessageException If the message is a thread itself.
	 */
	public final synchronized String getThreadID() throws NoSuchMessageException {
		// checkedActivate(1);
		
		if(mThreadID == null)
			throw new NoSuchMessageException();
		
		return mThreadID;
	}
	
	/**
	 * Gets the thread ID and throws a RuntimeException if it does not exist.
	 * 
	 * getThreadID() would throw a NoSuchMessageException which is not a RuntimeException - if you have checked that isThread()==false already
	 * you want a RuntimeException because getThreadID should not throw.
	 * 
	 * @return
	 */
	public final String getThreadIDSafe() {
		try {
			return getThreadID();
		} catch(NoSuchMessageException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Get the MessageURI to which this message is a reply. Null if the message is a thread.
	 */
	public final MessageURI getParentURI() throws NoSuchMessageException {
		checkedActivate(3); // It's likely that the URI will be used so we fully activate it.
		
		if(mParentURI == null)
			throw new NoSuchMessageException();
		
		mParentURI.initializeTransient(mFreetalk);
		return mParentURI;
	}
	
	public final String getParentID() throws NoSuchMessageException {
		// checkedActivate(1);
		
		if(mParentID == null)
			throw new NoSuchMessageException();
		
		return mParentID;
	}
	
	/**
	 * Gets the parent ID and throws a RuntimeException if it does not exist.
	 * 
	 * getParentID() would throw a NoSuchMessageException which is not a RuntimeException - if you have checked that isThread()==false already
	 * you want a RuntimeException because getParentID should not throw.
	 * 
	 * @return
	 */
	public final String getParentIDSafe() {
		try {
			return getParentID();
		} catch(NoSuchMessageException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Returns true if the message is a thread. A message is considered as a thread if and only if it does not specify the URI of a parent thread - even if it does
	 * specify the URI of a parent message it is still a thread if there is no parent thread URI!
	 */
	public final boolean isThread() {
		checkedActivate(2);
		
		return mThreadURI == null;
	}
	
	/**
	 * Get the boards to which this message was posted. The boards are returned in alphabetical order.
	 * The transient fields of the returned boards are initialized already.
	 */
	public final Board[] getBoards() {
		checkedActivate(2);
		assert(mBoards != null);
		
		for(Board b : mBoards)
			b.initializeTransient(mFreetalk);
		return mBoards;
	}
	
	public final Board getReplyToBoard() throws NoSuchBoardException {
		checkedActivate(2);
		
		if(mReplyToBoard == null)
			throw new NoSuchBoardException();
		
		mReplyToBoard.initializeTransient(mFreetalk);
		return mReplyToBoard;
	}

	/**
	 * Get the author of the message.
	 */
	public final Identity getAuthor() {
		checkedActivate(2);
		assert(mAuthor != null);
		
		if(mAuthor instanceof Persistent) {
			Persistent author = (Persistent)mAuthor;
			author.initializeTransient(mFreetalk);
		}
		return mAuthor;
	}

	/**
	 * Get the title of the message.
	 */
	public final String getTitle() {
		// checkedActivate(1);
		assert(mTitle != null);
		
		return mTitle;
	}
	
	/**
	 * Get the date when the message was written in <strong>UTC time</strong>.
	 */
	public final Date getDate() {
		// checkedActivate(1);
		assert(mDate != null);
		
		return mDate;
	}
	
	/**
	 * Get the date when the message was fetched by Freetalk.
	 */
	public final Date getFetchDate() {
		// checkedActivate(1);
		assert(mCreationDate != null);
		
		return mCreationDate;
	}

	/**
	 * Get the text of the message.
	 */
	public final String getText() {
		// checkedActivate(1);
		assert(mText != null);
		
		return mText;
	}
	
	/**
	 * Get the attachments of the message, in the order in which they were received.
	 */
	public final Attachment[] getAttachments() {
		checkedActivate(3);
		
		return mAttachments;
	}
	
	/**
	 * Get the thread to which this message belongs. The transient fields of the returned message will be initialized already.
	 * 
	 * Notice that the returned message might not be a thread itself - you are allowed to reply to a message saying that the message is a thread
	 * even though it is a reply to a different thread. Doing this is called forking a thread.
	 * 
	 * @throws NoSuchMessageException If the parent thread of this message was not downloaded yet.
	 */
	public final synchronized Message getThread() throws NoSuchMessageException {
		/* TODO: Find all usages of this function and check whether we might want to use a higher depth here */
		checkedActivate(2);
		
		if(mThread == null)
			throw new NoSuchMessageException();
		
		mThread.initializeTransient(mFreetalk);
		return mThread;
	}
	
	public final synchronized void setThread(Message newParentThread) {
		assert(mThread == null || mThread == newParentThread);
		
		if(mThread != null)
			Logger.warning(this, "Thread already exists for " + this + ": existing==" + mThread + "; new==" + newParentThread);
		
		if(!newParentThread.getID().equals(mThreadID)) // mThreadID needs no activation
			throw new IllegalArgumentException("Trying to set a message as thread which has the wrong ID: " + newParentThread.getID());
		
		if(newParentThread instanceof OwnMessage)
			throw new IllegalArgumentException("Trying to set an OwnMessage as parent thread: " + newParentThread);
		
		mThread = newParentThread;
		storeWithoutCommit();
	}
	
	protected final synchronized void clearThread() {
		mThread = null;
		storeWithoutCommit();
	}

	/**
	 * Get the message to which this message is a reply. The transient fields of the returned message will be initialized already.
	 */
	public synchronized Message getParent() throws NoSuchMessageException {
		/* TODO: Find all usages of this function and check whether we might want to use a higher depth here */
		checkedActivate(2);
		
		if(mParent == null)
			throw new NoSuchMessageException();
		
		mParent.initializeTransient(mFreetalk);
		return mParent;
	}

	public final synchronized void setParent(Message newParent)  {
		assert(mParent == null || newParent == mParent);
		
		if(mParent != null)
			Logger.warning(this, "Parent already exists for " + this + ": existing==" + mParent + "; new==" + newParent);
		
		if(!newParent.getID().equals(mParentID)) // mParentID needs no activation
			throw new IllegalArgumentException("Trying to set a message as parent which has the wrong ID: " + newParent.getID());
		
		if(newParent instanceof OwnMessage)
			throw new IllegalArgumentException("Trying to set an OwnMessage as parent: " + newParent);
		
		mParent = newParent;
		storeWithoutCommit();
	}
	
	protected final synchronized void clearParent() {
		mParent = null;
		storeWithoutCommit();
	}
	
	/** 
	 * @return Returns a flag which indicates whether this message was linked into all boards where it should be visible (by calling addMessage() on those boards).
	 * 			A message will only be visible in boards where it was linked in - the sole storage of a object of type Message does not make it visible.
	 * 
	 */
	protected final synchronized boolean wasLinkedIn() {
		// checkedActivate(1);
		return mWasLinkedIn;
	}
	
	/**
	 * Sets the wasLinkedIn flag of this message.
	 * For an explanation of this flag please read the documentation of {@link wasLinkedIn}.
	 */
	protected final synchronized void setLinkedIn(boolean wasLinkedIn) {
		mWasLinkedIn = wasLinkedIn;
	}
	
	/**
	 * Checks whether the title of the message is valid. Validity conditions:
	 * - Not empty
	 * - No line breaks, tabs, or any other control characters.
	 * - No invalid characters.
	 * - valid UTF-8 encoding
	 * - No invalid UTF formatting (unpaired direction or annotation characters).
	 * - Not too long
	 */
	public static final boolean isTitleValid(String title) {
		if(title == null)
			return false;
		
		if(title.length() == 0)
			return false;
		
		if(title.length() > MAX_MESSAGE_TITLE_TEXT_LENGTH)
			return false;
		
	    try {
    	    if (title.getBytes("UTF-8").length > MAX_MESSAGE_TITLE_BYTE_LENGTH) {
    	        return false;
    	    }
	    } catch(UnsupportedEncodingException e) {
	        return false;
	    }
		
		return  (StringValidityChecker.containsNoInvalidCharacters(title)
				&& StringValidityChecker.containsNoLinebreaks(title)
				&& StringValidityChecker.containsNoControlCharacters(title)
				&& StringValidityChecker.containsNoInvalidFormatting(title));
	}
	
	/**
	 * Checks whether the text of the message is valid. Validity conditions:
	 * - Not null
	 * - Not more than MAX_MESSAGE_TEXT_LENGTH characters or MAX_MESSAGE_TEXT_BYTE_LENGTH bytes.
	 * - Valid UTF-8 encoding.
	 * - Contains no invalid UTF formatting (unpaired direction or annotation characters).
	 * - Control characters are allowed: We want to allow other plugins to use Freetalk as their core, therefore we should not be too restrictive about
	 *   message content. Freetalk user interfaces have to take care on their own to be secure against weird control characters.
	 */
	public static final boolean isTextValid(String text) {
        if (text == null) {
            return false;
        }
	    if (text.length() > MAX_MESSAGE_TEXT_LENGTH) {
	        return false;
	    }
	    try {
    	    if (text.getBytes("UTF-8").length > MAX_MESSAGE_TEXT_BYTE_LENGTH) {
    	        return false;
    	    }
	    } catch(UnsupportedEncodingException e) {
	        return false;
	    }
	    
		return  (StringValidityChecker.containsNoInvalidCharacters(text)
				&& StringValidityChecker.containsNoInvalidFormatting(text));
	}
	
	/**
	 * Makes the passed title valid in means of <code>isTitleValid()</code>
	 * @see isTitleValid
	 */
	public static final String makeTitleValid(String title) {
		// TODO: the newline handling here is based on the RFC 822
		// format (newline + linear white space = single space).  If
		// necessary, we could move that part of the cleaning-up to
		// ui.NNTP.ArticleParser, but the same algorithm should work
		// fine in the general case.
		// TODO: Why are we only replacing multiple whitespace with a single one if a newline is prefixed?

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
	public static final String makeTextValid(String text) {
		return text.replace("\r\n", "\n");
	}
	
	public final synchronized void storeAndCommit() {
		synchronized(mDB.lock()) {
			try {
				storeWithoutCommit();
				checkedCommit(this);
			}
			catch(RuntimeException e) {
				checkedRollbackAndThrow(e);
			}
		}
	}
	
	public void storeWithoutCommit() {
		try {
			checkedActivate(3); // 3 is the maximum depth of all getter functions. You have to adjust this when adding new members.
			
			for(Board board : mBoards)
				throwIfNotStored(board);
			
			throwIfNotStored(mAuthor);
			
			// The MessageLists of OwnMessages are created within the same transaction as the storeAndCommit so we cannot do throwIfNotStored for them.
			if(mMessageList != null && !(this instanceof OwnMessage))
				throwIfNotStored(mMessageList);
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !

			if(mURI != null) {
				mURI.initializeTransient(mFreetalk);
				mURI.storeWithoutCommit();
			}
			if(mFreenetURI != null) {
				// It's a FreenetURI so it does not extend Persistent.
				checkedStore(mFreenetURI);
			}
			if(mThreadURI != null) {
				mThreadURI.initializeTransient(mFreetalk);
				mThreadURI.storeWithoutCommit();
			}
			if(mParentURI != null) {
				mParentURI.initializeTransient(mFreetalk);
				mParentURI.storeWithoutCommit();
			}
			// db.store(mBoards); /* Not stored because it is a primitive for db4o */
			// db.store(mDate); /* Not stored because it is a primitive for db4o */
			// db.store(mAttachments); /* Not stored because it is a primitive for db4o */
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
	
	protected void deleteWithoutCommit() {
		try {
			checkedActivate(3); // TODO: Figure out a suitable depth.
			
			checkedDelete(this);
			
			if(mParentURI != null) { 
				mParentURI.initializeTransient(mFreetalk);
				mParentURI.deleteWithoutCommit();
			}
			if(mThreadURI != null) {
				mThreadURI.initializeTransient(mFreetalk);
				mThreadURI.deleteWithoutCommit();
			}
			if(mFreenetURI != null) {
				// It's a FreenetURI so there is no transient initialization
				mFreenetURI.removeFrom(mDB);
			}
			if(mURI != null) {
				mURI.initializeTransient(mFreetalk);
				mURI.deleteWithoutCommit();
			}
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

    
    @Override
	public boolean equals(Object obj) {
    	if(obj instanceof Message) {
    		Message otherMessage = (Message)obj;
    		return mID.equals(otherMessage.getID()); // mID needs no activation
    	} else
    		return false;
	}
    
    public String toString() {
    	if(mDB != null)
    		return getURI().toString();
    	
		// We do not throw a NPE because toString() is usually used in logging, we want the logging to be robust
		
		Logger.error(this, "toString() called before initializeTransient()!");
		
		return super.toString() + " (intializeTransient() not called!, message URI may be null, here it is: " + mURI + ")";
    }

}
