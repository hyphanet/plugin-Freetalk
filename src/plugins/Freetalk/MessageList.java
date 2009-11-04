/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.Base64;

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
 */
public abstract class MessageList implements Iterable<MessageList.MessageReference> {
	
	protected String mID; /* Not final because OwnMessageList.incrementInsertIndex() might need to change it */
	
	protected final FTIdentity mAuthor;
	
	protected int mIndex; /* Not final because OwnMessageList.incrementInsertIndex() might need to change it */
	
	
	public static String[] getIndexedFields() {
		return new String[] { "mID", "mAuthor", "mIndex" };
	}
	
	
	/**
	 * Stores the <code>FreenetURI</code> and the <code>Board</code> of a not downloaded message. If a message is posted to multiple boards, a
	 * <code>MessageReference</code> is stored for each board. This is done to allow querying the database for <code>MessageReference</code>
	 * objects which belong to a certain board - which is necessary because we only want to download messages from boards to which the
	 * user is actually subscribed.
	 */
	public static class MessageReference {
		
		private MessageList mMessageList = null;
		
		private final String mMessageID;
		
		private final FreenetURI mURI; 
		
		private final Board mBoard;
		
		private boolean iWasDownloaded = false;
		
		public static String[] getIndexedFields() {
			return new String[] { "mMessageID", "mBoard", "iWasDownloaded" };
		}
		
		public MessageReference(String newMessageID, FreenetURI newURI, Board myBoard) {
			if(newMessageID == null || newURI == null || (myBoard == null && !(this instanceof OwnMessageList.OwnMessageReference)))
				throw new IllegalArgumentException(); /* TODO: Be more verbose */
			
			mMessageID = newMessageID;
			mURI = newURI;
			mBoard = myBoard;
		}
		
		private transient ExtObjectContainer db;
		
		protected void initializeTransient(ExtObjectContainer myDB) {
			db = myDB;
		}
		
		protected void storeWithoutCommit() {
			try {
				DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
				
				// We cannot throwIfNotStored because MessageReference objects are usually created within the same transaction of creating the MessageList
				//DBUtil.throwIfNotStored(db, mMessageList);
				
				// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !

				db.store(mURI);
				db.store(this);
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(db, this, e);
			}
		}
		
		public void deleteWithoutCommit() {
			try {
				DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
				
				DBUtil.checkedDelete(db, this);
				
				mURI.removeFrom(db);
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(db, this, e);
			}
		}
		
		/**
		 * Returns null, implemented only in OwnMessageList.OwnMessageReference.
		 */
		public String getMessageID() {
			return mMessageID;
		}
		
		public FreenetURI getURI() {
			return mURI;
		}
		
		public Board getBoard() {
			return mBoard;
		}
		
		public synchronized boolean wasMessageDownloaded() {
			return iWasDownloaded;
		}
		
		/**
		 * Marks the MessageReference as downloaded and stores the change in the database, without committing the transaction.
		 */
		public synchronized void setMessageWasDownloadedFlag() {
			assert(iWasDownloaded == false);
			iWasDownloaded = true;
			storeWithoutCommit();
		}
		
		/**
		 * Marks the MessageReference as not downloaded and stores the change in the database, without committing the transaction.
		 */
		public synchronized void clearMessageWasDownloadedFlag() {
			assert(iWasDownloaded == true);
			iWasDownloaded = false;
			storeWithoutCommit();
		}

		public MessageList getMessageList() {
			db.activate(this, 3);
			return mMessageList;
		}
		
		/**
		 * Called by it's parent <code>MessageList</code> to store the reference to it. Does not call store().
		 */
		protected void setMessageList(MessageList myMessageList) {
			mMessageList = myMessageList;
		}
		
	}
	
	public static final class MessageListFetchFailedMarker extends FetchFailedMarker {

		private final String mMessageListID;
		
		public static String[] getIndexedFields() {
			return new String[] { "mMessageListID" };
		}
		
		public MessageListFetchFailedMarker(MessageList myMessageList, Reason myReason, Date myDate, Date myDateOfNextRetry) {
			super(myReason, myDate, myDateOfNextRetry);
			
			mMessageListID = myMessageList.getID();
		}

		public String getMessageListID() {
			return mMessageListID;
		}
		
	}

	public static final class MessageFetchFailedMarker extends FetchFailedMarker {

		private final MessageReference mMessageReference;
		
		public static String[] getIndexedFields() {
			return new String[] { "mMessageReference" };
		}
		
		public MessageFetchFailedMarker(MessageReference myMessageReference, Reason myReason, Date myDate, Date myDateOfNextRetry) {
			super(myReason, myDate, myDateOfNextRetry);
			
			mMessageReference = myMessageReference;
		}
		
		public void storeWithoutCommit() {
			DBUtil.throwIfNotStored(mDB, mMessageReference);
			super.storeWithoutCommit();
		}

