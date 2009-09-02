/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Arrays;
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
 */
public abstract class MessageList implements Iterable<MessageList.MessageReference> {
	
	protected String mID; /* Not final because OwnMessageList.incrementInsertIndex() might need to change it */
	
	protected final FTIdentity mAuthor;
	
	protected int mIndex; /* Not final because OwnMessageList.incrementInsertIndex() might need to change it */
	
	
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
	
	/**
	 * When a message list fetch fails we need to mark the message list as fetched to prevent the failed message list from getting into the
	 * fetch queue over and over again. An attacker could insert many message list which have unparseable XML to fill up everyone's fetch queue
	 * otherwise, this would be a denial of service.
	 * 
	 * When marking a message list as fetched even though the fetch failed, we store a MessageListFetchFailedReference so that we can try to 
	 * fetch the message list again in the future. For example when the user installs a new version of the plugin we can fetch all messages list
	 * again with failed XML parsing if the new version has fixed a bug in the XML parser.
	 */
	public static class MessageListFetchFailedReference {

		private final MessageList mMessageList;

		public static enum Reason {
			Unknown,
			DataNotFound,
			ParsingFailed
		}

		private final Reason mReason;
		
		public MessageListFetchFailedReference(MessageList myMessageList, Reason myReason) {
			if(myMessageList == null || myReason == null)
				throw new IllegalArgumentException();
			
			mMessageList = myMessageList;
			mReason = myReason;
		}
	
		private transient ExtObjectContainer db;

		public void initializeTransient(ExtObjectContainer myDB) {
			db = myDB;
		}

		public void storeWithoutCommit() {
			try {
				DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
				
				// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
				
				db.store(this);
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(db, this, e);
			}
		}
		
		protected void deleteWithoutCommit() {
			try {
				DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
				
				DBUtil.checkedDelete(db, this);
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(db, this, e);
			}
		}
		
		public MessageList getMessageList() {
			return mMessageList;
		}
	
		public Reason getReason() {
			return mReason;
		}

	}
	
	/**
	 * When a message fetch fails we need to mark the message reference as fetched to prevent the failed message from getting into the
	 * fetch queue over and over again. An attacker could insert many messages which have unparseable XML to fill up everyone's fetch queue
	 * otherwise, this would be a denial of service.
	 * 
	 * When marking a message as fetched even though the fetch failed, we store a MessageFetchFailedReference so that we can try to 
	 * fetch the message again in the future. For example when the user installs a new version of the plugin we can fetch all messages
	 * again with failed XML parsing if the new version has fixed a bug in the XML parser.
	 */
	public static class MessageFetchFailedReference {

		private final MessageReference mMessageReference;

		public static enum Reason {
			Unknown,
			DataNotFound,
			ParsingFailed
		}

		private final Reason mReason;
		
		public MessageFetchFailedReference(MessageReference myMessageReference, Reason myReason) {
			if(myMessageReference == null || myReason == null)
				throw new IllegalArgumentException();
			
			mMessageReference = myMessageReference;
			mReason = myReason;
		}
	
		private transient ExtObjectContainer db;

		protected void initializeTransient(ExtObjectContainer myDB) {
			db = myDB;
		}

		protected void storeWithoutCommit() {
			try {
				DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
				
				// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
				
				db.store(this);
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(db, this, e);
			}
		}
		
		protected void deleteWithoutCommit() {
			try {
				DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
				
				DBUtil.checkedDelete(db, this);
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(db, this, e);
			}
		}
		
		public MessageReference getMessageReference() {
			return mMessageReference;
		}
	
		public Reason getReason() {
			return mReason;
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
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
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
				Query query = db.query();
				query.constrain(MessageListFetchFailedReference.class);
				query.descend("mMessageList").constrain(this).identity();
				
				for(MessageListFetchFailedReference failedRef : (ObjectSet<MessageListFetchFailedReference>)query.execute()) {
					failedRef.initializeTransient(db);
					failedRef.deleteWithoutCommit();
				}
			}
			
			
			// Then we delete our list of MessageReferences before we delete each of it's MessageReferences 
			// - less work of db4o, it does not have to null all the pointers to dhem.
			DBUtil.checkedDelete(db, mMessages);
			
			for(MessageReference ref : mMessages) {
				Query query = db.query();
				query.constrain(MessageFetchFailedReference.class);
				query.descend("mMessageReference").constrain(ref).identity();
				
				// Before deleting the MessageReference itself, we must delete any MessageFetchFailedReference objects which point to it. 
				for(MessageFetchFailedReference failedRef : (ObjectSet<MessageFetchFailedReference>)query.execute()) {
					failedRef.initializeTransient(db);
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
	
	/*
	public synchronized void markAsDownloaded(FreenetURI uri) {
		// TODO: Figure out whether MessageLists are usually large enough so that we can gain speed by using a Hashtable instead of ArrayList
		for(MessageReference ref : mMessages) {
			if(ref.getURI().equals(uri)) {
				ref.markAsDownloaded();
				return;
			}
		}
	}
	*/
	
}
