/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import plugins.Freetalk.Identity.IdentityID;
import plugins.Freetalk.Message.MessageID;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchFetchFailedMarkerException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.Logger;

/**
 * A <code>MessageList</code> contains a list of <code>MessageReference</code> objects.
 * Each <code>MessageReference</code> object contains the CHK URI of the referenced <code>Message</code>, the ID of the message and a single 
 * <code>Board> to which the <code>Message</code> belongs. If a <code>Message</code> belongs to multiple boards, a <code>MessageReference</code>
 * is stored for each of them.
 * Constraints:
 * - A <code>MessageList</code> must map the ID of a contained message to a single URI.
 * - A <code>MessageList</code> should not contain multiple mapppings of a URI to the same board. This is considered as DoS
 * - A <code>MessageList</code> should maybe be limited to a maximal amount of messages references.
 * - There should be a limit to a certain maximal amount of boards a message can be posted to.
 * 
 * Activation policy: Class MessageList (and it's member classes) does automatic activation on its own.
 * This means that objects of class MessageList can be activated to a depth of only 1 when querying them from the database.
 * All methods automatically activate the object to any needed higher depth.
 */
// @IndexedClass // I can't think of any query which would need to get all MessageList objects.
public abstract class MessageList extends Persistent implements Iterable<MessageList.MessageReference> {
	
	public static transient final int MAX_MESSAGES_PER_MESSAGELIST = 256;
	
	
	@IndexedField
	protected String mID; /* Not final because OwnMessageList.incrementInsertIndex() might need to change it */
	
	@IndexedField
	protected final Identity mAuthor;
	
	@IndexedField
	protected long mIndex; /* Not final because OwnMessageList.incrementInsertIndex() might need to change it */
	
	protected final ArrayList<MessageReference> mMessages;


	@Override
	public void databaseIntegrityTest() throws Exception {
		checkedActivate(1);
		
		if(mID == null)
			throw new NullPointerException("mID==null");
		
		final MessageListID id = MessageListID.construct(mID); // Also checks whether the index is valid
		
		if(mAuthor == null)
			throw new NullPointerException("mAuthor==null");
		
		id.throwIfAuthorDoesNotMatch(getAuthor());
		
		if(id.getIndex() != mIndex)
			throw new IllegalStateException("mIndex does not match mID: " + mIndex);
		
		if(mMessages==null)
			throw new NullPointerException("mMessages==null");
		
		if(getMessages().size() > MAX_MESSAGES_PER_MESSAGELIST)
			throw new IllegalStateException("mMessages is too large: " + getMessages().size());
		
		// TODO: Validate content of mMessages as we do in the constructor...
	}


	/**
	 * A class for representing and especially verifying message IDs.
	 * We do not use it as a type for storing it because that would make the database queries significantly slower.
	 */
	public static final class MessageListID {
		
		private final String mID;
		
		private final IdentityID mAuthorID;
		
		private final long mIndex;
		
		
		private MessageListID(String id) {
			mID = id;
			
			final StringTokenizer tokenizer = new StringTokenizer(id, "@");
			
			mIndex = Long.parseLong(tokenizer.nextToken());
			
			if(mIndex < 0)
				throw new IllegalArgumentException("Invalid index: " + mIndex);
			
			mAuthorID = IdentityID.construct(tokenizer.nextToken());
			
			if(tokenizer.hasMoreTokens())
				throw new IllegalArgumentException("Invalid MessageListID: " + id);
		}
		
		private MessageListID(String authorID, long index) {
			if(index < 0)
				throw new IllegalArgumentException("Invalid index: " + index);
			
			mID = index + "@" + authorID;
			mIndex = index;
			mAuthorID = IdentityID.construct(authorID);
		}
		
		private MessageListID(Identity author, long index) {
			this(author.getID(), index);
		}
	
		private MessageListID(FreenetURI messageListURI) {
			this(IdentityID.constructFromURI(messageListURI).toString(), messageListURI.getEdition());
		}
		
		public static final MessageListID construct(String id) {
			return new MessageListID(id); // TODO: Optimization: Write a non-validating constructor & use it in callers when possible.
		}
		
		public static final MessageListID construct(Identity author, long index) {
			return new MessageListID(author, index);
		}
		
		public static final MessageListID construct(FreenetURI messageListURI) {
			return new MessageListID(messageListURI);
		}
		
		public final void throwIfAuthorDoesNotMatch(Identity author) {
			if(!mAuthorID.equals(author.getID()))
				throw new IllegalArgumentException("MessageListID contains the wrong author ID, should be "
						+ author.getID() + " but is " + mAuthorID);
		}
		
