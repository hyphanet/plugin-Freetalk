/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.support.Logger;
import freenet.support.StringValidityChecker;

/**
 * Represents a forum / newsgroups / discussion board in Freetalk. Boards are created by the <code>MessageManager</code> on demand, you do
 * not need to manually create them. The <code>MessageManager</code> takes care of anything related to boards, to someone who just wants to
 * write a user interface this class can be considered as read-only.
 * 
 * @author xor
 */
public final class Board implements Comparable<Board> {

	/* Constants */
	
	private static transient final HashSet<String> ISOLanguages = new HashSet<String>(Arrays.asList(getAllowedLanguageCodes()));

	// Characters not allowed in board names:
	//  ! , ? * [ \ ] (space)  not allowed by NNTP
	//  / : < > | "            not allowed in filenames on certain platforms
	//                         (a problem for some newsreaders)
	private static final String DISALLOWED_NAME_CHARACTERS = "!,?*[\\] /:<>|\"";


	/* Attributes, stored in the database */

	private final String mName;
	
	private final Date mFirstSeenDate;
	
	
	/* References to objects of the plugin, not stored in the database. */
	
	private transient ObjectContainer db;
	private transient MessageManager mMessageManager;
	
	private static final Calendar mCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	/**
	 * Get a list of fields which the database should create an index on.
	 */
	public static String[] getIndexedFields() {
		return new String[] {"mName"};
	}
	
	public static String[] getBoardMessageLinkIndexedFields() { /* TODO: ugly! find a better way */
		return new String[] {"mBoard", "mMessage"};
	}
	
	public static String[] getAllowedLanguageCodes() {
		return Locale.getISOLanguages();
	}
	
	/**
	 * Create a board. You have to store() it yourself after creation.
	 * @param newName The name of the board. For restrictions, see <code>isNameValid()</code>
	 * @throws InvalidParameterException If none or an invalid name is given.
	 */
	public Board(String newName) throws InvalidParameterException {
		if(newName==null || newName.length() == 0)
			throw new IllegalArgumentException("Empty board name.");
		if(!isNameValid(newName))
			throw new InvalidParameterException("Board names have to be either in English or have an ISO language code at the beginning followed by a dot.");
		
		// FIXME: Validate name and description.
		mName = newName;
		mFirstSeenDate = mCalendar.getTime();
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
	
	/**
	 * Store this object in the database. You have to initializeTransient() before.
	 */
	public synchronized void store() {
		/* FIXME: check for duplicates */
		db.store(this);
		db.commit();
	}

	/**
	 * Check if a board name is valid.
	 *
	 * Board names are required to begin with a known language code,
	 * and may not contain any blacklisted characters.  Formatting
	 * characters must be properly paired within each part of the name
	 * (special formatting characters may be needed, e.g. for some
	 * Arabic or Hebrew group names to be displayed properly.)
	 */
	public static boolean isNameValid(String name) {
		// check for illegal characters

		if (!StringValidityChecker.containsNoLinebreaks(name)
			|| !StringValidityChecker.containsNoInvalidCharacters(name)
			|| !StringValidityChecker.containsNoControlCharacters(name)
			|| !StringValidityChecker.containsNoIDNBlacklistCharacters(name))
			return false;

		for (Character c : name.toCharArray()) {
			if (DISALLOWED_NAME_CHARACTERS.indexOf(c) != -1)
				return false;
		}

		// check for invalid formatting characters (each dot-separated
		// part of the input string must be valid on its own)

		String[] parts = name.split("\\.");
		if (parts.length < 2)
			return false;

		for (int i = 0; i < parts.length; i++) {
			if (parts[i].length() == 0 || !StringValidityChecker.containsNoInvalidFormatting(parts[i]))
				return false;
		}

		// first part of name must be a recognized language code

		return (ISOLanguages.contains(parts[0]));
	}
	

	/**
	 * @return The name.
	 */
	public String getName() {
		return mName;
	}
	
	public Date getFirstSeenDate() {
		return mFirstSeenDate;
	}
	
	public synchronized String getDescription(FTOwnIdentity viewer) {
		/* FIXME: Implement */
		return "";
	}
	
	/**
	 * @return An NNTP-conform representation of the name of the board.
	 */
	/*
	public String getNameNNTP() {
		// FIXME: Implement.
		return mName;
	}
	*/

	/**
	 * Compare boards by comparing their names; provided so we can
	 * sort an array of boards.
	 */
	public int compareTo(Board b) {
		return getName().compareTo(b.getName());
	}

	/**
	 * Called by the <code>FTMessageManager</code> to add a just received message to the board.
	 * The job for this function is to find the right place in the thread-tree for the new message and to move around older messages
	 * if a parent message of them is received.
	 */
	public synchronized void addMessage(Message newMessage) {
		synchronized(mMessageManager) {
			if(newMessage instanceof OwnMessage) {
				/* We do not add the message to the boards it is posted to because the user should only see the message if it has been downloaded
				 * successfully. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
				throw new IllegalArgumentException("Adding OwnMessages to a board is not allowed.");
			}
				
			newMessage.initializeTransient(db, mMessageManager);
			newMessage.store();
			
			synchronized(BoardMessageLink.class) {
				new BoardMessageLink(this, newMessage, getFreeMessageIndex()).store(db);
			}

			if(!newMessage.isThread())
			{
				Message parentThread = null;
	
				try {
					parentThread = findParentThread(newMessage).getMessage();
					newMessage.setThread(parentThread);
				}
				catch(NoSuchMessageException e) {}
	
				try {
					newMessage.setParent(mMessageManager.get(newMessage.getParentID())); /* TODO: This allows crossposting. Figure out whether we need to handle it specially */
				}
				catch(NoSuchMessageException e) {/* The message is an orphan */
					if(parentThread == null) {
						 /* The message is an absolute orphan */
	
						/* 
						 * FIXME: The MessageManager should try to download the parent message if it's poster has enough trust.
						 * If it is programmed to do that, it will check its Hashtable whether the parent message already exists.
						 * We also do that here, therefore, when implementing parent message downloading, please do the Hashtable checking only once. 
						 */
					}
				} 
			}
	
			linkOrphansToNewParent(newMessage);
		}
	}

