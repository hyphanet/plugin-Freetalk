/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import plugins.Freetalk.Persistent.IndexedClass;
import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.l10n.ISO639_3;
import freenet.support.Logger;
import freenet.support.StringValidityChecker;
import freenet.support.codeshortification.IfNull;

/**
 * Represents a forum / newsgroups / discussion board in Freetalk. Boards are created by the <code>MessageManager</code> on demand, you do
 * not need to manually create them. The <code>MessageManager</code> takes care of anything related to boards, to someone who just wants to
 * write a user interface this class can be considered as read-only.
 *
 * @author xor (xor@freenetproject.org)
 */
@IndexedClass // TODO: Check whether we need the index
public class Board extends Persistent implements Comparable<Board> {

    /* Constants */

    private static transient final Map<String, ISO639_3.LanguageCode> ALLOWED_LANGUAGES = Collections.unmodifiableMap(loadAllowedLanguages());

    // Characters not allowed in board names:
    //  ! , ? * [ \ ] (space)  not allowed by NNTP
    //  / : < > | "            not allowed in filenames on certain platforms
    //                         (a problem for some newsreaders)
    private static transient final String DISALLOWED_NAME_CHARACTERS = "!,?*[\\] /:<>|\"";

    public static transient final int MAX_BOARDNAME_TEXT_LENGTH = 256;


    /* Attributes, stored in the database */
    
    @IndexedField
    private final String mID;

    @IndexedField
    private final String mName;
    
    private String mDescription;
    
    /** True if at least one {@link SubscribedBoard} for this Board exists, i.e. if we should download messages of this board. */
    private boolean mHasSubscriptions;
    
    private int mNextFreeMessageIndex = 1;

    private static final Map<String, ISO639_3.LanguageCode> loadAllowedLanguages() {
    	final ISO639_3 iso639_3 = new ISO639_3();
    	
    	// Get all real (non-symbolic) and living languages
    	final HashMap<String, ISO639_3.LanguageCode> languages = new HashMap<String, ISO639_3.LanguageCode>(
    				iso639_3.getLanguagesByScopeAndType(ISO639_3.LanguageCode.Scope.Individual, ISO639_3.LanguageCode.Type.Living)
    			); // Convert from Hashtable to HashMap, we do not need synchronization.
    	
    	// Add the special code for multiple languages
    	final ISO639_3.LanguageCode multilingual = iso639_3.getMultilingualCode();
    	languages.put(multilingual.id, multilingual);
    	
    	// Latin is still being taught in schools. Type == Ancient, therefore not in result of getLanguagesByScopeAndType
    	final ISO639_3.LanguageCode lat = iso639_3.getLanguages().get("lat");
    	IfNull.thenThrow(lat);
    	languages.put("lat", lat);
    	
    	// Esperanto, added by request. Scope == "Constructed", therefore not in result of getLanguagesByScopeAndType
    	final ISO639_3.LanguageCode epo = iso639_3.getLanguages().get("epo");
    	IfNull.thenThrow(epo);
    	languages.put("epo", epo);
    	
    	// Klingon, easter-egg for nerds. Scope == "Constructed", therefore not in result of getLanguagesByScopeAndType
    	final ISO639_3.LanguageCode tlh = iso639_3.getLanguages().get("tlh");
    	IfNull.thenThrow(tlh);
    	languages.put("tlh", tlh);
    	
        return languages;
    }
    
    public static final Map<String, ISO639_3.LanguageCode> getAllowedLanguages() {
    	return ALLOWED_LANGUAGES;
    }

    /**
     * Create a board. You have to store() it yourself after creation.
     * Only for being used directly by unit tests. The client interface for creating boards is {@link MessageManager.getOrCreateBoard}.
     * 
     * @param newName The name of the board. For restrictions, see <code>isNameValid()</code>
     * @throws InvalidParameterException If none or an invalid name is given.
     */
    public Board(String newName, String description, boolean hasSubscriptions) throws InvalidParameterException {
        if(newName==null || newName.length() == 0)
            throw new IllegalArgumentException("Empty board name.");
        if(!isNameValid(newName))
            throw new InvalidParameterException("Invalid board name."); // TODO: Explain what is invalid

        mID = UUID.randomUUID().toString();
        mName = newName.toLowerCase();
        mDescription = description != null ? description : "";
        mHasSubscriptions = hasSubscriptions;
    }

