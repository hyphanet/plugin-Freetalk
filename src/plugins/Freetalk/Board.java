/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

import plugins.Freetalk.exceptions.InvalidParameterException;

import com.db4o.ext.ExtObjectContainer;

import freenet.support.CurrentTimeUTC;
import freenet.support.StringValidityChecker;

/**
 * Represents a forum / newsgroups / discussion board in Freetalk. Boards are created by the <code>MessageManager</code> on demand, you do
 * not need to manually create them. The <code>MessageManager</code> takes care of anything related to boards, to someone who just wants to
 * write a user interface this class can be considered as read-only.
 *
 * @author xor
 */
public class Board implements Comparable<Board> {

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


    /* References to objects of the plugin, not stored in the database. */

    protected transient ExtObjectContainer db;
    protected transient MessageManager mMessageManager;


    /**
     * Get a list of fields which the database should create an index on.
     */
    public static String[] getIndexedFields() {
        return new String[] { "mID", "mName" };
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
        mHasSubscriptions = false;
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
    		DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.

    		db.store(this);
    	}
    	catch(RuntimeException e) {
    		DBUtil.rollbackAndThrow(db, this, e);
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

}