		public final long getIndex() {
			return mIndex;
		}
		
		public final IdentityID getAuthorID() {
			return mAuthorID;
		}
		
		public final String toString() {
			return mID;
		}
	}
	
	/**
	 * Stores the <code>FreenetURI</code> and the <code>Board</code> of a not downloaded message. If a message is posted to multiple boards, a
	 * <code>MessageReference</code> is stored for each board. This is done to allow querying the database for <code>MessageReference</code>
	 * objects which belong to a certain board - which is necessary because we only want to download messages from boards to which the
	 * user is actually subscribed.
	 */
	// @IndexedClass // I can't think of any query which would need to get all MessageReference objects.
	@IndexedField(names = {"mCreationDate"})
	public static class MessageReference extends Persistent {
		
		private MessageList mMessageList = null;
		
		@IndexedField
		private final String mMessageID;
		
		@IndexedField
		private final FreenetURI mURI; 
		
		@IndexedField
		private final Board mBoard;
		
		@IndexedField
		private final Date mDate;
		
		@IndexedField
		private boolean mWasDownloaded = false;

		
		public MessageReference(MessageID newMessageID, FreenetURI newURI, Board myBoard, Date myDate) {			
			if(newURI == null)
				throw new NullPointerException("Message URI is null."); 
				
			if(myBoard == null && !(this instanceof OwnMessageList.OwnMessageReference))
				throw new IllegalArgumentException("Board is null and this is no own message reference.");
			
			if(myDate == null)
				throw new NullPointerException("Message date is null.");
			
			mMessageID = newMessageID.toString();
			mURI = newURI.clone(); // Prevent weird db4o problems.
			mBoard = myBoard;
			mDate = myDate;
		}
		
		@Override
		public void databaseIntegrityTest() throws Exception {		
			checkedActivate(1);
			
			if(mMessageID == null)
				throw new NullPointerException("mMessageID==null");
			
			if(mMessageList == null)
				throw new NullPointerException("mMessageList==null");
			
			MessageID.construct(mMessageID).throwIfAuthorDoesNotMatch(getMessageList().getAuthor());
			
			if(mURI == null)
				throw new NullPointerException("mURI==null");
			
			if(getMessageList().getReference(getURI()) != this)
				throw new IllegalStateException("Parent message list does not contain this MessageReference.");
			
			if(!(this instanceof OwnMessageList.OwnMessageReference) && mBoard == null)
				throw new NullPointerException("mBoard==null");
			
			if(mDate == null)
				throw new NullPointerException("mDate==null");
			
			// Do not check the date, it might be bogus because it is obtained from the content of the message list
			
			
			if(!(this instanceof OwnMessageList.OwnMessageReference)) { // OwnMessageReferences do not get marked as fetched
			try {
				mFreetalk.getMessageManager().get(mMessageID);
				if(!mWasDownloaded)
					throw new IllegalStateException("mWasDownloaded==false but message exists.");
			} catch(NoSuchMessageException e1) {
				if(mWasDownloaded) {
					try {
						mFreetalk.getMessageManager().getMessageFetchFailedMarker(this);
					} catch(NoSuchFetchFailedMarkerException e2) {
						throw new IllegalStateException("mWasDownloaded==true but message does not exist and there is no FetchFailedMarker.");
					}
				}
			}
			}
		}
		
		
		protected void storeWithoutCommit() {
			try {
				checkedActivate(1);
				
				// We cannot throwIfNotStored because MessageReference objects are usually created within the same transaction of creating the MessageList
				//DBUtil.throwIfNotStored(db, mMessageList);
				
				// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
				
				if(mURI == null)
					throw new NullPointerException("Should not happen: URI is null for " + this);
				
				checkedActivate(mURI, 2);
				checkedStore(mURI);
				checkedStore();
			}
			catch(RuntimeException e) {
				checkedRollbackAndThrow(e);
			}
		}
		
		public void deleteWithoutCommit() {
			try {
				checkedActivate(1);
				
				checkedDelete();
				
				if(mURI != null) {
					checkedActivate(mURI, 2);
					mDB.delete(mURI);
				}
				else
					Logger.error(this, "Should not happen: URI is null for " + this);
			}
			catch(RuntimeException e) {
				checkedRollbackAndThrow(e);
			}
		}
		
		public String getMessageID() {
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
			return mMessageID;
		}
		
		public FreenetURI getURI() {
			checkedActivate(1);
			checkedActivate(mURI, 2);
			return mURI;
		}
		