	@Override
	public void databaseIntegrityTest() throws Exception {
		checkedActivate(1); // String, int, boolean are db4o primitive types, depth 1 is enough
		
	    if(mID == null)
	    	throw new NullPointerException("mID==null");
	    
	    try {
	    	UUID.fromString(mID);
	    } catch(Exception e) {
	    	throw new IllegalStateException("mID is invalid UUID: " + mID);
	    }
	    
	    if(mName == null)
	    	throw new NullPointerException();
	    
	    if(!isNameValid(mName))
	    	throw new IllegalStateException("mName is invalid: " + mName);
	    
	    if(mHasSubscriptions != (mFreetalk.getMessageManager().subscribedBoardIterator(mName).size() != 0))
	    	throw new IllegalStateException("mHasSubscriptions is wrong: " + mHasSubscriptions);
	    
	    if(mNextFreeMessageIndex < 1)
	    	throw new IllegalStateException("mNextFreeMessageIndex is illegal: " + mNextFreeMessageIndex);
	    
	    if(getDownloadedMessagesAfterIndex(mNextFreeMessageIndex-1).size() != 0)
	    	throw new IllegalStateException("mNextFreetMessageIndex is wrong: " + mNextFreeMessageIndex);
	}

    /**
     * Store this object in the database. You have to initializeTransient() before.
     * 
     * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
     */
    @Override protected void storeWithoutCommit() {
    	super.storeWithoutCommit(1); // String, int, boolean are db4o primitive types, depth 1 is enough
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
    public static final boolean isNameValid(String name) {
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

        String[] parts = name.split("\\.", -1); // The 1-argument version will not return empty parts!
        if (parts.length < 2)
            return false;

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() == 0 || !StringValidityChecker.containsNoInvalidFormatting(parts[i]))
                return false;
        }

        // first part of name must be a recognized language code

