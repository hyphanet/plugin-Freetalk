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
import plugins.Freetalk.WoT.WoTMessageList;
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
	 */
	public abstract OwnMessage postMessage(Message myParentMessage, Set<Board> myBoards, Board myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, String myText, List<Attachment> myAttachments) throws InvalidParameterException;

	public synchronized OwnMessage postMessage(Message myParentMessage, Set<String> myBoards, String myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, String myText, List<Attachment> myAttachments) throws InvalidParameterException {

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
	

	public synchronized int countUnsentMessages() {
		/* FIXME: This is not fully synchronized: MessageInserter calls OwnMessage.wasInserted() to mark a message as inserted and that
		 * function synchronizes on the OwnMessage object. */
		Query q = db.query();
		q.constrain(OwnMessage.class);
		q.descend("iWasInserted").constrain(false);
		return q.execute().size();
	}
	
	public synchronized void onMessageReceived(Message message) {
		try {
			get(message.getID());
			Logger.debug(this, "Downloaded a message which we already have: " + message.getURI());
		}
		catch(NoSuchMessageException e) {
			message.initializeTransient(db, this);
			message.store();
			for(Board board : message.getBoards())
				board.addMessage(message);
		}
	}
	
	public synchronized void onMessageListReceived(WoTMessageList list) {
		try {
			getMessageList(list.getID());
			Logger.debug(this, "Downloaded a MessageList which we already have: " + list.getURI());
		}
		catch(NoSuchMessageListException e) {
			list.initializeTransient(db);
			list.store();
		}
	}

	/**
	 * Get a message by its URI. The transient fields of the returned message will be initialized already.
	 * This will NOT return OwnMessage objects. Your own messages will be returned by this function as soon as they have been downloaded.
	 * @throws NoSuchMessageException 
	 */
//	public Message get(FreenetURI uri) throws NoSuchMessageException {
//		return get(Message.getIDFromURI(uri));
//	}
	
	/**
	 * Get a message by its ID. The transient fields of the returned message will be initialized already.
	 * This will NOT return OwnMessage objects. Your own messages will be returned by this function as soon as they have been downloaded as
	 * if they were normal messages of someone else.
	 * @throws NoSuchMessageException 
	 */
	public synchronized Message get(String id) throws NoSuchMessageException {
		Query query = db.query();
		query.constrain(Message.class);
		query.constrain(OwnMessage.class).not();
		query.descend("mID").constrain(id);
		ObjectSet<Message> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateMessageException();
		
		if(result.size() == 0)
			throw new NoSuchMessageException();

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
	public synchronized MessageList getMessageList(String id) throws NoSuchMessageListException {
		Query query = db.query();
		query.constrain(MessageList.class);
		query.constrain(OwnMessageList.class).not();
		query.descend("mID").constrain(id);
		ObjectSet<MessageList> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateMessageListException();
		
		if(result.size() == 0)
			throw new NoSuchMessageListException();

		MessageList m = result.next();
		m.initializeTransient(db);
		return m;
	}
	
	public synchronized int getUnavailableNewMessageListIndex(FTIdentity identity) {
		Query query = db.query();
		query.constrain(MessageList.class);
		query.constrain(OwnMessageList.class).not();
		query.descend("mIndex").orderDescending(); /* FIXME: This is inefficient! Use a native query instead, we just need to find the maximum */
		ObjectSet<MessageList> result = query.execute();
		
		if(result.size() == 0)
			return 0;
		
		return result.next().getIndex() + 1;
	}
	
	public synchronized int getUnavailableOldMessageListIndex(FTIdentity identity) {
		Query query = db.query();
		query.constrain(MessageList.class);
		query.constrain(OwnMessageList.class).not();
		query.descend("mIndex").orderDescending(); /* FIXME: This is inefficient! Use a native query instead */
		ObjectSet<MessageList> result = query.execute();
		
		if(result.size() == 0)
			return 0;
		
		int latestAvailableIndex = result.next().getIndex();
		int freeIndex = latestAvailableIndex - 1;
		for(; result.hasNext() && result.next().getIndex() == freeIndex; ) {
			--freeIndex;
		}
		
		/* FIXME: To avoid always checking ALL messagelists for a missing one, store somewhere in the FTIdentity what the latest index is up to
		 * which all messagelists are available! */
		
		return freeIndex>0 ? freeIndex : latestAvailableIndex+1;
	}
	
	public synchronized OwnMessageList getOwnMessageList(String id) throws NoSuchMessageListException {
		Query query = db.query();
		query.constrain(OwnMessageList.class);
		query.descend("mID").constrain(id);
		ObjectSet<OwnMessageList> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateMessageListException();
		
		if(result.size() == 0)
			throw new NoSuchMessageListException();

		OwnMessageList m = result.next();
		m.initializeTransient(db);
		return m;
	}
	
//	public OwnMessage getOwnMessage(FreenetURI uri) throws NoSuchMessageException {
//		return getOwnMessage(Message.getIDFromURI(uri));
//	}
	
	public synchronized OwnMessage getOwnMessage(String id) throws NoSuchMessageException {
		Query query = db.query();
		query.constrain(OwnMessage.class);
		query.descend("mID").constrain(id);
		ObjectSet<OwnMessage> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateMessageException();
		
		if(result.size() == 0)
			throw new NoSuchMessageException();

		OwnMessage m = result.next();
		m.initializeTransient(db, this);
		return m;
	}

	/**
	 * Get a board by its name. The transient fields of the returned board will be initialized already.
	 * @throws NoSuchBoardException 
	 */
	public synchronized Board getBoardByName(String name) throws NoSuchBoardException {
		Query query = db.query();
		query.constrain(Board.class);
		query.descend("mName").constrain(name);
		ObjectSet<Board> result = query.execute();

		if(result.size() > 1)
			throw new DuplicateBoardException();

		if(result.size() == 0)
			throw new NoSuchBoardException();
		
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
	 * Get the next free index for an OwnMessage. Please synchronize on OwnMessage.class while creating a message, this method does not
	 * provide synchronization.
	 */
//	public int getFreeMessageIndex(FTOwnIdentity messageAuthor)  {
//		Query q = db.query();
//		q.constrain(Message.class);
//		q.descend("mAuthor").constrain(messageAuthor);
//		q.descend("mIndex").orderDescending(); /* FIXME: Write a native db4o query which just looks for the maximum! */
//		ObjectSet<Message> result = q.execute();
//		
//		return result.size() > 0 ? result.next().getIndex()+1 : 0;
//	}
	
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
	
	public synchronized Iterator<OwnMessage> notInsertedMessageIterator() {
		return new Iterator<OwnMessage>() {
			private Iterator<OwnMessage> iter;

			{
				Query query = db.query();
				query.constrain(OwnMessage.class);
				query.descend("iWasInserted").constrain(false);
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
	
	public synchronized Iterator<MessageList> notDownloadedMessageListIterator() {
		return new Iterator<MessageList>() {
			private Iterator<MessageList> iter;

			{
				Query query = db.query();
				query.constrain(MessageList.class);
				query.constrain(OwnMessageList.class).not();
				query.descend("mNumberOfNotDownloadedMessages").constrain(0).not();
				/* FIXME: Order the message lists by something random */
				iter = query.execute().iterator();
			}

			public boolean hasNext() {
				return iter.hasNext();
			}

			public MessageList next() {
				MessageList next = iter.next();
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
