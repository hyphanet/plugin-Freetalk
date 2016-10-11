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
 * {@docRoot}
 * @author xor (xor@freenetproject.org)
 */
@IndexedClass
public class Board extends Persistent implements Comparable<Board> {

    private static transient final Map<String, ISO639_3.LanguageCode> ALLOWED_LANGUAGES = Collections.unmodifiableMap(loadAllowedLanguages());
    private static transient final String DISALLOWED_NAME_CHARACTERS = "!,?*[\\] /:<>|\"";
    public static transient final int MAX_BOARDNAME_TEXT_LENGTH = 256;

    @IndexedField
    private final String mID;
    private final String mName;
    private String mDescription;
    
    private boolean mHasSubscriptions;
    private int mNextFreeMessageIndex = 1;
    private static final Map<String, ISO639_3.LanguageCode> loadAllowedLanguages() {
    	final ISO639_3 iso639_3 = new ISO639_3();
    	final HashMap<String, ISO639_3.LanguageCode> languages = new HashMap<String, ISO639_3.LanguageCode>(
    				iso639_3.getLanguagesByScopeAndType(ISO639_3.LanguageCode.Scope.Individual, ISO639_3.LanguageCode.Type.Living)
    			);
    	final ISO639_3.LanguageCode multilingual = iso639_3.getMultilingualCode();
    	final ISO639_3.LanguageCode lat = iso639_3.getLanguages().get("lat");
    	final ISO639_3.LanguageCode epo = iso639_3.getLanguages().get("epo");
    	final ISO639_3.LanguageCode tlh = iso639_3.getLanguages().get("tlh");
    	languages.put(multilingual.id, multilingual);
    	languages.put("lat", lat);
    	languages.put("epo", epo);
    	languages.put("tlh", tlh);
    	IfNull.thenThrow(lat);
    	IfNull.thenThrow(epo);
    	IfNull.thenThrow(tlh);
        return languages;
    }
    
    public static final Map<String, ISO639_3.LanguageCode> getAllowedLanguages() {
    	return ALLOWED_LANGUAGES;
    }

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

    protected void storeWithoutCommit() {
    	super.storeWithoutCommit(1); // String, int, boolean are db4o primitive types, depth 1 is enough
    }

    public static final boolean isNameValid(String name) {
        if (name == null || name.length() == 0) {
            return false;
        }

        if (name.length() > MAX_BOARDNAME_TEXT_LENGTH) {
            return false;
        }

        if (!StringValidityChecker.containsNoLinebreaks(name)
                || !StringValidityChecker.containsNoInvalidCharacters(name)
                || !StringValidityChecker.containsNoControlCharacters(name)
                || !StringValidityChecker.containsNoIDNBlacklistCharacters(name))
            return false;

        for (Character c : name.toCharArray()) {
            if (DISALLOWED_NAME_CHARACTERS.indexOf(c) != -1)
                return false;
        }

        String[] parts = name.split("\\.", -1); // The 1-argument version will not return empty parts!
        if (parts.length < 2)
            return false;

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() == 0 || !StringValidityChecker.containsNoInvalidFormatting(parts[i]))
                return false;
        }

        return (ALLOWED_LANGUAGES.containsKey(parts[0]));
    }

    public final String getID() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
    	return mID;
    }

    public final ISO639_3.LanguageCode getLanguage() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
    	return ALLOWED_LANGUAGES.get(mName.substring(0, mName.indexOf('.')));
    }
    public final String getName() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
        return mName;
    }

    public final String getNameWithoutLanguagePrefix() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
        return mName.substring(mName.indexOf('.')+1);
    }

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
    
    public final boolean hasSubscriptions() {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
    	return mHasSubscriptions;
    }

	protected final void setHasSubscriptions(boolean hasSubscriptions) {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
		mHasSubscriptions = hasSubscriptions;
	}

    public int compareTo(Board b) {
        return getName().compareTo(b.getName());
    }

    public final boolean contains(Message message) {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
    	
    	for(Board board : message.getBoards()) {
    		if(mName.equals(board.getName()))
    			return true;
    	}
    	
    	return false;
    }

    public static final class DownloadedMessageLink extends Persistent {
    	
    	@IndexedField
    	private final Board mBoard;
    	private final int mIndex;
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
			if(board.getDownloadedMessageByIndex(mIndex) != this)
				throw new IllegalStateException("getMessageByIndex is broken");
			if(mMessage == null)
				throw new NullPointerException("mMessage==null");
			final Message message = getMessage();
			if(!board.contains(message))
				throw new IllegalStateException("mMessage does not belong in this Board: mBoard==" + mBoard 
						+ "; mMessage.getBoards()==" + mMessage.getBoards()
						+ "; mMessage==" + mMessage);
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

        protected void storeWithoutCommit() {
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
        
    	protected void deleteWithoutCommit() {
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

    protected final void throwIfNotAllowedInThisBoard(Message newMessage) {
    	if(newMessage instanceof OwnMessage) {
    		throw new IllegalArgumentException("Adding OwnMessages to a board is not allowed.");
    	}
    	
    	if(contains(newMessage) == false)
    		throw new IllegalArgumentException("addMessage called with a message which was not posted to this board (" + getName() + "): " +
    				newMessage);
    }

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