        return (ALLOWED_LANGUAGES.containsKey(parts[0]));
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
    public final String getID() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
    	return mID;
    }
    
    /**
     * @return Returns the language code of the board. It is the token before the first '.' in the name of the board.
     */
    public final ISO639_3.LanguageCode getLanguage() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
    	return ALLOWED_LANGUAGES.get(mName.substring(0, mName.indexOf('.')));
    }

    /**
     * @return The name of this board. Only one board with a given name can exist at once. The name is case-insensitive.
     */
    public final String getName() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
        return mName;
    }
    
    /**
     * @see getName()
     */
    public final String getNameWithoutLanguagePrefix() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
        return mName.substring(mName.indexOf('.')+1);
    }
    
    /**
     * In the current implementation, returns the hardcoded description if there is one.
     * No user specified descriptions are supported yet, we just hardcode descriptions of the default boards. 
     * 
     * TODO: The future implementation should do that:
     * Returns the description of this board from the view of the given {@link OwnIdentity}.
     * This shall be voted by the community and the description with the most votes shall be returned.
     * "From the view of the given OwnIdentity" means that votes are only counted when the own identity trusts the voter.
     */
    public String getDescription(OwnIdentity viewer) {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		return mDescription;
    }
    
    protected boolean setDescription(String newDescription) {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
    	
		boolean result = false;
		if(mDescription == null || !mDescription.equals(newDescription))
			result = true;
		
		mDescription = newDescription;
		
		return result;
    }

    public final Date getFirstSeenDate() {
		checkedActivate(1); // Date is a db4o primitive type so 1 is enough
        return mCreationDate;
    }
    
    /**
     * @return Returns true if at least one {@link SubscribedBoard} for this board exists, i.e. if we should download messages for this board.
     */
    public final boolean hasSubscriptions() {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
    	return mHasSubscriptions;
    }
    
    /**
     * Set the "has subscriptions" flag (see {@link hasSubscriptions}) of this board.
     * 
     * This function must be called by the {@link MessageManager} when the amount of {@link SubscribedBoard} objects for this board changes from zero to positive
     * or positive to zero because the "has subscriptions" flag is a cached boolean and therefore is NOT auto-updated by the database when you delete the last
     * {@link SubscribedBoard} object or create the first one. 
     */
	protected final void setHasSubscriptions(boolean hasSubscriptions) {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
		mHasSubscriptions = hasSubscriptions;
	}

    /**
     * Compare boards by comparing their names; provided so we can sort an array of boards.
     */
    @Override public int compareTo(Board b) {
        return getName().compareTo(b.getName());
    }
    
    /**
     * Returns true if the given {@link Message} has this board's name listed in it's target board names.
     * Does not check whether the {@link Board} object referenced by the message is equal to this {@link Board} object!
     */
    public final boolean contains(Message message) {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
    	
    	for(Board board : message.getBoards()) {
    		if(mName.equals(board.getName()))
    			return true;
    	}
    	
    	return false;
    }
    
    /**
     * A DownloadedMessageLink links an actually downloaded message to a board - in opposite to {@link MessageList.MessageReference} which mark messages which might be downloaded or not.  
     * These DownloadedMessageLink objects are used for querying the database for messages which belong to a certain board.
     * - Since a message can be posted to multiple boards, we need these helper objects for being able to do fast queries on the message lists of boards.
     */
    // @IndexedClass // I can't think of any query which would need to get all DownloadedMessageLink objects.
    public static final class DownloadedMessageLink extends Persistent {
    	
    	@IndexedField
    	private final Board mBoard;
    	
    	@IndexedField
    	private final int mIndex;
    	
    	@IndexedField
    	private final Message mMessage;
    	
    	private final Identity mAuthor;
    	
    	private DownloadedMessageLink(Board myBoard, Message myMessage, int myIndex) {
    		if(myBoard == null) throw new NullPointerException();
    		if(myMessage == null) throw new NullPointerException();
    		if(myIndex <= 0) throw new IllegalArgumentException();
    		
    		mBoard = myBoard;
    		mMessage = myMessage;
    		mIndex = myIndex;
    		mAuthor = myMessage.getAuthor();
    		
    		assert(mIndex == (mBoard.mNextFreeMessageIndex-1));
    	}


		@Override
		public void databaseIntegrityTest() throws Exception {
			checkedActivate(2); // One higher than necessary, we call getters on the member objects anyway
			
			if(mBoard == null)
				throw new NullPointerException("mBoard==null");
	    	
			if(mIndex < 1)
				throw new IllegalStateException("mIndex is illegal: " + mIndex);
			
			final Board board = getBoard(); 
			
			// The primary reason for calling it is to ensure that the index is only taken once:
			// It should throw a DuplicateMessageException if there are multiple...
			if(board.getDownloadedMessageByIndex(mIndex) != this)
				throw new IllegalStateException("getMessageByIndex is broken");
	    	
			if(mMessage == null)
				throw new NullPointerException("mMessage==null");
			
			final Message message = getMessage();
			
			if(!board.contains(message))
				throw new IllegalStateException("mMessage does not belong in this Board: mBoard==" + mBoard 
						+ "; mMessage.getBoards()==" + mMessage.getBoards()
						+ "; mMessage==" + mMessage);
			
			// The primary reason for calling it is to ensure that the message is only linked once:
			// It should throw a DuplicateMessageException if there are multiple...
			if(board.getDownloadedMessageLink(message) != this)
				throw new IllegalStateException("getMessageLink is broken");
			
			if(mAuthor == null)
				throw new NullPointerException("mAuthor==null");
			
			if(message.getAuthor() != mAuthor)
				throw new IllegalStateException("mAuthor is wrong: mAuthor==" + mAuthor 
						+ "; mMessage.getAuthor()==" + message.getAuthor()
						+ "; mMessage==" + mMessage);
			
		}

    	public Board getBoard() {
    		checkedActivate(1);
    		mBoard.initializeTransient(mFreetalk);
    		return mBoard;
    	}
    	
    	public Message getMessage() {
    		checkedActivate(1);
    		mMessage.initializeTransient(mBoard.mFreetalk);
    		return mMessage;
    	}
    	
    	public int getMessageIndex() {
    		checkedActivate(1); // int is a db4o primitive type so 1 is enough
    		return mIndex;
    	}
    	
    	public Identity getAuthor() {
    		checkedActivate(1);
    		if(mAuthor instanceof Persistent)
    			((Persistent)mAuthor).initializeTransient(mFreetalk);
    		return mAuthor;
    	}
    	

        /**
         * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
         */
        @Override protected void storeWithoutCommit() {
        	try {
        		checkedActivate(1);
        		throwIfNotStored(mBoard);
        		throwIfNotStored(mMessage);
        		throwIfNotStored(mAuthor);
        		checkedStore();
        	}
        	catch(RuntimeException e) {
        		checkedRollbackAndThrow(e);
        	}
        }

        @Override protected void deleteWithoutCommit() {
    		deleteWithoutCommit(2);
		}

    }
    
	protected final DownloadedMessageLink getDownloadedMessageLink(Message message) throws NoSuchMessageException {
    	Query q = mDB.query();
    	q.constrain(DownloadedMessageLink.class);
    	q.descend("mMessage").constrain(message).identity();
    	q.descend("mBoard").constrain(this).identity();
    	ObjectSet<DownloadedMessageLink> messageLinks = new Persistent.InitializingObjectSet<Board.DownloadedMessageLink>(mFreetalk, q);
    	
    	switch(messageLinks.size()) {
    		case 0: throw new NoSuchMessageException(message.getID());
    		case 1: return messageLinks.next();
    		default: throw new DuplicateMessageException(message.getID());
    	}
    }
    
    
    /**
     * @return Returns the next free message index and increments the internal free message index counter - therefore, the message index will be taken even if
     * 	you do not store any message with it. This ensures that deleting the head message cannot cause it's index to be associated with a new, different message.
     */
	protected final synchronized int takeFreeMessageIndexWithoutCommit() {
		checkedActivate(1); // int is a db4o primitive type so 1 is enough
		int result = mNextFreeMessageIndex++;
		storeWithoutCommit();
		return result;
    }
    
    protected final ObjectSet<DownloadedMessageLink> getDownloadedMessagesAfterIndex(int index) {
        Query q = mDB.query();
        q.constrain(DownloadedMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mIndex").constrain(index).greater();
        return new Persistent.InitializingObjectSet<DownloadedMessageLink>(mFreetalk, q.execute());
    }
    
    private final DownloadedMessageLink getDownloadedMessageByIndex(int index) throws NoSuchMessageException {
        final Query q = mDB.query();
        q.constrain(DownloadedMessageLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mIndex").constrain(index);
    	final ObjectSet<DownloadedMessageLink> messageLinks = new Persistent.InitializingObjectSet<Board.DownloadedMessageLink>(mFreetalk, q);
    	
    	switch(messageLinks.size()) {
    		case 0: throw new NoSuchMessageException("index: " + index);
    		case 1: return messageLinks.next();
    		default: throw new DuplicateMessageException("index: " + index);
    	}
    }
    
    public final synchronized int getDownloadedMessageCount() {
    	final Query query = mDB.query();
    	query.constrain(DownloadedMessageLink.class);
    	query.descend("mBoard").constrain(this).identity();
    	return query.execute().size();
    }
    
    /**
     * Sanity check function for checking whether the given message really belongs to this board.
     * @throws IllegalArgumentException If the message does not belong to this board according to its board list or if it is an OwnMessage.
     */
    protected final void throwIfNotAllowedInThisBoard(Message newMessage) {
    	if(newMessage instanceof OwnMessage) {
    		/* We do not add the message to the boards it is posted to because the user should only see the message if it has been downloaded
    		 * successfully. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
    		throw new IllegalArgumentException("Adding OwnMessages to a board is not allowed.");
    	}
    	
    	if(contains(newMessage) == false)
    		throw new IllegalArgumentException("addMessage called with a message which was not posted to this board (" + getName() + "): " +
    				newMessage);
    }
    
    /**
     * Called by the {@link MessageManager} when a new message was fetched.
     * Stores any messages in this board, does not check whether any {@link OwnIdentity} actually wants the messages.
     * 
     * The purpose of this is:
     * - that the message manager can fill a new {@link SubscribedBoard} with already downloaded messages from it's parent {@link Board}
     * - that the message manager can tell existing subscribed boards to pull new messages from their parent boards. 
     * 
     * @throws IllegalArgumentException When trying to add a message which does not belong to this board or trying to add an OwnMessage.
     * @throws RuntimeException If storing this Board to the database fails. 
     */
    protected synchronized void addMessage(Message newMessage) throws Exception {
    	throwIfNotAllowedInThisBoard(newMessage);
    	
    	try {
    		getDownloadedMessageLink(newMessage);
    		Logger.error(this, "addMessage() called for already existing message: " + newMessage);
    	}
    	catch(NoSuchMessageException e) {
    		final DownloadedMessageLink link = new DownloadedMessageLink(this, newMessage, takeFreeMessageIndexWithoutCommit());
    		link.initializeTransient(mFreetalk);
    		link.storeWithoutCommit();
    	}
    }
    
    protected synchronized void deleteMessage(Message message) throws NoSuchMessageException {
    	getDownloadedMessageLink(message).deleteWithoutCommit();
    }

    @Override
    public String toString() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
    	return super.toString() + " with mName: " + mName;
    }
}
