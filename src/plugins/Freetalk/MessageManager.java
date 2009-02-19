/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.MessageList.MessageReference;
import plugins.Freetalk.exceptions.DuplicateBoardException;
import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.DuplicateMessageListException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.Executor;
import freenet.support.Logger;

/**
 * @author xor
 *
 */
public abstract class MessageManager implements Runnable {
	
	protected final MessageManager self = this;

	protected ObjectContainer db;
	
	protected Executor mExecutor;

	protected IdentityManager mIdentityManager;

	public MessageManager(ObjectContainer myDB, Executor myExecutor, IdentityManager myIdentityManager) {
		assert(myDB != null);
		assert(myIdentityManager != null);

		db = myDB;
		mExecutor = myExecutor;
		mIdentityManager = myIdentityManager;
	}
	
	public MessageManager(ObjectContainer myDB) {
		db = myDB;
		mExecutor = null;
		mIdentityManager = null;
	}
	
	/**
	 * This is the primary function for posting messages.
	 * 
	 * @param myParentMessage The message to which the new message is a reply. Null if the message should be a thread.
	 * @param myBoards The boards to which the new message should be posted. Has to contain at least one board.
	 * @param myReplyToBoard The board to which replies to this message should be sent. This is just a recommendation. Notice that it should be contained in myBoards. Can be null.
	 * @param myAuthor The author of the new message. Cannot be null.
	 * @param myTitle The subject of the new message. Cannot be null or empty.
	 * @param myText The body of the new message. Cannot be null.
	 * @param myAttachments The Attachments of the new Message. See <code>Message.Attachment</code>. Set to null if the message has none.
	 * @return The new message.
	 * @throws InvalidParameterException Invalid board names, invalid title, invalid body.
	 * @throws Exception 
	 */
	public abstract OwnMessage postMessage(Message myParentMessage, Set<Board> myBoards, Board myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, String myText, List<Attachment> myAttachments) throws InvalidParameterException, Exception;

	public synchronized OwnMessage postMessage(Message myParentMessage, Set<String> myBoards, String myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, String myText, List<Attachment> myAttachments) throws Exception {

		/* FIXME: Instead of always creating the boards, notify the user that they do not exist and ask if he made a typo */
		HashSet<Board> boardSet = new HashSet<Board>();
		for (Iterator<String> i = myBoards.iterator(); i.hasNext(); ) {
			String boardName = i.next();
			Board board = getOrCreateBoard(boardName);
			boardSet.add(board);
		}

		Board replyToBoard = null;
		if (myReplyToBoard != null) {
			replyToBoard = getOrCreateBoard(myReplyToBoard);
		}

		return postMessage(myParentMessage, boardSet, replyToBoard, myAuthor, myTitle, myText, myAttachments);
	}
	

	@SuppressWarnings("unchecked")
	public synchronized int countUnsentMessages() {
		/* FIXME: This is not fully synchronized: MessageInserter calls OwnMessage.wasInserted() to mark a message as inserted and that
		 * function synchronizes on the OwnMessage object. */
		Query q = db.query();
		q.constrain(OwnMessage.class);
		q.descend("mRealURI").constrain(null).identity();
		int unsentCount = q.execute().size();
		
		q = db.query();
		q.constrain(OwnMessageList.class);
		q.descend("iWasInserted").constrain(false);
		ObjectSet<OwnMessageList> notInsertedLists = q.execute();
		for(OwnMessageList list : notInsertedLists)
			unsentCount += list.getMessageCount();
		
		return unsentCount;
	}
	
	public synchronized void onMessageReceived(Message message) {
		try {
			get(message.getID());
			Logger.debug(this, "Downloaded a message which we already have: " + message.getURI());
		}
		catch(NoSuchMessageException e) {
			try {
				message.initializeTransient(db, this);
				message.store();
				for(Board board : message.getBoards())
					board.addMessage(message);
				
				for(MessageReference ref : getAllReferencesToMessage(message.getID()))
					ref.setMessageWasDownloadedFlag();
			}
			catch(Exception ex) {
				/* FIXME: Delete the message if this happens. */
				Logger.error(this, "Exception while storing a downloaded message", ex);
			}
		}
	}
	