		public Board getBoard() {
			checkedActivate(1);
			mBoard.initializeTransient(mFreetalk);
			return mBoard;
		}
		
		public Date getDate() {
			checkedActivate(1); // Date is a db4o primitive type so 1 is enough
			return mDate;
		}
		
		public synchronized boolean wasMessageDownloaded() {
			checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
			return mWasDownloaded;
		}
		
		/**
		 * Marks the MessageReference as downloaded and stores the change in the database, without committing the transaction.
		 */
		public synchronized void setMessageWasDownloadedFlag() {
			checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
			
			// TODO: Figure out why this happens sometimes.
			// assert(mWasDownloaded == false);
			mWasDownloaded = true;
		}
		
		/**
		 * Marks the MessageReference as not downloaded and stores the change in the database, without committing the transaction.
		 */
		public synchronized void clearMessageWasDownloadedFlag() {
			checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
			
			// TODO: Figure out why this happens sometimes.
			// assert(mWasDownloaded == true);
			mWasDownloaded = false;
		}

		public MessageList getMessageList() {
			checkedActivate(1);
			mMessageList.initializeTransient(mFreetalk);
			return mMessageList;
		}
		
		/**
		 * Called by it's parent <code>MessageList</code> to store the reference to it. Does not call store().
		 */
		protected void setMessageList(MessageList myMessageList) {
			checkedActivate(1);
			mMessageList = myMessageList;
		}
		
		@Override
		public String toString() {
			if(mDB == null)
				return "[" + super.toString() + " (cannot get more info because mDB==null) ]";
			else
				return "[" + super.toString() + ": mMessageID: " + getMessageID() + "; mMessageURI: " + getURI() + "]";
		}

	}
	
	// @IndexedField // I can't think of any query which would need to get all MessageListFetchFailedMarker objects.
	public static final class MessageListFetchFailedMarker extends FetchFailedMarker {

		@IndexedField
		private final String mMessageListID;
		
		
		public MessageListFetchFailedMarker(MessageList myMessageList, Reason myReason, Date myDate, Date myDateOfNextRetry) {
			super(myReason, myDate, myDateOfNextRetry);
			
			mMessageListID = myMessageList.getID();
		}
		
		@Override
		public void databaseIntegrityTest() throws Exception {
			super.databaseIntegrityTest();
			
			 checkedActivate(1);
			
			if(mMessageListID == null)
				throw new NullPointerException("mMessageListID==null");
			
			MessageListID.construct(mMessageListID);
		}

		public String getMessageListID() {
			checkedActivate(1); // String is a db4o primitive type so 1 is enough
			return mMessageListID;
		}
		
		@Override
		public String toString() {
			if(mDB == null)
				return "[" + super.toString() + " (cannot get more info because mDB==null) ]";
			else
				return "[" + super.toString() + ": mMessageListID: " + getMessageListID() + "]";
		}
		
	}

	// @IndexedField // I can't think of any query which would need to get all MessageFetchFailedMarker objects.
	public static final class MessageFetchFailedMarker extends FetchFailedMarker {

		@IndexedField
		private final MessageReference mMessageReference;
		
		
		public MessageFetchFailedMarker(MessageReference myMessageReference, Reason myReason, Date myDate, Date myDateOfNextRetry) {
			super(myReason, myDate, myDateOfNextRetry);
			
			mMessageReference = myMessageReference;
		}
		
		@Override
		public void databaseIntegrityTest() throws Exception {
			super.databaseIntegrityTest();
			
			checkedActivate(1);
			
			if(mMessageReference == null)
				throw new NullPointerException("mMessageReference==null");
		}
		
		public void storeWithoutCommit() {
			checkedActivate(1);
			throwIfNotStored(mMessageReference);
			super.storeWithoutCommit();
		}

		public MessageReference getMessageReference() {
			checkedActivate(1);
			mMessageReference.initializeTransient(mFreetalk);
			return mMessageReference;
		}
		
		@Override
		public String toString() {
			return "[" + super.toString() + ": [" + (mDB != null ? getMessageReference() : "(cannot get because mDB==null)") +"] ]";
		}
	}