	/**
	 * Assumes that the transient fields of the newMessage are initialized already.
	 */
	private synchronized void linkOrphansToNewParent(Message newMessage) {
		if(newMessage.isThread()) {
			Iterator<Message> absoluteOrphans = absoluteOrphanIterator(newMessage.getID());
			while(absoluteOrphans.hasNext()) {	/* Search in the absolute orphans for messages which belong to this thread  */
				Message orphan = absoluteOrphans.next();
				orphan.setThread(newMessage);
				try {
					if(orphan.getParentURI().equals(newMessage.getURI()))
						orphan.setParent(newMessage);
				} catch (NoSuchMessageException e) {
					Logger.error(this, "Message is reply to thread but parentURI == null: " + orphan.getURI());
				}
			}
		}
		else {
			try {
				Message parentThread = newMessage.getThread();
				/* Search in its parent thread for its children */
				Iterator<Message> iter = parentThread.childrenIterator(this);
				while(iter.hasNext()) {
					Message parentThreadChild = iter.next();
					
					try {
						if(parentThreadChild.getParentURI().equals(newMessage.getURI())) /* We found its parent, yeah! */
							parentThreadChild.setParent(newMessage); /* It's a child of the newMessage, not of the parentThread */
					}
					catch(NoSuchMessageException e) {
						Logger.error(this, "Message is reply to thread but parentURI == null: " + parentThreadChild.getURI());
					}
				}
			}
			catch(NoSuchMessageException e)
			{ /* The new message is an absolute orphan, find its children amongst the other absolute orphans */
				Iterator<Message> absoluteOrphans = absoluteOrphanIterator(newMessage.getID());
				while(absoluteOrphans.hasNext()){	/* Search in the orphans for messages which belong to this message  */
					Message orphan = absoluteOrphans.next();
					/*
					 * The following if() could be joined into the db4o query in absoluteOrphanIterator(). I did not do it because we could
					 * cache the list of absolute orphans locally. 
					 */
					try {
						if(orphan.getParentURI().equals(newMessage.getURI()))
							orphan.setParent(newMessage);
					}
					catch(NoSuchMessageException error) {
						Logger.error(this, "Should not happen", error);
					}
				}
			}
		}
	}
	
