/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.Base64;
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
// @Indexed // I can't think of any query which would need to get all MessageList objects.
public abstract class MessageList extends Persistent implements Iterable<MessageList.MessageReference> {
	
	public static final int MAX_MESSAGES_PER_MESSAGELIST = 256;
	
	
	@Indexed
	protected String mID; /* Not final because OwnMessageList.incrementInsertIndex() might need to change it */
	
	@Indexed
	protected final FTIdentity mAuthor;
	
	@Indexed
	protected int mIndex; /* Not final because OwnMessageList.incrementInsertIndex() might need to change it */
	
	
	/**
	 * Stores the <code>FreenetURI</code> and the <code>Board</code> of a not downloaded message. If a message is posted to multiple boards, a
	 * <code>MessageReference</code> is stored for each board. This is done to allow querying the database for <code>MessageReference</code>
	 * objects which belong to a certain board - which is necessary because we only want to download messages from boards to which the
	 * user is actually subscribed.
	 */
	// @Indexed // I can't think of any query which would need to get all MessageReference objects.
	public static class MessageReference extends Persistent {
		
		private MessageList mMessageList = null;
		
		@Indexed
		private final String mMessageID;
		
		private final FreenetURI mURI; 
		
		@Indexed
		private final Board mBoard;
		
		@Indexed
		private final Date mDate;
		
		@Indexed
		private boolean mWasDownloaded = false;

		
		public MessageReference(String newMessageID, FreenetURI newURI, Board myBoard, Date myDate) {
			if(newMessageID == null || newURI == null || (myBoard == null && !(this instanceof OwnMessageList.OwnMessageReference)))
				throw new IllegalArgumentException(); /* TODO: Be more verbose */
			
			mMessageID = newMessageID;
			mURI = newURI;
			mBoard = myBoard;
			mDate = myDate;
		}
		
		protected void storeWithoutCommit() {
			try {
				checkedActivate(3); // TODO: Figure out a suitable depth.
				
				// We cannot throwIfNotStored because MessageReference objects are usually created within the same transaction of creating the MessageList
				//DBUtil.throwIfNotStored(db, mMessageList);
				
				// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
				if(mURI == null)
					throw new NullPointerException("Should not happen: URI is null for " + this);
				
				checkedStore(mURI);
				checkedStore();
			}
			catch(RuntimeException e) {
				checkedRollbackAndThrow(e);
			}
		}
		
		public void deleteWithoutCommit() {
			try {
				checkedActivate(3); // TODO: Figure out a suitable depth.
				
				checkedDelete();
				
				if(mURI != null)
					mURI.removeFrom(mDB);
				else
					Logger.error(this, "Should not happen: URI is null for " + this);
			}
			catch(RuntimeException e) {
				checkedRollbackAndThrow(e);
			}
		}
		
		public String getMessageID() {
			// checkedActivate(1);
			return mMessageID;
		}
		
		public FreenetURI getURI() {
			checkedActivate(2);
			return mURI;
		}
		
		public Board getBoard() {
			checkedActivate(2);
			return mBoard;
		}
		
		public Date getDate() {
			// checkedActivate(1);
			return mDate;
		}
		
		public synchronized boolean wasMessageDownloaded() {
			// checkedActivate(1);
			return mWasDownloaded;
		}
		
		/**
		 * Marks the MessageReference as downloaded and stores the change in the database, without committing the transaction.
		 */
		public synchronized void setMessageWasDownloadedFlag() {
			// checkedActivate(1);
			
			// TODO: Figure out why this happens sometimes.
			// assert(mWasDownloaded == false);
			mWasDownloaded = true;
		}
		
		/**
		 * Marks the MessageReference as not downloaded and stores the change in the database, without committing the transaction.
		 */
		public synchronized void clearMessageWasDownloadedFlag() {
			// checkedActivate(1);
			
			// TODO: Figure out why this happens sometimes.
			// assert(mWasDownloaded == true);
			mWasDownloaded = false;
		}

		public MessageList getMessageList() {
			checkedActivate(2);
			return mMessageList;
		}
		
		/**
		 * Called by it's parent <code>MessageList</code> to store the reference to it. Does not call store().
		 */
		protected void setMessageList(MessageList myMessageList) {
			mMessageList = myMessageList;
		}
		
	}
	