		public MessageReference getMessageReference() {
			mMessageReference.initializeTransient(mDB);
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
	public MessageList(FTIdentity myAuthor, FreenetURI myURI, List<MessageReference> newMessages) throws InvalidParameterException, NoSuchIdentityException {
		this(myAuthor, myURI, new ArrayList<MessageReference>(newMessages));
		
		if(mMessages.size() < 1)
			throw new IllegalArgumentException("Trying to construct a message list with no messages.");
		
		Hashtable<String, FreenetURI> messageURIs = new Hashtable<String, FreenetURI>(newMessages.size());
		
		/* FIXME: 1. Limit the amount of MessageReferences. 2. Limit the amount of boards a single message can be posted to by counting
		 * the number of occurrences of a single FreenetURI in the MessageReference list. 3. Ensure that no (FreenetURI, Board) pair is twice
		 * or more in the list, this would be a DoS attempt. See also the constraints list at the top of this file.
		 * - NOTE: Currently, WoTMessageListXML ensures (3) by using a HashSet<Board>. */
		for(MessageReference ref : mMessages) {
			ref.setMessageList(this);
			
			try {
				Message.verifyID(mAuthor, ref.getMessageID());
			}
			catch(InvalidParameterException e) {
				throw new IllegalArgumentException("Trying to create a MessageList which contains a Message with an ID which does not belong to the author of the MessageList");
			}
			
			FreenetURI previousURI = messageURIs.put(ref.getMessageID(), ref.getURI());
			if(previousURI != null && previousURI.equals(ref.getURI()) == false)
				throw new IllegalArgumentException("Trying to create a MessageList which maps one message ID to multiple URIs");
				
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
	
	protected transient ExtObjectContainer db;
	protected transient MessageManager mMessageManager;

	public void initializeTransient(ExtObjectContainer myDB, MessageManager myMessageManager) {
		db = myDB;
		mMessageManager = myMessageManager;
	}
	
	public synchronized void storeWithoutCommit() {
		try {
			DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
			
			DBUtil.throwIfNotStored(db, mAuthor);
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			// mMessages contains MessageReference objects which keep a reference to this MessageList object
			// AND this MessageList keeps a reference to mMessages - so there are mutual references. We first store mMessages because it's
			// the more complex structure. FIXME: I hope that the implicit storage of this MessageList by ref.storeWithoutCommit() does not hurt?
			
			for(MessageReference ref : mMessages) {
				ref.initializeTransient(db);
				ref.storeWithoutCommit();
			}
			db.store(mMessages, 1);
			db.store(this);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(db, this, e);
		}
	}
	
	@SuppressWarnings("unchecked")
	protected synchronized void deleteWithoutCommit() {
		try {
			DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
			
			{ // First we have to delete the objects of type MessageListFetchFailedReference because this MessageList needs to exist in the db so we can query them
				// TODO: This requires that we have locked the MessageManager, which is currently the case for every call to deleteWithoutCommit()
				// However, we should move the code elsewhere to ensure the locking...
				Query query = db.query();
				query.constrain(MessageListFetchFailedMarker.class);
				query.descend("mMessageListID").constrain(getID());
				
				for(MessageListFetchFailedMarker failedRef : (ObjectSet<MessageListFetchFailedMarker>)query.execute()) {
					failedRef.initializeTransient(db, mMessageManager);
					failedRef.deleteWithoutCommit();
				}
			}
			
			
			// Then we delete our list of MessageReferences before we delete each of it's MessageReferences 
			// - less work of db4o, it does not have to null all the pointers to them.
			DBUtil.checkedDelete(db, mMessages);
			
			for(MessageReference ref : mMessages) {
				// TODO: This requires that we have locked the MessageManager, which is currently the case for every call to deleteWithoutCommit()
				// However, we should move the code elsewhere to ensure the locking...
				Query query = db.query();
				query.constrain(MessageFetchFailedMarker.class);
				query.descend("mMessageReference").constrain(ref).identity();
				
				// Before deleting the MessageReference itself, we must delete any MessageFetchFailedReference objects which point to it. 
				for(MessageFetchFailedMarker failedRef : (ObjectSet<MessageFetchFailedMarker>)query.execute()) {
					failedRef.initializeTransient(db, mMessageManager);
					failedRef.deleteWithoutCommit();
				}
				
				ref.initializeTransient(db);
				ref.deleteWithoutCommit();
			}
			
			// We delete this at last because each MessageReference object had a pointer to it - less work for db4o, it doesn't have to null them all
			DBUtil.checkedDelete(db, this);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(db, this, e);
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
		return mID;
	}
	
	/**
	 * Get the SSK URI of this message list.
	 * @return
	 */
	public FreenetURI getURI() {
		return generateURI(mAuthor.getRequestURI(), mIndex).sskForUSK();
	}
	
	/**
	 * Get the USK URI of a message list with the given base URI and index.
	 * @param baseURI
	 * @param index
	 * @return
	 */
	protected abstract FreenetURI generateURI(FreenetURI baseURI, int index);
	
	public FTIdentity getAuthor() {
		return mAuthor;
	}
	
	public int getIndex() {
		return mIndex;
	}
	
	/**
	 * You have to synchronize on the <code>MessageList</code> when using this method.
	 */
	public Iterator<MessageReference> iterator() {
		return mMessages.iterator();
	}
	
	public synchronized MessageReference getReference(FreenetURI messageURI) throws NoSuchMessageException {
		for(MessageReference ref : this) {
			if(ref.getURI().equals(messageURI))
				return ref;
		}
		
		throw new NoSuchMessageException();
	}

}
