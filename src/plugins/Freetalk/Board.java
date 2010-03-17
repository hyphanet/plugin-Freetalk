/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectSet;
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
public class Board extends Persistent implements Comparable<Board> {

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
    
    /** True if at least one {@link SubscribedBoard} for this Board exists, i.e. if we should download messages of this board. */
    private boolean mHasSubscriptions;
    
    private int mNextFreeMessageIndex = 1;


    /**
     * Get a list of fields which the database should create an index on.
     */
    static {
        registerIndexedFields(Board.class, new String[] { "mID", "mName" });
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
            throw new InvalidParameterException("Invalid board name."); // TODO: Explain what is invalid

        mID = UUID.randomUUID().toString();
        mName = newName.toLowerCase();
        mFirstSeenDate = CurrentTimeUTC.get();
        mHasSubscriptions = false;
    }

    /**
     * Store this object in the database. You have to initializeTransient() before.
     * 
     * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
     */
    protected void storeWithoutCommit() {
    	super.storeWithoutCommit(3); // TODO: Figure out a suitable depth.
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
    
    public String getDescription(FTOwnIdentity viewer) {
		// TODO: Implement: Use the description which most of the known identities have chosen.
		return "";
    }

    public Date getFirstSeenDate() {
        return mFirstSeenDate;
    }
    
    /**
     * @return Returns true if at least one {@link SubscribedBoard} for this board exists, i.e. if we should download messages for this board.
     */
    public boolean hasSubscriptions() {
    	return mHasSubscriptions;
    }
    
    /**
     * Set the "has subscriptions" flag (see {@link hasSubscriptions}) of this board.
     * 
     * This function must be called by the {@link MessageManager} when the amount of {@link SubscribedBoard} objects for this board changes from zero to positive
     * or positive to zero because the "has subscriptions" flag is a cached boolean and therefore is NOT auto-updated by the database when you delete the last
     * {@link SubscribedBoard} object or create the first one. 
     */
	protected void setHasSubscriptions(boolean hasSubscriptions) {
		mHasSubscriptions = hasSubscriptions;
	}

    /**
     * Compare boards by comparing their names; provided so we can sort an array of boards.
     */
    public int compareTo(Board b) {
        return getName().compareTo(b.getName());
    }
    
    /**
     * Returns true if the given {@link Message} has this board's name listed in it's target board names.
     * Does not check whether the {@link Board} object referenced by the message is equal to this {@link Board} object!
     */
    public boolean contains(Message message) {
    	for(Board board : message.getBoards()) {
    		if(mName.equals(board.getName()))
    			return true;
    	}
    	
    	return false;
    }
    
    public static final class BoardMessageLink extends Persistent {
    	
    	private final Board mBoard;
    	
    	private final Message mMessage;
    	
    	private final int mMessageIndex;
    	
    	private final FTIdentity mAuthor;
    	
    	private BoardMessageLink(Board myBoard, Message myMessage, int myIndex) {
    		if(myBoard == null) throw new NullPointerException();
    		if(myMessage == null) throw new NullPointerException();
    		if(myIndex <= 0) throw new IllegalArgumentException();
    		
    		mBoard = myBoard;
    		mMessage = myMessage;
    		mMessageIndex = myIndex;
    		mAuthor = myMessage.getAuthor();
    		
    		assert(mMessageIndex == (mBoard.mNextFreeMessageIndex-1));
    	}
    	
    	static {
    		registerIndexedFields(BoardMessageLink.class, new String[] { "mBoard" , "mMessage" });
    	}
    	
    	public Board getBoard() {
    		return mBoard;
    	}
    	
    	public Message getMessage() {
    		mMessage.initializeTransient(mBoard.mFreetalk);
    		return mMessage;
    	}
    	
    	public int getMessageIndex() {
    		return mMessageIndex;
    	}
    	
    	public FTIdentity getAuthor() {
    		return mAuthor;
    	}
    	

        /**
         * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
         */
        protected void storeWithoutCommit() {
        	try {
        		checkedActivate(3); // TODO: Figure out a suitable depth.
        		throwIfNotStored(mBoard);
        		throwIfNotStored(mMessage);
        		throwIfNotStored(mAuthor);
        		checkedStore();
        	}
        	catch(RuntimeException e) {
        		checkedRollbackAndThrow(e);
        	}
        }
        
    	protected void deleteWithoutCommit() {
    		deleteWithoutCommit(3); // TODO: Figure out a suitable depth.
		}
    	
    }
    
    @SuppressWarnings("unchecked")
	private BoardMessageLink getMessageLink(Message message) throws NoSuchMessageException {
    	Query q = mDB.query();
    	q.constrain(BoardMessageLink.class);
    	q.descend("mMessage").constrain(message).identity();
    	q.descend("mBoard").constrain(this).identity();
    	ObjectSet<BoardMessageLink> messageLinks = q.execute();
    	
    	switch(messageLinks.size()) {
    		case 0: throw new NoSuchMessageException(message.getID());
    		case 1:
    			final BoardMessageLink link = messageLinks.next();
    			link.initializeTransient(mFreetalk);
    			return link;
    		default: throw new DuplicateMessageException(message.getID());
    	}
    }
    
    
    /**
     * @return Returns the next free message index and increments the internal free message index counter - therefore, the message index will be taken even if
     * 	you do not store any message with it. This ensures that deleting the head message cannot cause it's index to be associated with a new, different message.
     */
	protected synchronized int takeFreeMessageIndexWithoutCommit() {
		int result = mNextFreeMessageIndex++;
		storeWithoutCommit();
		return result;
    }
    
    @SuppressWarnings("unchecked")
    protected Iterable<BoardMessageLink> getMessagesAfterIndex(int index) {
        Query q = mDB.query();
        q.constrain(BoardMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessageIndex").constrain(index).greater();
        return new Persistent.InitializingObjectSet(mFreetalk, q.execute());
    }
    
    /**
     * Called by the {@link MessageManager} when a new message was fetched.
     * Stores any messages in this board, does not check whether any {@link FTOwnIdentity} actually wants the messages.
     * 
     * The purpose of this is:
     * - that the message manager can fill a new {@link SubscribedBoard} with already downloaded messages from it's parent {@link Board}
     * - that the message manager can tell existing subscribed boards to pull new messages from their parent boards. 
     * 
     * @throws IllegalArgumentException When trying to add a message which does not belong to this board or trying to add an OwnMessage.
     * @throws RuntimeException If storing this Board to the database fails. 
     */
    protected synchronized void addMessage(Message newMessage) throws Exception {
    	if(newMessage instanceof OwnMessage) {
    		// We do not add the message to the boards it is posted to because the user should only see the message if it has been downloaded
    		// successfully. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug
    		throw new IllegalArgumentException("Adding OwnMessages to a board is not allowed.");
    	}
    	
    	if(contains(newMessage) == false)
    		throw new IllegalArgumentException("addMessage called with a message which was not posted to this board (" + getName() + "): " + newMessage);
    	
    	try {
    		getMessageLink(newMessage);
    		Logger.error(this, "addMessage() called for already existing message: " + newMessage);
    	}
    	catch(NoSuchMessageException e) {
    		final BoardMessageLink link = new BoardMessageLink(this, newMessage, takeFreeMessageIndexWithoutCommit());
    		link.initializeTransient(mFreetalk);
    		link.storeWithoutCommit();
    	}
    }
    
    protected synchronized void deleteMessage(Message message) throws NoSuchMessageException {
    	getMessageLink(message).deleteWithoutCommit();
    }

}
