/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.CurrentTimeUTC;
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

    public static final int MAX_BOARDNAME_TEXT_LENGTH = 256;


    /* Attributes, stored in the database */
    
    private final String mID;

    private final String mName;

    private final Date mFirstSeenDate;

    private Date mLatestMessageDate;


    /* References to objects of the plugin, not stored in the database. */

    private transient ExtObjectContainer db;
    private transient MessageManager mMessageManager;


    /**
     * Get a list of fields which the database should create an index on.
     */
    public static String[] getIndexedFields() {
        return new String[] { "mID", "mName" };
    }
    
    public static String[] getMessageReferenceIndexedFields() { /* TODO: ugly! find a better way */
    	return new String[] { "mBoard", "mMessage", "mMessageIndex" };
    }

    public static String[] getBoardReplyLinkIndexedFields() { /* TODO: ugly! find a better way */
        return new String[] { "mThreadID" };
    }
    
    public static String[] getBoardThreadLinkIndexedFields() { /* TODO: ugly! find a better way */
    	return new String[] { "mThreadID" };
    }

    public static String[] getAllowedLanguageCodes() {
        return Locale.getISOLanguages();
    }

    /**
     * Create a board. You have to store() it yourself after creation.
     * Only for being used directly by unit tests. The client interface for creating boards is {@link MessageManager.getOrCreateBoard}.
     * 
     * @param newName The name of the board. For restrictions, see <code>isNameValid()</code>
     * @throws InvalidParameterException If none or an invalid name is given.
     */
    public Board(String newName) throws InvalidParameterException {
        if(newName==null || newName.length() == 0)
            throw new IllegalArgumentException("Empty board name.");
        if(!isNameValid(newName))
            throw new InvalidParameterException("Board names have to be either in English or have an ISO language code at the beginning followed by a dot.");

        mID = UUID.randomUUID().toString();
        mName = newName.toLowerCase();
        mFirstSeenDate = CurrentTimeUTC.get();
        mLatestMessageDate = null;
    }

    /**
     * Has to be used after loading a FTBoard object from the database to initialize the transient fields.
     */
    protected void initializeTransient(ExtObjectContainer myDB, MessageManager myMessageManager) {
        assert(myDB != null);
        assert(myMessageManager != null);
        db = myDB;
        mMessageManager = myMessageManager;
    }

    /**
     * Store this object in the database. You have to initializeTransient() before.
     * 
     * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
     */
    protected void storeWithoutCommit() {
    	try  {
    		if(db.ext().isStored(this) && !db.ext().isActive(this))
    			throw new RuntimeException("Trying to store a non-active Board object");

    		db.store(this);
    	}
    	catch(RuntimeException e) {
    		db.rollback(); Logger.error(this, "ROLLED BACK!", e);
    		throw e;
    	}
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
        // paranoia checks

        if (name == null || name.length() == 0) {
            return false;
        }

        // check maximum length

        if (name.length() > MAX_BOARDNAME_TEXT_LENGTH) {
            return false;
        }

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
     * Get the ID of this board.
     * 
     * It is a local (to this Freetalk database), random, unique UUID of this board. If a board is created, deleted and then re-created with the same name,
     * the ID will still be different.
     * Needed for synchronization with client apps: They use the per-board unique message index numbers to check whether they have all messages stored in their
     * caching database. If the user deleted a board in Freetalk and then the board was re-created due to new messages, the message indexes will start at 0 again.
     * If we used only the board name as identification, the client apps would not download the new messages because they already have stored messages with index
     * 0, 1, 2 and so on. 
     */
    public String getID() {
    	return mID;
    }

    /**
     * @return The name of this board. Only one board with a given name can exist at once. The name is case-insensitive.
     */
    public String getName() {
        return mName;
    }

    public Date getFirstSeenDate() {
        return mFirstSeenDate;
    }

    public synchronized Date getLatestMessageDate() {
        return mLatestMessageDate;
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
     * 
     * Does not store the message, you have to do this before!
     */
    protected synchronized void addMessage(Message newMessage) {        
    	if(newMessage instanceof OwnMessage) {
    		/* We do not add the message to the boards it is posted to because the user should only see the message if it has been downloaded
    		 * successfully. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
    		throw new IllegalArgumentException("Adding OwnMessages to a board is not allowed.");
    	}
    	
    	BoardThreadLink ghostRef = null;
    	
    	try {
    		// If there was a ghost thread reference for the new message, we associate the message with it - even if it is no thread:
    		// People are allowed to reply to non-threads as if they were threads, which results in a 'forked' thread.
    		ghostRef = getThreadReference(newMessage.getID());
    		ghostRef.setMessage(newMessage);
    		ghostRef.storeWithoutCommit(db);
    		
    		linkThreadRepliesToNewParent(newMessage.getID(), newMessage);
    	}
    	catch(NoSuchMessageException e) {
    		// If there was no ghost reference, we must store a BoardThreadLink if the new message is a thread 
			if(newMessage.isThread()) {
	    		BoardThreadLink threadRef = new BoardThreadLink(this, newMessage, getFreeMessageIndex());
	    		threadRef.storeWithoutCommit(db);
	    		
	    		// We do not call linkThreadRepliesToNewParent() here because if there was no ghost reference for the new message this means that no replies to
	    		// it were received yet.
			}
    	}
    
    	if(newMessage.isThread() == false) {
    		// The new message is no thread. We must:
    		
    		// 1. Find it's parent thread, create a ghost reference for it if it does not exist.
    		BoardThreadLink parentThreadRef = findOrCreateParentThread(newMessage);
    		
    		// 2. Tell it about it's parent thread if it exists.
    		Message parentThread = parentThreadRef.getMessage(); // Can return null if the parent thread was not downloaded yet, then it's a ghost reference.
    		if(parentThread != null)
    			newMessage.setThread(parentThread);
    		
    		// 3. Update the last reply date of the parent thread
    		parentThreadRef.updateLastReplyDate(newMessage.getDate());
    		parentThreadRef.storeWithoutCommit(db);
    		
    		// 4. Store a BoardReplyLink for the new message
    		BoardReplyLink messageRef = new BoardReplyLink(this, newMessage, getFreeMessageIndex());
    		messageRef.storeWithoutCommit(db);
    		
    		
    		// 5. Try to find the new message's parent message and tell it about it's parent message if it exists.
    		try {
    			// Try to find the parent message of the message
    			
    			// FIXME: This allows crossposting. Figure out whether we need to handle it specially:
    			// What happens if the message has specified a parent thread which belongs to this board BUT a parent message which is in a different board
    			// and does not belong to the parent thread
    			newMessage.setParent(mMessageManager.get(newMessage.getParentID()));
    		}
    		catch(NoSuchMessageException e) {
    			// The parent message of the message was not downloaded yet
    			// TODO: The MessageManager should try to download the parent message if it's poster has enough trust.
    		}

    		linkThreadRepliesToNewParent(parentThreadRef.getThreadID(), newMessage);
    	}
    	
    	// Finally, we must update the latest message date of this board.
    	if(mLatestMessageDate == null || newMessage.getDate().after(mLatestMessageDate))
    		mLatestMessageDate = newMessage.getDate();

    	storeWithoutCommit();
    }

    /**
     * For a new thread, calls setParent() for all messages which are a reply to it and setThread() for all messages which belong to the new thread.
     * For a new message, i.e. reply to a thread, calls setParent() for all messages which are a reply to it.
     *      
     * Assumes that the transient fields of the newMessage are initialized already.
     */
    private synchronized void linkThreadRepliesToNewParent(String parentThreadID, Message newMessage) {
    	
    	boolean newMessageIsThread = (newMessage.getID().equals(parentThreadID));
 
    	for(MessageReference ref : getAllThreadReplies(parentThreadID, false)) {
    		Message threadReply = ref.getMessage();
    		
    		try {
    			threadReply.getParent();
    		}
    		catch(NoSuchMessageException e) {
    			try {
    				if(threadReply.getParentID().equals(newMessage.getID()))
    					threadReply.setParent(newMessage);
    			}
    			catch(NoSuchMessageException ex) {
    				Logger.debug(this, "SHOULD NOT HAPPEN: getParentID() failed for a thread reply: " + threadReply, ex);
    			}
    		}
    		
    		if(newMessageIsThread) {
	    		try {
	    			threadReply.getThread();
	    		}
	    		catch(NoSuchMessageException e) {
	    			threadReply.setThread(newMessage);
	    		}
    		}
    	}
    }
    
    public synchronized BoardReplyLink getMessageReference(Message message) throws NoSuchMessageException {
        Query q = db.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mMessage").constrain(message).identity();
        ObjectSet<BoardReplyLink> results = q.execute();
        
        switch(results.size()) {
	        case 1:
				BoardReplyLink messageRef = results.next();
				assert(messageRef.getMessage().equals(message)); // The query works
				return messageRef;
	        case 0:
	        	throw new NoSuchMessageException(message.getID());
	        default:
	        	throw new DuplicateMessageException(message.getID());
        }
    }
    
    @SuppressWarnings("unchecked")
	public synchronized BoardThreadLink getThreadReference(String threadID) throws NoSuchMessageException {
        Query q = db.query();
        q.constrain(BoardThreadLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        ObjectSet<BoardThreadLink> results = q.execute();
        
        switch(results.size()) {
	        case 1:
				BoardThreadLink threadRef = results.next();
				assert(threadRef.getMessage().getID().equals(threadID)); // The query works
				return threadRef;
	        case 0:
	        	throw new NoSuchMessageException(threadID);
	        default:
	        	throw new DuplicateMessageException(threadID);
        }
    }

    /**
     * Returns the {@link BoardThreadLink} of the parent thread of the given message.
     * If the parent thread was not downloaded yet, a ghost BoardThreadLink is created and stored for it, without committing the transaction. 
     * You have to lock the board and the database before calling this function.
     * 
     * If the parent thread was downloaded but is no thread actually, a new thread is 'forked' off, making the parent thread message of the given message
     * both appear as a reply to the original thread where it belonged AND as a thread on it's own to which the given message belong.
     * 
     * The transient fields of the returned message will be initialized already.
     * @throws NoSuchMessageException
     */
    private synchronized BoardThreadLink findOrCreateParentThread(Message newMessage) {
    	String parentThreadID;
    	
    	try {
    		parentThreadID = newMessage.getThreadID();
    	}
    	catch(NoSuchMessageException e) {
    		Logger.error(this, "SHOULD NOT HAPPEN: findOrCreateParentThread called for a message where getThreadID failed: " + e);
    		throw new IllegalArgumentException(e);
    	}

    	try {
    		// The parent thread was downloaded and marked as a thread already, we return its BoardThreadLink
    		return getThreadReference(parentThreadID);
    	}
    	catch(NoSuchMessageException e) {
    		// There is no thread reference for the parent thread yet. Either it was not downloaded yet or it was downloaded but is no thread.
    		try {
    			Message parentThread = mMessageManager.get(parentThreadID);
    			
    			if(Arrays.binarySearch(parentThread.getBoards(), this) < 0) {
    				// The parent thread is not a message in this board.
    				// TODO: Decide whether we should maybe store a flag in the BoardThreadLink which marks it.
    				// IMHO it is part of the UI's job to read the board list of the actual Message object and display something if the thread is not
    				// really a message to this board.
    			}

    			// The parent thread was downloaded and is no thread actually, we create a BoardThreadLink for it and therefore 'fork' a new thread off
    			// that message. The parent thread message will still be displayed as a reply to it's original thread, but it will also appear as a new thread
    			// which is the parent of the message which was passed to this function.

    			BoardThreadLink parentThreadRef = new BoardThreadLink(this, parentThread, getFreeMessageIndex()); 
    			parentThreadRef.storeWithoutCommit(db);
    			return parentThreadRef;
    		}
    		catch(NoSuchMessageException ex) { 
    			// The message manager did not find the parentThreadID, so the parent thread was not downloaded yet, we create a ghost thread reference for it.
    			BoardThreadLink ghostThreadRef = new BoardThreadLink(this, parentThreadID, newMessage.getDate(), getFreeMessageIndex());
    			ghostThreadRef.storeWithoutCommit(db);
    			return ghostThreadRef;
    		}		
    	}
    }


    /**
     * Get all threads in the board. The view is specified to the FTOwnIdentity displaying it, therefore you have to pass one as parameter.
     * The transient fields of the returned messages will be initialized already.
     * @param identity The identity viewing the board.
     * @return An iterator of the message which the identity will see (based on its trust levels).
     */
    @SuppressWarnings("unchecked")
    public synchronized Iterable<BoardThreadLink> getThreads(final FTOwnIdentity identity) {
    	return new Iterable<BoardThreadLink>() {
		public Iterator<BoardThreadLink> iterator() {
        return new Iterator<BoardThreadLink>() {
            private final FTOwnIdentity mIdentity = identity;
            private final Iterator<BoardThreadLink> iter;
            private BoardThreadLink next;

            {
                Query q = db.query();
                q.constrain(BoardThreadLink.class);
                q.descend("mBoard").constrain(Board.this).identity();
                q.descend("mLastReplyDate").orderDescending();
                               
                iter = q.execute().iterator();
                next = iter.hasNext() ? iter.next() : null;
            }

            public boolean hasNext() {
                for(; next != null; next = iter.hasNext() ? iter.next() : null)
                {
                	if(next.getMessage() == null)
                		return true; // FIXME: Get the author from the message ID 
                	
                    if(mIdentity.wantsMessagesFrom(next.getMessage().getAuthor()))
                        return true;
                }
                return false;
            }

            public BoardThreadLink next() {
                BoardThreadLink result = hasNext() ? next : null;
                next = iter.hasNext() ? iter.next() : null;
                return result;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
		};
    	}
        };
    }


    /* FIXME: This function returns all messages, not only the ones which the viewer wants to see. Convert the function to an iterator
     * which picks threads chosen by the viewer, see threadIterator() for how to do this */
    @SuppressWarnings("unchecked")
    public synchronized List<MessageReference> getAllMessages(final boolean sortByMessageIndexAscending) {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending(); /* Needed for NNTP */
        }
        return q.execute();
    }

    @SuppressWarnings("unchecked")
    public synchronized int getMessageIndex(Message message) throws NoSuchMessageException {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mMessage").constrain(message).identity();
        ObjectSet<MessageReference> result = q.execute();

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
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessageIndex").constrain(index);
        ObjectSet<MessageReference> result = q.execute();
        
        switch(result.size()) {
	        case 1:
	        	return result.next().getMessage();
	        case 0:
	            throw new NoSuchMessageException();
	        default:
	        	throw new DuplicateMessageException("index " + Integer.toString(index));
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<MessageReference> getMessagesByMinimumIndex(
            int minimumIndex,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (minimumIndex > 0) {
            q.descend("mMessageIndex").constrain(minimumIndex).smaller().not();
        }
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
        return q.execute();
    }

    @SuppressWarnings("unchecked")
    public synchronized List<MessageReference> getMessagesByMinimumDate(
            long minimumDate,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (minimumDate > 0) {
            q.descend("mMessageDate").constrain(minimumDate).smaller().not();
        }
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
        return q.execute();
    }

    /**
     * Get the next free NNTP index for a message. Please synchronize on this Board when creating a message, this method
     * does not and cannot provide synchronization as creating a message is no atomic operation.
     */
    @SuppressWarnings("unchecked")
    private int getFreeMessageIndex() {
        Query q = db.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
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
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        return q.execute().size();
    }

    /**
     * Get the number of replies to the given thread.
     */
    /* FIXME: This function counts all replies, not only the ones which the viewer wants to see. */
    public synchronized int threadReplyCount(FTOwnIdentity viewer, String threadID) {
        return getAllThreadReplies(threadID, false).size();
    }

    /**
     * Get all replies to the given thread, sorted ascending by date if requested
     */
    // FIXME: This function returns all replies, not only the ones which the viewer wants to see. Convert the function to an iterator
    // which picks threads chosen by the viewer, see threadIterator() for how to do this.
    @SuppressWarnings("unchecked")
    public synchronized List<BoardReplyLink> getAllThreadReplies(String threadID, final boolean sortByDateAscending) {
        Query q = db.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        
        if (sortByDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
       
        return q.execute();
    }

    public static abstract class MessageReference {
    	
    	protected final Board mBoard;
    	
    	protected Message mMessage;
    	
    	protected Date mMessageDate;
    	
    	protected final int mMessageIndex;

    	
    	private MessageReference(Board myBoard, int myMessageIndex) {
        	if(myBoard == null)
        		throw new NullPointerException();
        	
    		mBoard = myBoard;
    		mMessage = null;
    		mMessageDate = null;
    		mMessageIndex = myMessageIndex;
    		
    		assert(mMessageIndex >= mBoard.getFreeMessageIndex());
    	}
    	
    	private MessageReference(Board myBoard, Message myMessage, int myMessageIndex) {
    		this(myBoard, myMessageIndex);
    		
    		if(myMessage == null)
    			throw new NullPointerException();
    		
    		mMessage = myMessage;
    		mMessageDate = mMessage.getDate();
    	}
    	
        /** Get the message to which this reference points */
        public synchronized Message getMessage() {
            /* We do not have to initialize mBoard and can assume that it is initialized because a MessageReference will only be loaded
             * by the board it belongs to. */
        	mBoard.db.activate(this, 3); // FIXME: Figure out a reasonable depth
        	mMessage.initializeTransient(mBoard.db, mBoard.mMessageManager);
            return mMessage;
        }
        
        /** Get an unique index number of this message in the board where which the query for the message was executed.
         * This index number is needed for NNTP and for synchronization with client-applications: They can check whether they have all messages by querying
         * for the highest available index number. */
        public int getIndex() {
        	return mMessageIndex;
        }
        
        /**
         * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
         */
        protected void storeWithoutCommit(ExtObjectContainer db) {
        	try {
        		if(db.ext().isStored(this) && !db.ext().isActive(this))
        			throw new RuntimeException("Trying to store a non-active MessageReference object");

        		db.store(this);
        	}
        	catch(RuntimeException e) {
        		db.rollback(); Logger.error(this, "ROLLED BACK!", e);
        		throw e;
        	}
        }
    }

    
    // FIXME: This class was made static so that it does not store an internal reference to it's Board object because we store that reference in mBoard already,
    // for being able to access it with db4o queries. Reconsider whether it should really be static: It would be nice if db4o did store an index on mMessageIndex
    // *per-board*, and not just a global index - the message index is board-local anyway! Does db4o store a per-board index if the class is not static???
    /**
     * Helper class to associate messages with boards in the database
     */
    public static class BoardReplyLink extends MessageReference { /* TODO: This is only public for configuring db4o. Find a better way */
        
        private final String mThreadID;

        protected BoardReplyLink(Board myBoard, Message myMessage, int myIndex) {
        	super(myBoard, myMessage, myIndex);
            
            try {
            	mThreadID = mMessage.getThreadID();
            }
            catch(NoSuchMessageException e) {
            	throw new IllegalArgumentException("Trying to create a BoardReplyLink for a thread, should be a BoardThreadLink.");
            }
            
        }
        
        public String getThreadID() {
        	return mThreadID;
        }

    }
    
    public final static class BoardThreadLink  extends MessageReference {
        
        private final String mThreadID;
        
    	private Date mLastReplyDate;
    	
    	
    	protected BoardThreadLink(Board myBoard, Message myThread, int myMessageIndex) {
    		super(myBoard, myThread, myMessageIndex);
    		
    		if(myThread == null)
    			throw new NullPointerException();
    		
    		mThreadID = mMessage.getID();
    		mLastReplyDate = myThread.getDate();
    	}
    	
    	/**
    	 * @param myLastReplyDate The date of the last reply to this thread. This parameter must be specified at creation to prevent threads from being hidden if
    	 * 							the user of this constructor forgot to call updateLastReplyDate() - thread display is sorted descending by reply date!
    	 */
    	protected BoardThreadLink(Board myBoard, String myThreadID, Date myLastReplyDate, int myMessageIndex) {
    		super(myBoard, myMessageIndex);
    		
    		if(myThreadID == null)
    			throw new NullPointerException();
    		
    		// TODO: We might validate the thread id here. Should be safe not to do so because it is taken from class Message which validates it.
    		
    		mThreadID = myThreadID;
    		mLastReplyDate = myLastReplyDate;
    	}
		
		protected synchronized void updateLastReplyDate(Date newDate) {
			if(newDate.after(mLastReplyDate))
				mLastReplyDate = newDate;
		}
		
		public synchronized Date getLastReplyDate() {
			return mLastReplyDate;
		}
    	
		public String getThreadID() {
			return mThreadID;
		}
		
		@Override
		public synchronized Message getMessage() {
			if(mMessage == null)
				return null;
			
			return super.getMessage();
		}
		
		public synchronized void setMessage(Message myThread) {
			if(myThread == null)
				throw new NullPointerException();
			
			if(myThread.getID().equals(mThreadID) == false)
				throw new IllegalArgumentException();
			
			mMessage = myThread;
			
			updateLastReplyDate(myThread.getDate());
		}
	
    }

}
