/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;
import freenet.support.Logger;
import freenet.support.StringValidityChecker;

/**
 * @author xor
 *
 */
public class Board implements Comparable<Board> {

	/* Constants */
	
	private static transient final HashSet<String> ISOLanguages = new HashSet<String>(Arrays.asList(Locale.getISOLanguages()));
	
	
	/* Attributes, stored in the database */

	private final String mName;
	
	
	/* References to objects of the plugin, not stored in the database. */
	
	private transient ObjectContainer db;
	private transient MessageManager mMessageManager;
	

	/**
	 * Get a list of fields which the database should create an index on.
	 */
	public static String[] getIndexedFields() {
		return new String[] {"mName"};
	}
	
	public static String[] getBoardMessageLinkIndexedFields() { /* TODO: ugly! find a better way */
		return new String[] {"mBoard", "mMessage"};
	}
	
	public Board(MessageManager newMessageManager, String newName) throws InvalidParameterException {
		if(newName==null || newName.length() == 0)
			throw new IllegalArgumentException("Empty board name.");
		if(!isNameValid(newName))
			throw new InvalidParameterException("Board names have to be either in English or have an ISO language code at the beginning followed by a dot.");

		assert(newMessageManager != null);

		mMessageManager = newMessageManager;
		
		// FIXME: Validate name and description.
		mName = newName;
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
	 * I suggest that we allow letters of any language in the name of a board with one restriction:
	 * If the name contains any letters different than A to Z and '.' then the part of the name before the first dot
	 * has to be only letters of A to Z specifying an ISO language code. This allows users which cannot type the
	 * letters of that language to filter based on the first part because they then can type its name.
	 * Further, it is polite to specify what language a board is in if it is not English.
	 */
	public static boolean isNameValid(String name) {
		int firstDot = name.indexOf('.');
		String firstPart = firstDot!=-1 ? name.substring(0, firstDot) : name;

		/* FIXME: This is just the basic check, we should do more checks:
		 * The rest of the name should match a whitelist of allowed punctuation (excluding for example &<>%#), or letters (i.e. not necessarily
		 * English letters) according to Character.isLetter() and numerals. */
		return name.matches("[a-zA-Z0-9.]+") || 
				(ISOLanguages.contains(firstPart)
					&& StringValidityChecker.containsNoIDNBlacklistCharacters(name));
	}
	
	/* 
	 * FIXME:
	 * We should post a warning on the interface if a user wants to post to a board with a non-NNTP-valid name and show him what the NNTP client
	 * will display the board name as, as soon as we have a getNameNNTP() function which converts the name to something displayable by NNTP
	 * readers.  
	 */
	/**
	 * Check whether the boardname is valid in the context of NNTP.
	 */
	public static boolean isNameValidNNTP(String name) {
		/* 
		 * FIXME:
		 * - Check the specification of NNTP and see if it allows anything else than the following regular expression.
		 */
		
		return name.matches("[a-zA-Z0-9.]");
	}

	/**
	 * @return The name.
	 */
	public String getName() {
		return mName;
	}
	
	public String getDescription(FTOwnIdentity viewer) {
		/* FIXME: Implement */
		return "";
	}
	
	/**
	 * @return An NNTP-conform representation of the name of the board.
	 */
	public String getNameNNTP() {
		/* FIXME: Implement. */
		return mName;
	}

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
					newMessage.setParent(mMessageManager.get(newMessage.getParentURI())); /* TODO: This allows crossposting. Figure out whether we need to handle it specially */
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
			Iterator<Message> absoluteOrphans = absoluteOrphanIterator(newMessage.getURI());
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
				Iterator<Message> absoluteOrphans = absoluteOrphanIterator(newMessage.getURI());
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
	protected synchronized MessageReference findParentThread(Message m) throws NoSuchMessageException {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		/* FIXME: I assume that db4o is configured to keep an URI index per board. We still have to ensure in FMS.java that it is configured to do so.
		 * If my second assumption - that the descend() statements are evaluated in the specified order - is true, then it might be faste because the
		 * URI index is smaller per board than the global URI index. */
		q.descend("mBoard").constrain(this); 
		q.descend("mMessage").descend("mURI").constrain(m.getParentThreadURI());
		ObjectSet<MessageReference> parents = q.execute();
		
		assert(parents.size() <= 1);
		
		if(parents.size() == 0)
			throw new NoSuchMessageException();
		else
			return parents.next();
	}
	

	/**
	 * Get all threads in the board. The view is specified to the FTOwnIdentity displaying it, therefore you have to pass one as parameter.
	 * The transient fields of the returned messages will be initialized already.
	 * @param identity The identity viewing the board.
	 * @return An iterator of the message which the identity will see (based on its trust levels).
	 */
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
	 * Get an iterator over messages for which the parent thread with the given URI was not known. 
	 * The transient fields of the returned messages will be initialized already.
	 */
	private synchronized Iterator<Message> absoluteOrphanIterator(final FreenetURI threadURI) {
		return new Iterator<Message>() {
			private final Iterator<BoardMessageLink> iter;

			{
				/* FIXME: This query should be accelerated. The amount of absolute orphans is very small usually, so we should configure db4o
				 * to keep a separate list of those. */
				Query q = db.query();
				q.constrain(BoardMessageLink.class);
				q.descend("mBoard").constrain(Board.this); /* FIXME: mBoards is an array. Does constrain() check whether it contains the element mName? */
				q.descend("mMessage").descend("mThreadURI").constrain(threadURI);
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
	
	public synchronized List<MessageReference> getAllMessages() {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		q.descend("mBoard").constrain(this);
		q.descend("mMessageIndex").orderAscending(); /* Needed for NNTP */
		return q.execute();
	}
	
	public synchronized int getMessageIndex(Message message) throws NoSuchMessageException {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		q.descend("mMessage").constrain(message);
		ObjectSet<BoardMessageLink> result = q.execute();
		
		if(result.size() == 0)
			throw new NoSuchMessageException();
		
		return result.next().getIndex();
	}

	public synchronized int getLastMessageIndex() {
		return getFreeMessageIndex() - 1;
	}
	
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
	public synchronized int messageCount() {
		Query q = db.query();
		q.constrain(BoardMessageLink.class);
		q.descend("mBoard").constrain(this);
		return q.execute().size();
	}
	
	/**
	 * Get the number of replies to the given thread.
	 */
	public synchronized int threadReplyCount(Message thread) {
		Query q = db.query();
		/* FIXME: Check whether this query is fast. I think it should rather first query for objects of Message.class which have mThread == thread
		 * and then check whether a BoardMessageLink to this board exists. */
		q.constrain(BoardMessageLink.class);
		q.descend("mBoard").constrain(this);
		try {
			q.descend("mMessage").descend("mThreadURI").constrain(thread.isThread() ? thread.getURI() : thread.getParentThreadURI());
		} catch (NoSuchMessageException e) {
			Logger.error(this, "Message is no thread but parentThreadURI == null : " + thread.getURI());
			return -1; /* To make the users report this bug */
		}
		return q.execute().size();
	}
	
	public synchronized void store() {
		/* FIXME: check for duplicates */
		db.store(this);
		db.commit();
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
