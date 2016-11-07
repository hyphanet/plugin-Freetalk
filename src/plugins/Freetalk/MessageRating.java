/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.support.Logger;

/**
 * A MessageRating is an assignment of a "good" / "bad" value from a {@link OwnIdentity} to a {@link Message}.
 * 
 * MessageRating objects are stored in the database.
 * There can only be one rating in the database for each message, therefore if the message is posted to multiple {@link Board}s and you rate it in one
 * board you cannot rate it in any other boards.
 * 
 * Message ratings are supposed to have an effect on plugin which Freetalk uses for obtaining identities from.
 * MessageRating is an abstract class so what this effect actually is depends on the implementation which is used.
 * 
 * Activation policy: MessageRating does automatic activation on its own.
 * This means that MessageRating can be activated to a depth of only 1 when querying them from the database.
 * All methods automatically activate the object to any needed higher depth.
 * 
 * @author xor (xor@freenetproject.org)
 */
//@IndexedClass // I can't think of any query which would need to get all MessageRating objects.
public abstract class MessageRating extends Persistent {
	
	/**
	 * The {@link OwnIdentity} which has assigned this rating. 
	 */
	@IndexedField
	private final OwnIdentity mRater;

	/**
	 * The affected {@link Message}.
	 */
	@IndexedField
	private final Message mMessage;
	
	/**
	 * The author of the affected {@link Message}
	 */
	@IndexedField
	private final Identity mMessageAuthor;


	/**
	 * Constructor for being used be the implementing child classes.
	 * 
	 * @param myRater The own identity which has assigned this rating. Must not be null. Cannot be changed after constructing the MessageRating.
	 * @param myMessage The message which is being rated. Must not be an instance of OwnMessage. Cannot be changed after constructing the MessageRating.
	 * @throws NullPointerException If any of the arguments is null.
	 * @throws IllegalArgumentException If the given Message is an instance of OwnMessage.
	 */
	protected MessageRating(final OwnIdentity myRater, final Message myMessage) {
		if(myRater == null)
			throw new NullPointerException("No own identity specified.");
		
		if(myMessage == null)
			throw new NullPointerException("No message specified.");
		
		if(myMessage instanceof OwnMessage)
			throw new IllegalArgumentException("Ratings cannot be assigned to objects of type OwnMessage.");
		
		mRater = myRater;
		mMessage = myMessage;
		mMessageAuthor = mMessage.getAuthor();
	}

	@Override public void databaseIntegrityTest() throws Exception {
		checkedActivate(1);
		
		if(mRater == null)
			throw new NullPointerException("mRater == null");
		
		if(mMessage == null)
			throw new NullPointerException("mMessage == null");
		
		if(mMessageAuthor == null)
			throw new NullPointerException("mMessageAuthor == null");
		
		if(getMessage().getAuthor() != mMessageAuthor)
			throw new IllegalStateException("mMessageAuthor == " + getMessageAuthor() + " but getMessage().getAuthor() == " + getMessage().getAuthor());
	}
	
	/**
	 * Get the {@link OwnIdentity} which has assigned this rating.
	 * 
	 * Before using any getter functions you must call {@link initializeTransient} once after having obtained this object from the database.
	 * The transient fields of the returned object will be initialized by this getter already though.
	 */
	public final OwnIdentity getRater() {
		checkedActivate(1); assert(mRater != null);
		if(mRater instanceof Persistent) {
			Persistent rater = (Persistent)mRater;
			rater.initializeTransient(mFreetalk);
		}
		return mRater;
	}
	
	/**
	 * Get the {@link Message} which is being rated. 
	 * 
	 * Before using any getter functions you must call {@link initializeTransient} once after having obtained this object from the database.
	 * The transient fields of the returned object will be initialized by this getter already though.
	 */
	public final Message getMessage() {
		checkedActivate(1); assert(mMessage != null);
		mMessage.initializeTransient(mFreetalk);
		return mMessage;
	}
	
	public final Identity getMessageAuthor() {
		checkedActivate(1); assert(mMessageAuthor != null);
		
		if(mMessageAuthor instanceof Persistent)
			((Persistent)mMessageAuthor).initializeTransient(mFreetalk);
			
		return mMessageAuthor;
	}

	@Override protected void storeWithoutCommit() {
		try {		
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(1);
			
			throwIfNotStored(getRater());
			throwIfNotStored(getMessage());
			throwIfNotStored(getMessageAuthor());
			
			checkedStore();
		}
		catch(final RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	@Override public String toString() {
		if(mDB != null)
			return getRater() + " has rated the message " + getMessage();
		
		// We do not throw a NPE because toString() is usually used in logging, we want the logging to be robust
		
		Logger.error(this, "toString() called before initializeTransient()!");
		
		return super.toString() + " (intializeTransient() not called!)";
	}

}