	// @Indexed // I can't think of any query which would need to get all MessageListFetchFailedMarker objects.
	public static final class MessageListFetchFailedMarker extends FetchFailedMarker {

		@Indexed
		private final String mMessageListID;
		
		
		public MessageListFetchFailedMarker(MessageList myMessageList, Reason myReason, Date myDate, Date myDateOfNextRetry) {
			super(myReason, myDate, myDateOfNextRetry);
			
			mMessageListID = myMessageList.getID();
		}

		public String getMessageListID() {
			// checkedActivate(1);
			return mMessageListID;
		}
		
	}

	// @Indexed // I can't think of any query which would need to get all MessageFetchFailedMarker objects.
	public static final class MessageFetchFailedMarker extends FetchFailedMarker {

		@Indexed
		private final MessageReference mMessageReference;
		
		
		public MessageFetchFailedMarker(MessageReference myMessageReference, Reason myReason, Date myDate, Date myDateOfNextRetry) {
			super(myReason, myDate, myDateOfNextRetry);
			
			mMessageReference = myMessageReference;
		}
		
		public void storeWithoutCommit() {
			checkedActivate(2);
			throwIfNotStored(mMessageReference);
			super.storeWithoutCommit();
		}

		public MessageReference getMessageReference() {
			checkedActivate(2);
			mMessageReference.initializeTransient(mFreetalk);
			return mMessageReference;
		}
	}

	
	protected final ArrayList<MessageReference> mMessages;
	