	public synchronized void onMessageListReceived(MessageList list) {
		try {
			getMessageList(list.getID());
			Logger.debug(this, "Downloaded a MessageList which we already have: " + list.getURI());
		}
		catch(NoSuchMessageListException e) {
			list.initializeTransient(db, this);
			list.store();
		}
	}
	
	/**
	 * Abstract because we need to store an object of a child class of MessageList which is chosen dependent on which implementation of the
	 * messging system we are using.
	 */
	public abstract void onMessageListFetchFailed(FTIdentity author, FreenetURI uri, MessageList.MessageListFetchFailedReference.Reason reason);
	
	public synchronized void onMessageFetchFailed(MessageReference messageReference, MessageList.MessageFetchFailedReference.Reason reason) {
		if(reason == MessageList.MessageFetchFailedReference.Reason.DataNotFound) {
			/* TODO: Handle DNF in some reasonable way. Mark the Messages as unavailable after a certain amount of retries maybe */
			return;
		}

		try {
			get(messageReference.getMessageID());
			Logger.debug(this, "Trying to mark a message as 'downlod failed' which we actually have: " + messageReference.getURI());
		}
		catch(NoSuchMessageException e) {
			try {
				MessageList.MessageFetchFailedReference failedMarker = new MessageList.MessageFetchFailedReference(messageReference, reason);
				failedMarker.initializeTransient(db);
				failedMarker.store();
				for(MessageReference r : getAllReferencesToMessage(messageReference.getMessageID()))
					r.setMessageWasDownloadedFlag();
				Logger.debug(this, "Marked message as download failed with reason " + reason + ": " +  messageReference.getURI());
			}
			catch(Exception ex) {
				Logger.error(this, "Exception while marking a not-downloadable messge", ex);
			}
		}
	}
	