	/**
	 * 
	 * @param identityManager
	 * @param myURI
	 * @param newMessages A list of the messages. If a message is posted to multiple boards, the list should contain one <code>MessageReference</code> object for each board.
	 * @throws InvalidParameterException
	 * @throws NoSuchIdentityException
	 */
	public MessageList(Freetalk myFreetalk, final Identity myAuthor, final FreenetURI myURI, final List<MessageReference> newMessages)
		throws InvalidParameterException, NoSuchIdentityException {
		
		this(myFreetalk, myAuthor, myURI, new ArrayList<MessageReference>(newMessages));
		
		if(mMessages.size() < 1)
			throw new IllegalArgumentException("Trying to construct a message list with no messages.");
		
		// We must ensure that:
		// - Each message ID is mapped to a unique FreenetURI
		// - A message is not posted to too many boards
		// - No board is specified twice
		// Because one MessageReference is passed in per board we must create a set which contains one object per message ID which associates the URI with the ID
		// and aggregates the list of its boards.
		// We use an ID=>MessageInfo hash table for that, MessageInfo being:
		
		class MessageInfo {
			final FreenetURI uri;
			final HashSet<Board> boards;
			
			public MessageInfo(FreenetURI myURI) {
				uri = myURI;
				boards = new HashSet<Board>(Math.min(newMessages.size(), Message.MAX_BOARDS_PER_MESSAGE) * 2);
			}
			
			public void addBoard(Board board) {
				if(!boards.add(board))
					throw new IllegalArgumentException("Message list contains multiple mappings of the same board to the message " + uri);
				
				if(boards.size() > Message.MAX_BOARDS_PER_MESSAGE)
					throw new IllegalArgumentException("Message list contains too many boards for message " + uri);
			}
		}
		
		HashMap<String, MessageInfo> messages = new HashMap<String, MessageInfo>(newMessages.size() * 2);
		
		for(MessageReference ref : mMessages) {
			ref.initializeTransient(mFreetalk);
			ref.setMessageList(this);
			
			 // Don't use getter methods because initializeTransient does not happen before the constructor
			
			try {
				MessageID.construct(ref.mMessageID).throwIfAuthorDoesNotMatch(myAuthor);
			}
			catch(Exception e) {
				throw new IllegalArgumentException("Trying to create a MessageList which contains a Message with an ID which does not belong to the author of the MessageList");
			}
			
			MessageInfo info = messages.get(ref.mMessageID);
			
			if(info != null) {
				if(info.uri.equals(ref.mURI) == false)
					throw new IllegalArgumentException("Trying to create a MessageList which maps one message ID to multiple URIs: " + ref.getMessageID());
			} else {
				info = new MessageInfo(ref.mURI);
				messages.put(ref.mMessageID, info);
			}
			
			info.addBoard(ref.mBoard);
			
			if(messages.size() > MAX_MESSAGES_PER_MESSAGELIST)
				throw new IllegalArgumentException("Too many messages in message list: " + mMessages.size());
		}
	}
	
	/**
	 * For constructing an empty dummy message list when the download of message list failed.
	 * @param myAuthor
	 * @param myURI
	 */
	public MessageList(Freetalk myFreetalk, Identity myAuthor, FreenetURI myURI) {
		this(myFreetalk, myAuthor, myURI, new ArrayList<MessageReference>(0));
	}
	
	/**
	 * General constructor for being used by public constructors.
	 */
	protected MessageList(Freetalk myFreetalk, Identity myAuthor, FreenetURI myURI, ArrayList<MessageReference> newMessages) {
		initializeTransient(myFreetalk);
		
		if(myURI == null)
			throw new IllegalArgumentException("Trying to construct a MessageList with null URI.");
				
		final MessageListID myID = new MessageListID(myURI);
		myID.throwIfAuthorDoesNotMatch(myAuthor);
		
		mID = myID.toString();
		mAuthor = myAuthor;
		mIndex = myID.getIndex();
		mMessages = newMessages;
	}
	