	/**
	 * 
	 * @param identityManager
	 * @param myURI
	 * @param newMessages A list of the messages. If a message is posted to multiple boards, the list should contain one <code>MessageReference</code> object for each board.
	 * @throws InvalidParameterException
	 * @throws NoSuchIdentityException
	 */
	public MessageList(final FTIdentity myAuthor, final FreenetURI myURI, final List<MessageReference> newMessages)
		throws InvalidParameterException, NoSuchIdentityException {
		
		this(myAuthor, myURI, new ArrayList<MessageReference>(newMessages));
		
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
		
		Hashtable<String, MessageInfo> messages = new Hashtable<String, MessageInfo>(newMessages.size() * 2);
		
		for(MessageReference ref : mMessages) {
			ref.setMessageList(this);
			
			try {
				Message.verifyID(mAuthor, ref.mMessageID); // Don't use getter methods because initializeTransient does not happen before the constructor
			}
			catch(InvalidParameterException e) {
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
	public MessageList(FTIdentity myAuthor, FreenetURI myURI) {
		this(myAuthor, myURI, new ArrayList<MessageReference>(0));
	}
	
	/**
	 * General constructor for being used by public constructors.
	 */
	protected MessageList(FTIdentity myAuthor, FreenetURI myURI, ArrayList<MessageReference> newMessages) {
		if(myURI == null)
			throw new IllegalArgumentException("Trying to construct a MessageList with null URI.");
		
		mIndex = (int) myURI.getEdition();
		if(mIndex < 0)
			throw new IllegalArgumentException("Trying to construct a message list with invalid index " + mIndex);
		
		if(myAuthor == null || Arrays.equals(myAuthor.getRequestURI().getRoutingKey(), myURI.getRoutingKey()) == false)
			throw new IllegalArgumentException("Trying to construct a message list with a wrong author " + myAuthor);
		
		mAuthor = myAuthor;
		mID = calculateID();
		mMessages = newMessages;
	}
	
	protected MessageList(FTOwnIdentity myAuthor, int newIndex) {
		if(myAuthor == null)
			throw new IllegalArgumentException("Trying to construct a MessageList with no author");
		
		if(newIndex < 0)
			throw new IllegalArgumentException("Trying to construct a message list with invalid index " + newIndex);
		
		mAuthor = myAuthor;
		mIndex = newIndex;
		mID = calculateID();
		mMessages = new ArrayList<MessageReference>(16); /* TODO: Find a reasonable value */
	}
	
	
	public void storeWithoutCommit() {
		try {
			checkedActivate(3); // TODO: Figure out a suitable depth.
			
			throwIfNotStored(mAuthor);
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			// mMessages contains MessageReference objects which keep a reference to this MessageList object
			// AND this MessageList keeps a reference to mMessages - so there are mutual references. We first store mMessages because it's
			// the more complex structure. FIXME: I hope that the implicit storage of this MessageList by ref.storeWithoutCommit() does not hurt?
			
			for(MessageReference ref : mMessages) {
				ref.initializeTransient(mFreetalk);
				ref.storeWithoutCommit();
			}
			mDB.store(mMessages, 1);
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void deleteWithoutCommit() {
		try {
			checkedActivate(3); // TODO: Figure out a suitable depth.
			
			{ // First we have to delete the objects of type MessageListFetchFailedReference because this MessageList needs to exist in the db so we can query them
				// TODO: This requires that we have locked the MessageManager, which is currently the case for every call to deleteWithoutCommit()
				// However, we should move the code elsewhere to ensure the locking...
				Query query = mDB.query();
				query.constrain(MessageListFetchFailedMarker.class);
				query.descend("mMessageListID").constrain(getID());
				
				for(MessageListFetchFailedMarker failedRef : (ObjectSet<MessageListFetchFailedMarker>)query.execute()) {
					failedRef.initializeTransient(mFreetalk);
					failedRef.deleteWithoutCommit();
				}
			}
			
			
			// Then we delete our list of MessageReferences before we delete each of it's MessageReferences 
			// - less work of db4o, it does not have to null all the pointers to them.
			checkedDelete(mMessages);
			
			for(MessageReference ref : mMessages) {
				// TODO: This requires that we have locked the MessageManager, which is currently the case for every call to deleteWithoutCommit()
				// However, we should move the code elsewhere to ensure the locking...
				Query query = mDB.query();
				query.constrain(MessageFetchFailedMarker.class);
				query.descend("mMessageReference").constrain(ref).identity();
				
				// Before deleting the MessageReference itself, we must delete any MessageFetchFailedReference objects which point to it. 
				for(MessageFetchFailedMarker failedRef : (ObjectSet<MessageFetchFailedMarker>)query.execute()) {
					failedRef.initializeTransient(mFreetalk);
					failedRef.deleteWithoutCommit();
				}
				
				// TODO: Its sort of awful to have this code here, maybe find a better place for it :|
				// It's required to prevent zombie message lists.
				query = mDB.query();
				query.constrain(Message.class);
				query.descend("mID").constrain(ref.getMessageID());
				for(Message message : new Persistent.InitializingObjectSet<Message>(mFreetalk, query)) {
					message.clearMessageList();
				}
				
				ref.initializeTransient(mFreetalk);
				ref.deleteWithoutCommit();
			}
			
			// We delete this at last because each MessageReference object had a pointer to it - less work for db4o, it doesn't have to null them all
			checkedDelete();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
	
	protected String calculateID() {
		return calculateID(mAuthor, mIndex);
	}
	
	public static String calculateID(FTIdentity author, int index) {
		return index + "@" + Base64.encode(author.getRequestURI().getRoutingKey());
	}
	
	public static String getIDFromURI(FreenetURI uri) {
		return uri.getEdition() + "@" + Base64.encode(uri.getRoutingKey());
	}
	
	public String getID() {
		// checkedActivate(1);
		return mID;
	}
	
	/**
	 * Get the SSK URI of this message list.
	 * @return
	 */
	public FreenetURI getURI() {
		return generateURI(getAuthor().getRequestURI(), mIndex).sskForUSK();
	}
	
	/**
	 * Get the USK URI of a message list with the given base URI and index.
	 * @param baseURI
	 * @param index
	 * @return
	 */
	protected abstract FreenetURI generateURI(FreenetURI baseURI, int index);
	
	public FTIdentity getAuthor() {
		checkedActivate(2);
		if(mAuthor instanceof Persistent) {
			((Persistent)mAuthor).initializeTransient(mFreetalk);
		}
		return mAuthor;
	}
	
	public int getIndex() {
		// checkedActivate(1);
		return mIndex;
	}
	
	/**
	 * You have to synchronize on the <code>MessageList</code> when using this method.
	 */
	public Iterator<MessageReference> iterator() {
		checkedActivate(3);
		for(MessageReference ref : mMessages) {
			ref.initializeTransient(mFreetalk);
		}
		return mMessages.iterator();
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
