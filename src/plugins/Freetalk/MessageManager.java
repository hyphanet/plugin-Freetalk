/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.exceptions.DuplicateBoardException;
import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

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
		Logger.debug(this, "Starting message manager...");
		assert(myDB != null);
		assert(myIdentityManager != null);

		db = myDB;
		mExecutor = myExecutor;
		mIdentityManager = myIdentityManager;
		mExecutor.execute(this, "FT Identity Manager");
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
	 */
	public abstract OwnMessage postMessage(Message myParentMessage, Set<Board> myBoards, Board myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, String myText, List<Attachment> myAttachments);

	public OwnMessage postMessage(Message myParentMessage, Set<String> myBoards, String myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, String myText, List<Attachment> myAttachments) {

		// FIXME: still need to figure out our policy for creating new
		// boards.
		HashSet<Board> boardSet = new HashSet<Board>();
		for (Iterator<String> i = myBoards.iterator(); i.hasNext(); ) {
			String boardName = i.next();
			try {
				Board board = getBoardByName(boardName);
				boardSet.add(board);
			}
			catch (NoSuchBoardException e) {

			}
		}

		Board replyToBoard = null;
		if (myReplyToBoard != null) {
			try {
				replyToBoard = getBoardByName(myReplyToBoard);
			}
			catch (NoSuchBoardException e) {
				// ignore
			}
		}

		return postMessage(myParentMessage, boardSet, replyToBoard, myAuthor, myTitle, myText, myAttachments);
	}

	/**
	 * Get a message by its URI. The transient fields of the returned message will be initialized already.
	 * @throws NoSuchMessageException 
	 */
	public Message get(FreenetURI uri) throws NoSuchMessageException {
		return get(Message.generateID(uri));
	}
	
	public synchronized Message get(String id) throws NoSuchMessageException {
		Query query = db.query();
		query.constrain(Message.class);
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
			board = new Board(this, name);
			board.initializeTransient(db, this);
			board.store();
		}
		
		return board;
	}

	/**
	 * Get an iterator of all boards. The transient fields of the returned boards will be initialized already.
	 */
	public synchronized Iterator<Board> boardIterator() {
		return new Iterator<Board>() {
			private Iterator<Board> iter;
			
			{
				/* FIXME: Accelerate this query. db4o should be configured to keep an alphabetic index of boards */
				Query query = db.query();
				query.constrain(Board.class);
				query.descend("mName").orderDescending();
				iter = query.execute().iterator();
			}

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
	 * Get the next free index for an OwnMessage. Please synchronize on OwnMessage.class while creating a message, this method does not
	 * provide synchronization.
	 */
	public int getFreeMessageIndex(FTOwnIdentity messageAuthor, Date date)  {
		Query q = db.query();
		q.constrain(OwnMessage.class);
		q.descend("mAuthor").constrain(messageAuthor);
		q.descend("mDate").constrain(new Date(date.getYear(), date.getMonth(), date.getDate()));
		q.descend("mIndex").orderDescending(); /* FIXME: Write a native db4o query which just looks for the maximum! */
		ObjectSet<OwnMessage> result = q.execute();
		
		return result.size() > 0 ? result.next().getIndex()+1 : 0;
	}
	
	/**
	 * Get the next index of which a message from the selected identity is not stored.
	 */
	public int getUnavailableMessageIndex(FTIdentity messageAuthor) {
		Query q = db.query();
		q.constrain(Message.class);
		q.descend("mAuthor").constrain(messageAuthor);
		q.descend("mIndex").orderDescending(); /* FIXME: Write a native db4o query which just looks for the maximum! */
		ObjectSet<Message> result = q.execute();
		
		return result.size() > 0 ? result.next().getIndex()+1 : 0;
	}
	
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

	/**
	 * Returns true if the message was not downloaded yet and any of the FTOwnIdentity wants the message.
	 */
	protected synchronized boolean shouldDownloadMessage(FreenetURI uri, FTIdentity author) {
		try {
			get(uri);
			return false;
		}
		catch(NoSuchMessageException e) {
			return mIdentityManager.anyOwnIdentityWantsMessagesFrom(author);
		}
	}
	
	public abstract void terminate();

	public IdentityManager getIdentityManager() {
		return mIdentityManager;
	}
}