	protected MessageList(OwnIdentity myAuthor, long newIndex) {
		if(myAuthor == null)
			throw new IllegalArgumentException("Trying to construct a MessageList with no author");
		
		final MessageListID myID = new MessageListID(myAuthor, newIndex);
		
		mID = myID.toString();
		mAuthor = myAuthor;
		mIndex = myID.getIndex();
		mMessages = new ArrayList<MessageReference>(16); /* TODO: Find a reasonable value */
	}
	
	
	public void storeWithoutCommit() {
		try {
			checkedActivate(1);
			
			throwIfNotStored(mAuthor);
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			// mMessages contains MessageReference objects which keep a reference to this MessageList object
			// AND this MessageList keeps a reference to mMessages - so there are mutual references. We first store mMessages because it's
			// the more complex structure. 
			
			// TODO: Change class Persistent to provide a way to specify a store-depth so we can prevent implicit storage of this MessageList object in the
			// following loop .. I hope that the implicit storage does not hurt meanwhile.. I have no sign of it so I'm marking as TO-DO and not FIX-ME,,,
			
			checkedActivate(mMessages, 2);
			
			for(MessageReference ref : mMessages) {
				ref.initializeTransient(mFreetalk);
				ref.storeWithoutCommit();
			}
			
			mDB.store(mMessages, 1); // TODO: Do not use the lowlevel db4o function, rather add something to class Persistent...
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	protected void deleteWithoutCommit() {
		try {
			checkedActivate(1);
			
			{ // First we have to delete the objects of type MessageListFetchFailedReference because this MessageList needs to exist in the db so we can query them
				// TODO: This requires that we have locked the MessageManager, which is currently the case for every call to deleteWithoutCommit()
				// However, we should move the code elsewhere to ensure the locking...
				// TODO: This query should be a function in MessageManager
				Query query = mDB.query();
				query.constrain(MessageListFetchFailedMarker.class);
				query.descend("mMessageListID").constrain(getID());
				
				for(MessageListFetchFailedMarker failedRef : new Persistent.InitializingObjectSet<MessageListFetchFailedMarker>(mFreetalk, query)) {
					failedRef.deleteWithoutCommit();
				}
			}
			
			
			// Then we delete our list of MessageReferences before we delete each of it's MessageReferences 
			// - less work of db4o, it does not have to null all the pointers to them.
			checkedActivate(mMessages, 2);
			MessageReference[] messages = mMessages.toArray(new MessageReference[mMessages.size()]);
			mMessages.clear(); // I don't know why I'm doing this but it seems better to me - it makes clear that we delete the MessageReference objects on our own.
			checkedDelete(mMessages);
			
			for(MessageReference ref : messages) {
				ref.initializeTransient(mFreetalk);
				
				// TODO: This requires that we have locked the MessageManager, which is currently the case for every call to deleteWithoutCommit()
				// However, we should move the code elsewhere to ensure the locking...
				final MessageManager messageManager = mFreetalk.getMessageManager();
				
				// Before deleting the MessageReference itself, we must delete any MessageFetchFailedReference objects which point to it. 
				try {
					messageManager.getMessageFetchFailedMarker(ref).deleteWithoutCommit();
				} catch (NoSuchFetchFailedMarkerException e1) {
				}
				
				// TODO: Its sort of awful to have this code here, maybe find a better place for it :|
				// It's required to prevent zombie message lists.
				try {
					messageManager.get(ref.getMessageID()).clearMessageList();
				} catch(NoSuchMessageException e) {
					
				}
				
				ref.deleteWithoutCommit();
			}
			
			// We delete this at last because each MessageReference object had a pointer to it - less work for db4o, it doesn't have to null them all
			checkedDelete();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
	
	public String getID() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		return mID;
	}
	
	/**
	 * Get the SSK URI of this message list.
	 * @return
	 */
	public FreenetURI getURI() {
		return generateURI(getAuthor().getRequestURI(), getIndex()).sskForUSK();
	}
	
	/**
	 * Get the USK URI of a message list with the given base URI and index.
	 * @param baseURI
	 * @param index
	 * @return
	 */
	protected abstract FreenetURI generateURI(FreenetURI baseURI, long index);
	
	public Identity getAuthor() {
		checkedActivate(1);
		if(mAuthor instanceof Persistent) {
			((Persistent)mAuthor).initializeTransient(mFreetalk);
		}
		return mAuthor;
	}
	
	public long getIndex() {
		checkedActivate(1); // long is a db4o primitive type so 1 is enough
		return mIndex;
	}
	
	/**
	 * You have to synchronize on the <code>MessageList</code> when using this method.
	 */
	public Iterator<MessageReference> iterator() {
		return getMessages().iterator();
	}
	
	protected ArrayList<MessageReference> getMessages() {
		checkedActivate(1);
		checkedActivate(mMessages, 2);
		for(MessageReference ref : mMessages) {
			ref.initializeTransient(mFreetalk);
		}
		return mMessages;
	}
	
	public synchronized MessageReference getReference(FreenetURI messageURI) throws NoSuchMessageException {
		for(MessageReference ref : this) {
			if(ref.getURI().equals(messageURI))
				return ref;
		}
		
		throw new NoSuchMessageException();
	}
	
	public String toString() {
		if(mDB != null)
			return getURI().toString();
		
		// We do not throw a NPE because toString() is usually used in logging, we want the logging to be robust
		
		Logger.error(this, "toString() called before initializeTransient()!");
		
		return super.toString() + " (intializeTransient() not called!, ID may be null, here it is: " + mID + ")";
	}

}
