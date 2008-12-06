/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;
import java.util.Iterator;

import plugins.WoT.introduction.IntroductionPuzzle;

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

	/**
	 * Get a message by its URI. The transient fields of the returned message will be initialized already.
	 */
	public Message get(FreenetURI uri) {
		return get(Message.generateID(uri));
	}
	
	public synchronized Message get(String id) {
		Query query = db.query();
		query.constrain(Message.class);
		query.descend("mID").constrain(id);
		ObjectSet<Message> result = query.execute();

		assert(result.size() <= 1);
		
		if(result.size() == 0)
			return null;
		else {
			Message m = result.next();
			m.initializeTransient(db, this);
			return m;
		}
	}

	/**
	 * Get a board by its name. The transient fields of the returned board will be initialized already.
	 */
	public synchronized Board getBoardByName(String name) {
		Query query = db.query();
		query.constrain(Board.class);
		query.descend("mName").constrain(name);
		ObjectSet<Board> result = query.execute();

		assert(result.size() <= 1);

		if(result.size() == 0)
			return null;
		else {
			Board b = result.next();
			b.initializeTransient(db, this);
			return b;
		}
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
		q.descend("mIndex").orderDescending();
		ObjectSet<OwnMessage> result = q.execute();
		
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
		return (get(uri) != null) || mIdentityManager.anyOwnIdentityWantsMessagesFrom(author);
	}
	
	public abstract void terminate();

	public IdentityManager getIdentityManager() {
		return mIdentityManager;
	}
}