	/**
	 * Get a list of all MessageReference objects to the given message ID. References to OwnMessage are not returned.
	 * Used to mark the references to a message which was downloaded as downloaded.
	 */
	private Iterable<MessageList.MessageReference> getAllReferencesToMessage(final String id) {
		return new Iterable<MessageList.MessageReference>() {
			@SuppressWarnings("unchecked")
			public Iterator<MessageList.MessageReference> iterator() {
				return new Iterator<MessageList.MessageReference>() {
					private Iterator<MessageList.MessageReference> iter;

					{
						Query query = db.query();
						query.constrain(MessageList.MessageReference.class);
						query.constrain(OwnMessageList.OwnMessageReference.class).not();
						query.descend("mMessageID").constrain(id);
						/* FIXME: This function should only return MessageReferences which are for a board which an OwnIdentity wants to read and from 
						 * as specified above. This has to be implemented yet. */
						/* FIXME: Order the message references randomly with some trick. */
						iter = query.execute().iterator();
					}

					public boolean hasNext() {
						return iter.hasNext();
					}

					public MessageList.MessageReference next() {
						MessageList.MessageReference next = iter.next();
						next.initializeTransient(db);
						return next;
					}

					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	/**
	 * Get a message by its URI. The transient fields of the returned message will be initialized already.
	 * This will NOT return OwnMessage objects. Your own messages will be returned by this function as soon as they have been downloaded.
	 * @throws NoSuchMessageException 
	 */
	public Message get(FreenetURI uri) throws NoSuchMessageException {
		/* return get(Message.getIDFromURI(uri)); */
		throw new UnsupportedOperationException("Getting a message by it's URI is inefficient compared to getting by ID. Please only repair this function if absolutely unavoidable.");
	}
	
	/**
	 * Get a message by its ID. The transient fields of the returned message will be initialized already.
	 * This will NOT return OwnMessage objects. Your own messages will be returned by this function as soon as they have been downloaded as
	 * if they were normal messages of someone else.
	 * @throws NoSuchMessageException 
	 */
	@SuppressWarnings("unchecked")
	public synchronized Message get(String id) throws NoSuchMessageException {
		Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mID").constrain(id);
		ObjectSet<Message> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateMessageException();
		
		if(result.size() == 0)
			throw new NoSuchMessageException(id);

		Message m = result.next();
		m.initializeTransient(db, this);
		return m;
	}
	
	/**
	 * Get a <code>MessageList</code> by its ID. The transient fields of the returned <code>MessageList</code>  will be initialized already.
	 * This will NOT return <code>OwnMessageList</code> objects. Your own message lists will be returned by this function as soon as they have
	 * been downloaded as if they were normal message  lists of someone else.
	 * @throws NoSuchMessageListException 
	 */
	@SuppressWarnings("unchecked")
	public synchronized MessageList getMessageList(String id) throws NoSuchMessageListException {
		Query query = db.query();
		query.constrain(MessageList.class);
		query.constrain(OwnMessageList.class).not();
		query.descend("mID").constrain(id);
		ObjectSet<MessageList> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateMessageListException();
		
		if(result.size() == 0)
			throw new NoSuchMessageListException(id);

		MessageList list = result.next();
		list.initializeTransient(db, this);
		return list;
	}
	
	@SuppressWarnings("unchecked")
	public synchronized OwnMessageList getOwnMessageList(String id) throws NoSuchMessageListException {
		Query query = db.query();
		query.constrain(OwnMessageList.class);
		query.descend("mID").constrain(id);
		ObjectSet<OwnMessageList> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateMessageListException();
		
		if(result.size() == 0)
			throw new NoSuchMessageListException(id);

		OwnMessageList list = result.next();
		list.initializeTransient(db, this);
		return list;
	}
	
	public OwnMessage getOwnMessage(FreenetURI uri) throws NoSuchMessageException {
		/* return getOwnMessage(Message.getIDFromURI(uri)); */
		throw new UnsupportedOperationException("Getting a message by it's URI is inefficient compared to getting by ID. Please only repair this function if absolutely unavoidable.");
	}
	
	@SuppressWarnings("unchecked")
	public synchronized OwnMessage getOwnMessage(String id) throws NoSuchMessageException {
		Query query = db.query();
		query.constrain(OwnMessage.class);
		query.descend("mID").constrain(id);
		ObjectSet<OwnMessage> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateMessageException();
		
		if(result.size() == 0)
			throw new NoSuchMessageException(id);

		OwnMessage m = result.next();
		m.initializeTransient(db, this);
		return m;
	}

	/**
	 * Get a board by its name. The transient fields of the returned board will be initialized already.
	 * @throws NoSuchBoardException 
	 */
	@SuppressWarnings("unchecked")
	public synchronized Board getBoardByName(String name) throws NoSuchBoardException {
		Query query = db.query();
		query.constrain(Board.class);
		query.descend("mName").constrain(name);
		ObjectSet<Board> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateBoardException();

		if(result.size() == 0)
			throw new NoSuchBoardException(name);
		
		Board b = result.next();
		b.initializeTransient(db, this);
		return b;
	}
	
	public synchronized Board getOrCreateBoard(String name) throws InvalidParameterException {
		Board board;

		try {		
			board = getBoardByName(name);
		}
		catch(NoSuchBoardException e) {
			board = new Board(name);
			board.initializeTransient(db, this);
			board.store();
		}
		
		return board;
	}
	
	/**
	 * For a database Query of result type <code>ObjectSet\<Board\></code>, this function provides an iterator. The iterator of the ObjectSet
	 * cannot be used instead because it will not call initializeTransient() on the boards. The iterator which is returned by this function
	 * takes care of that.
	 * Please synchronize on the <code>MessageManager</code> when using this function, it is not synchronized itself.
	 */
	@SuppressWarnings("unchecked")
	protected Iterator<Board> generalBoardIterator(final Query q) {
		return new Iterator<Board>() {
			private Iterator<Board> iter = q.execute().iterator();
			
			public boolean hasNext() {
				return iter.hasNext();
			}

			public Board next() {
				Board next = iter.next();
				next.initializeTransient(db, self);
				return next;
			}

			public void remove() {
				throw new UnsupportedOperationException("Boards cannot be deleted yet.");
			}
			
		};
	}

	/**
	 * Get an iterator of all boards. The transient fields of the returned boards will be initialized already.
	 */
	public synchronized Iterator<Board> boardIterator() {
		/* FIXME: Accelerate this query. db4o should be configured to keep an alphabetic index of boards */
		Query query = db.query();
		query.constrain(Board.class);
		query.descend("mName").orderDescending();
		return generalBoardIterator(query);
	}
	
	/**
	 * Get an iterator of boards which were first seen after the given Date, sorted ascending by the date they were first seen at.
	 */
	public synchronized Iterator<Board> boardIteratorSortedByDate(final Date seenAfter) {
		Query query = db.query();
		query.constrain(Board.class);
		query.descend("mFirstSeenDate").constrain(seenAfter).greater();
		query.descend("mFirstSeenDate").orderAscending();
		return generalBoardIterator(query);
	}
	
	/**
	 * Get the next index of which a message from the selected identity is not stored.
	 */
//	public int getUnavailableMessageIndex(FTIdentity messageAuthor) {
//		Query q = db.query();
//		q.constrain(Message.class);
//		q.constrain(OwnMessage.class).not(); /* We also download our own message. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
//		q.descend("mAuthor").constrain(messageAuthor);
//		q.descend("mIndex").orderDescending(); /* FIXME: Write a native db4o query which just looks for the maximum! */
//		ObjectSet<Message> result = q.execute();
//		
//		return result.size() > 0 ? result.next().getIndex()+1 : 0;
//	}
	
	@SuppressWarnings("unchecked")
	public synchronized Iterator<OwnMessage> notInsertedMessageIterator() {
		return new Iterator<OwnMessage>() {
			private Iterator<OwnMessage> iter;

			{
				Query query = db.query();
				query.constrain(OwnMessage.class);
				query.descend("mRealURI").constrain(null).identity();
				iter = query.execute().iterator();
			}
			
			public boolean hasNext() {
				return iter.hasNext();
			}

			public OwnMessage next() {
				OwnMessage next = iter.next();
				next.initializeTransient(db, self);
				return next;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	/**
	 * Get a list of not downloaded messages. This function only returns messages which are posted to a board which an OwnIdentity wants to
	 * receive messages from. However, it might also return messages which are from an author which nobody wants to receive messages from.
	 * Filtering out unwanted authors is done at MessageList-level: MessageLists are only downloaded from identities which we want to read
	 * messages from.
	 */
	@SuppressWarnings("unchecked")
	public synchronized Iterator<MessageList.MessageReference> notDownloadedMessageIterator() {
		return new Iterator<MessageList.MessageReference>() {
			private Iterator<MessageList.MessageReference> iter;

			{
				Query query = db.query();
				query.constrain(MessageList.MessageReference.class);
				query.constrain(OwnMessageList.OwnMessageReference.class).not();
				query.descend("iWasDownloaded").constrain(false);
				/* FIXME: This function should only return MessageReferences which are for a board which an OwnIdentity wants to read and from 
				 * as specified above. This has to be implemented yet. */
				/* FIXME: Order the message references randomly with some trick. */
				iter = query.execute().iterator();
			}

			public boolean hasNext() {
				return iter.hasNext();
			}

			public MessageList.MessageReference next() {
				MessageList.MessageReference next = iter.next();
				next.initializeTransient(db);
				return next;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Returns true if the message was not downloaded yet and any of the FTOwnIdentity wants the message.
	 */
//	protected synchronized boolean shouldDownloadMessage(FreenetURI uri, FTIdentity author) {
//		try {
//			get(uri);
//			return false;
//		}
//		catch(NoSuchMessageException e) {
//			return mIdentityManager.anyOwnIdentityWantsMessagesFrom(author);
//		}
//	}
	
	public abstract void terminate();

	public IdentityManager getIdentityManager() {
		return mIdentityManager;
	}

}