	/**
	 * Finds the parent thread of a message in the database. The transient fields of the returned message will be initialized already.
	 * @throws NoSuchMessageException 
	 */
	@SuppressWarnings("unchecked")
	private synchronized MessageReference findParentThread(Message m) throws NoSuchMessageException {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		/* FIXME: I assume that db4o is configured to keep an URI index per board. We still have to ensure in FMS.java that it is configured to do so.
		 * If my second assumption - that the descend() statements are evaluated in the specified order - is true, then it might be faste because the
		 * URI index is smaller per board than the global URI index. */
		q.descend("mBoard").constrain(this); 
		q.descend("mMessage").descend("mThreadID").constrain(m.getParentThreadID());
		ObjectSet<MessageReference> parents = q.execute();
		
		assert(parents.size() <= 1);
		
		if(parents.size() == 0)
			throw new NoSuchMessageException(m.getParentThreadID());
		else {
			MessageReference parentThread = parents.next();
			assert(parentThread.getMessage().getID().equals(m.getParentThreadID())); /* The query works */
			return parentThread;
		}
	}
	

	/**
	 * Get all threads in the board. The view is specified to the FTOwnIdentity displaying it, therefore you have to pass one as parameter.
	 * The transient fields of the returned messages will be initialized already.
	 * @param identity The identity viewing the board.
	 * @return An iterator of the message which the identity will see (based on its trust levels).
	 */
	@SuppressWarnings("unchecked")
	public synchronized Iterator<MessageReference> threadIterator(final FTOwnIdentity identity) {
		return new Iterator<MessageReference>() {
			private final FTOwnIdentity mIdentity = identity;
			private final Iterator<BoardMessageLink> iter;
			private MessageReference next;
			 
			{
				/* FIXME: If db4o supports precompiled queries, this one should be stored precompiled.
				 * Reason: We sort the threads by date.
				 * Maybe we can just keep the Query-object and call q.execute() as many times as we like to?
				 * Or somehow tell db4o to keep a per-board thread index which is sorted by Date? - This would be the best solution */
				Query q = db.query();
				q.constrain(BoardMessageLink.class);
				q.descend("mBoard").constrain(Board.this);
				q.descend("mMessage").descend("mThread").constrain(null).identity();
				q.descend("mMessage").descend("mDate").orderDescending();
				iter = q.execute().iterator();
				next = iter.hasNext() ? iter.next() : null;
			}

			public boolean hasNext() {
				for(; next != null; next = iter.hasNext() ? iter.next() : null)
				{
					if(mIdentity.wantsMessagesFrom(next.getMessage().getAuthor()))
						return true;
				}
				return false;
			}

			public MessageReference next() {
				MessageReference result = hasNext() ? next : null;
				next = iter.hasNext() ? iter.next() : null;
				return result;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
			
		};
	}
	
	/**
	 * Get an iterator over messages for which the parent thread with the given ID was not known. 
	 * The transient fields of the returned messages will be initialized already.
	 */
	@SuppressWarnings("unchecked")
	private synchronized Iterator<Message> absoluteOrphanIterator(final String threadID) {
		return new Iterator<Message>() {
			private final Iterator<BoardMessageLink> iter;

			{
				/* FIXME: This query should be accelerated. The amount of absolute orphans is very small usually, so we should configure db4o
				 * to keep a separate list of those. */
				Query q = db.query();
				q.constrain(BoardMessageLink.class);
				q.descend("mBoard").constrain(Board.this); /* FIXME: mBoards is an array. Does constrain() check whether it contains the element mName? */
				q.descend("mMessage").descend("mThreadID").constrain(threadID);
				q.descend("mMessage").descend("mThread").constrain(null).identity();
				iter = q.execute().iterator();
			}

			public boolean hasNext() {
				return iter.hasNext();
			}

			public Message next() {
				return iter.next().getMessage();
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	/* FIXME: This function returns all messages, not only the ones which the viewer wants to see. Convert the function to an iterator
	 * which picks threads chosen by the viewer, see threadIterator() for how to do this */
	@SuppressWarnings("unchecked")
	public synchronized List<MessageReference> getAllMessages() {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		q.descend("mBoard").constrain(this);
		q.descend("mMessageIndex").orderAscending(); /* Needed for NNTP */
		return q.execute();
	}
	
	@SuppressWarnings("unchecked")
	public synchronized int getMessageIndex(Message message) throws NoSuchMessageException {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		q.descend("mMessage").constrain(message);
		ObjectSet<BoardMessageLink> result = q.execute();
		
		if(result.size() == 0)
			throw new NoSuchMessageException(message.getID());
		
		return result.next().getIndex();
	}

	/* FIXME: This function counts all messages, not only the ones which the viewer wants to see. */
	public synchronized int getLastMessageIndex() {
		return getFreeMessageIndex() - 1;
	}
	
	@SuppressWarnings("unchecked")
	public synchronized Message getMessageByIndex(int index) throws NoSuchMessageException {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		q.descend("mBoard").constrain(this);
		q.descend("mMessageIndex").constrain(index);
		ObjectSet<MessageReference> result = q.execute();
		if(result.size() == 0)
			throw new NoSuchMessageException();
		
		return result.next().getMessage();
	}
	
	/**
	 * Get the next free NNTP index for a message. Please synchronize on BoardMessageLink.class when creating a message, this method
	 * does not provide synchronization.
	 */
	@SuppressWarnings("unchecked")
	public int getFreeMessageIndex() {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		q.descend("mBoard").constrain(this);
		q.descend("mMessageIndex").orderDescending(); /* FIXME: Use a db4o native query to find the maximum instead of sorting. O(n) vs. O(n log(n))! */
		ObjectSet<MessageReference> result = q.execute();
		return result.size() == 0 ? 1 : result.next().getIndex()+1;
	}
	
	/**
	 * Get the number of messages in this board.
	 */
	/* FIXME: This function counts all messages, not only the ones which the viewer wants to see. */
	public synchronized int messageCount() {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		q.descend("mBoard").constrain(this);
		return q.execute().size();
	}
	
	/**
	 * Get the number of replies to the given thread.
	 */
	/* FIXME: This function counts all replies, not only the ones which the viewer wants to see. */
	public synchronized int threadReplyCount(FTOwnIdentity viewer, Message thread) {
		return getAllThreadReplies(thread).size();
	}
	
	/**
	 * Get all replies to the given thread, sorted ascending by date
	 */
	/* FIXME: This function returns all replies, not only the ones which the viewer wants to see. Convert the function to an iterator
	 * which picks threads chosen by the viewer, see threadIterator() for how to do this */
	@SuppressWarnings("unchecked")
	public synchronized List<MessageReference> getAllThreadReplies(Message thread) {
		Query q = db.query();
		/* FIXME: This query is inefficient. It should rather first query for objects of Message.class which have mThreadID == thread.getID()
		 * and then check whether a BoardMessageLink to this board exists. */
		q.constrain(BoardMessageLink.class);
		q.descend("mBoard").constrain(this);
		try {
			Query sub = q.descend("mMessage");
			sub.constrain(thread).identity().not();
			sub.descend("mThreadID").constrain(thread.isThread() ? thread.getID() : thread.getParentThreadID());
		} catch (NoSuchMessageException e) {
			throw new RuntimeException( "Message is no thread but parentThreadURI == null : " + thread.getURI());
		}
		
		return q.execute();
	}
	
	public interface MessageReference {
		/** Get the message to which this reference points */
		public Message getMessage();
		
		/** Get an unique index number of this message in the board where which the query for the message was executed.
		 * This index number is needed for NNTP. */
		public int getIndex();
	}
	
	/**
	 * Helper class to associate messages with boards in the database
	 */
	public final class BoardMessageLink implements MessageReference { /* TODO: This is only public for configuring db4o. Find a better way */
		private final Board mBoard;
		private final Message mMessage;
		private final int mMessageIndex; /* TODO: The NNTP server should maintain the index values itself maybe. */
		
		public BoardMessageLink(Board myBoard, Message myMessage, int myIndex) {
			assert(myBoard != null && myMessage != null);
			mBoard = myBoard;
			mMessage = myMessage;
			mMessageIndex = myIndex;
		}
		
		public void store(ObjectContainer db) {
			db.store(this);
			db.commit();
		}

		public int getIndex() {
			return mMessageIndex;
		}

		public Message getMessage() {
			/* We do not have to initialize mBoard and can assume that it is initialized because a BoardMessageLink will only be loaded
			 * by the board it belongs to. */
			mMessage.initializeTransient(mBoard.db, mBoard.mMessageManager);
			db.activate(mMessage, 2); /* FIXME: Figure out a reasonable depth */
			return mMessage;
		}
	}
	
}
