/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;

import plugins.Freetalk.exceptions.InvalidParameterException;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.FreenetURI;

/**
 * @author xor
 *
 */
public class FTBoard {

	/* Constants */
	
	private static transient final HashSet<String> ISOLanguages = new HashSet<String>(Arrays.asList(Locale.getISOLanguages()));
	
	
	/* Attributes, stored in the database */

	private final String mName;
	
	
	/* References to objects of the plugin, not stored in the database. */
	
	private transient ObjectContainer db;
	private transient FTMessageManager mMessageManager;
	private transient FTBoard self;
	

	/**
	 * Get a list of fields which the database should create an index on.
	 */
	public static String[] getIndexedFields() {
		return new String[] {"mName"};
	}
	
	public FTBoard(FTMessageManager newMessageManager, String newName) throws InvalidParameterException {
		if(newName==null || newName.length() == 0)
			throw new IllegalArgumentException("Empty board name.");
		if(!isNameValid(newName))
			throw new InvalidParameterException("Board names have to be either in English or have an ISO language code at the beginning followed by a dot.");

		assert(newMessageManager != null);

		mMessageManager = newMessageManager;
		self = this;
		
		// FIXME: Validate name and description.
		mName = newName;
	}
	
	/**
	 * Has to be used after loading a FTBoard object from the database to initialize the transient fields.
	 */
	public void initializeTransient(ObjectContainer myDB, FTMessageManager myMessageManager) {
		assert(myDB != null);
		assert(myMessageManager != null);
		self = this;
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
		return name.matches("[a-zA-Z0-9.]") || ISOLanguages.contains(firstPart);
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
	
	/**
	 * @return An NNTP-conform representation of the name of the board.
	 */
	public String getNameNNTP() {
		/* FIXME: Implement. */
		return mName;
	}

	/**
	 * Called by the <code>FTMessageManager</code> to add a just received message to the board.
	 * The job for this function is to find the right place in the thread-tree for the new message and to move around older messages
	 * if a parent message of them is received.
	 */
	public synchronized void addMessage(FTMessage newMessage) {
		synchronized(mMessageManager) {
			newMessage.initializeTransient(db);
			newMessage.store();

			if(!newMessage.isThread())
			{
				FreenetURI parentURI = newMessage.getParentURI();
				FTMessage parentMessage = mMessageManager.get(parentURI); /* TODO: This allows crossposting. Figure out whether we need to handle it specially */
				FTMessage parentThread = findParentThread(newMessage);
	
				if(parentThread != null)
					newMessage.setThread(parentThread);
	
				if(parentMessage != null) {
					newMessage.setParent(parentMessage);
				} else { /* The message is an orphan */
					if(parentThread != null) {
						newMessage.setParent(parentThread);	/* We found its parent thread so just stick it in there for now */
					}
					else {
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
	private synchronized void linkOrphansToNewParent(FTMessage newMessage) {
		if(newMessage.isThread()) {
			Iterator<FTMessage> absoluteOrphans = absoluteOrphanIterator(newMessage.getURI());
			while(absoluteOrphans.hasNext())	/* Search in the absolute orphans for messages which belong to this thread  */
				absoluteOrphans.next().setParent(newMessage);
		}
		else {
			FTMessage parentThread = newMessage.getThread();
			if(parentThread != null) {	/* Search in its parent thread for its children */
				Iterator<FTMessage> iter = parentThread.childrenIterator(this);
				while(iter.hasNext()) {
					FTMessage parentThreadChild = iter.next();
					
					if(parentThreadChild.getParentURI().equals(newMessage.getURI())) /* We found its parent, yeah! */
						parentThreadChild.setParent(newMessage); /* It's a child of the newMessage, not of the parentThread */
				}
			}
			else { /* The new message is an absolute orphan, find its children amongst the other absolute orphans */
				Iterator<FTMessage> absoluteOrphans = absoluteOrphanIterator(newMessage.getURI());
				while(absoluteOrphans.hasNext()){	/* Search in the orphans for messages which belong to this message  */
					FTMessage orphan = absoluteOrphans.next();
					/*
					 * The following if() could be joined into the db4o query in absoluteOrphanIterator(). I did not do it because we could
					 * cache the list of absolute orphans locally. 
					 */
					if(orphan.getParentURI().equals(newMessage.getURI()))
						orphan.setParent(newMessage);
				}
			}
		}
	}
	
	/**
	 * Finds the parent thread of a message in the database. The transient fields of the returned message will be initialized already.
	 */
	protected synchronized FTMessage findParentThread(FTMessage m) {
		Query q = db.query();
		q.constrain(FTMessage.class);
		/* FIXME: I assume that db4o is configured to keep an URI index per board. We still have to ensure in FMS.java that it is configured to do so.
		 * If my second assumption - that the descend() statements are evaluated in the specified order - is true, then it might be faste because the
		 * URI index is smaller per board than the global URI index. */
		q.descend("mBoards").constrain(mName); 
		q.descend("mURI").constrain(m.getParentThreadURI());
		ObjectSet<FTMessage> parents = q.execute();
		
		assert(parents.size() <= 1);
		
		if(parents.size() == 0)
			return null;
		else {
			FTMessage thread = parents.next();
			thread.initializeTransient(db);
			return thread;
		}
	}
	

	/**
	 * Get all threads in the board. The view is specified to the FTOwnIdentity displaying it, therefore you have to pass one as parameter.
	 * The transient fields of the returned messages will be initialized already.
	 * @param identity The identity viewing the board.
	 * @return An iterator of the message which the identity will see (based on its trust levels).
	 */
	public synchronized Iterator<FTMessage> threadIterator(final FTOwnIdentity identity) {
		return new Iterator<FTMessage>() {
			private final FTOwnIdentity mIdentity = identity;
			private final Iterator<FTMessage> iter;
			private FTMessage next;
			 
			{
				/* FIXME: If db4o supports precompiled queries, this one should be stored precompiled.
				 * Reason: We sort the threads by date.
				 * Maybe we can just keep the Query-object and call q.execute() as many times as we like to?
				 * Or somehow tell db4o to keep a per-board thread index which is sorted by Date? - This would be the best solution */
				Query q = db.query();
				q.constrain(FTMessage.class);
				q.descend("mBoards").constrain(mName); /* FIXME: mBoards is an array. constrain() does NOT check whether it contains the element mName. We need to change this code. */
				q.descend("mThread").constrain(null).identity();
				q.descend("mDate").orderDescending();

				iter = q.execute().iterator();
				next = iter.hasNext() ? iter.next() : null;
			}

			public boolean hasNext() {
				for(; next != null; next = iter.hasNext() ? iter.next() : null)
				{
					if(mIdentity.wantsMessagesFrom(identity))
						return true;
				}
				return false;
			}

			public FTMessage next() {
				FTMessage result = hasNext() ? next : null;
				next = iter.hasNext() ? iter.next() : null;
				result.initializeTransient(db);
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
	public synchronized Iterator<FTMessage> absoluteOrphanIterator(final FreenetURI thread) {
		return new Iterator<FTMessage>() {
			private final Iterator<FTMessage> iter;

			{
				/* FIXME: This query should be accelerated. The amount of absolute orphans is very small usually, so we should configure db4o
				 * to keep a separate list of those. */
				Query q = db.query();
				q.constrain(FTMessage.class);
				q.descend("mBoards").constrain(mName); /* FIXME: mBoards is an array. Does constrain() check whether it contains the element mName? */
				q.descend("mThreadURI").constrain(thread);
				q.descend("mThread").constrain(null);
				ObjectSet<FTMessage> result = q.execute();
				iter = result.iterator();
			}

			public boolean hasNext() {
				return iter.hasNext();
			}

			public FTMessage next() {
				FTMessage next = iter.next();
				next.initializeTransient(db);
				return next;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	public synchronized void store() {
		/* FIXME: check for duplicates */
		db.store(this);
		db.commit();
	}
}
