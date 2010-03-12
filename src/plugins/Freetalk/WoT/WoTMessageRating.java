package plugins.Freetalk.WoT;

import plugins.Freetalk.MessageRating;
import plugins.Freetalk.exceptions.NotTrustedException;

/**
 * A rating for a WoTMessage.
 * 
 * It is an additive adjustment of the trust value of an identity.
 * For example if your own identity has assigned a trust value of 30 to identity X and you assign a 
 * WoTMessageRating with a value of 10 to the identity it will get a trust value of 40.
 * 
 * Freetalk does not allow the storage of a new rating which would cause the trust value to exceed the -100 or +100 lower/upper limit.<br />
 * This must be handled this way:<br />
 * The UI must allow reverting of ratings. Reverting a positive rating is equal to subtraction of the given points.<br />
 * If storage of ratings was allowed where the total sum would be > 100, reverting all ratings would result in the target identity being distrusted,
 * even though all removed ratings were positive - if all removed ratings were positive the resulting trust value should be neutral, not negative.<br />
 * The same applies for negative ratings with flipped signs.<br />
 * This means that the checkedCommit() function throws a RuntimeException in case of a not allow rating.<br />
 * It must happen at the commit stage and not in the storeWithoutCommit function because storing can be aborted by rollback at any point
 * and the handling in the rollback function would be complicated because it would have to check whether the rating was already applied.<br /><br />
 * 
 * There, there is an IMPORTANT restriction of class WoTMessageRating: Only commit transactions using the WoTMessageRating.commit()
 * function, do not use the static version of this function! 
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class WoTMessageRating extends MessageRating {
	
	private final byte mValue;

	protected WoTMessageRating(WoTOwnIdentity myRater, WoTMessage myMessage, byte myValue) {
		super(myRater, myMessage);
		
		if(myValue < -100 || myValue > 100)
			throw new IllegalArgumentException("Trust value can be in range of -100 to +100, an adjustment by a rating of " + myValue + " would result in an invalid trust value");
		
		mValue = myValue;
	}
	
	public byte getValue() {
		return mValue;
	}
	
	public String toString() {
		if(mDB != null)
			return getRater() + " has rated the message " + getMessage() + " with " + mValue + " points.";
		else
			return super.toString();
	}
	
	protected void storeWithoutCommit() { // Override visibility
		super.storeWithoutCommit();
	}

	public void checkedCommit(Object loggingObject) {
		final WoTIdentityManager identityManager = mFreetalk.getIdentityManager();
		final WoTOwnIdentity rater = (WoTOwnIdentity)getRater();
		final WoTIdentity messageAuthor = (WoTIdentity)getMessageAuthor();
		
		byte trustValue;
		
		try {
			trustValue = identityManager.getTrust(rater, messageAuthor);
		} catch (NotTrustedException e) {
			trustValue = 0;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
					
		trustValue += mValue;
		
		int signum = Integer.signum(trustValue);
		
		if(trustValue*signum > 100)
			throw new RuntimeException("The rating cannot be assigned because the limit of +/- 100 for trust values would be exceeded.");		
		
		try {
			identityManager.setTrust(rater, messageAuthor, trustValue, "Freetalk web interface");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		super.checkedCommit(loggingObject);
	}
}