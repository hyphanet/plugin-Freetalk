/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import plugins.Freetalk.MessageRating;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NotTrustedException;
import freenet.support.Logger;

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
//@IndexedField // I can't think of any query which would need to get all WoTMessageRating objects.
public final class WoTMessageRating extends MessageRating {
	
	private final byte mValue;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(WoTMessageRating.class);
	}
	

	protected WoTMessageRating(WoTOwnIdentity myRater, WoTMessage myMessage, byte myValue) {
		super(myRater, myMessage);
		
		if(myValue < -100 || myValue > 100)
			throw new IllegalArgumentException("Trust value can be in range of -100 to +100, an adjustment by a rating of " + myValue + " would result in an invalid trust value");
		
		mValue = myValue;
	}

	@Override public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		if(!(getRater() instanceof WoTIdentity))
			throw new IllegalStateException("getRater() == " + getRater());
		
		checkedActivate(1);
		if(mValue < - 100 || mValue > 100)
			throw new IllegalStateException("mValue ==  "+ mValue);
	}

	
	public byte getValue() {
		checkedActivate(1);
		return mValue;
	}

	@Override public String toString() {
		if(mDB != null)
			return getRater() + " has rated the message " + getMessage() + " with " + getValue() + " points.";
		else
			return super.toString();
	}

	
	/**
	 * @throws IllegalArgumentException If the addition of the value would cause the trust value limit to be exceeded.
	 */
	private void addValueToWoTTrust(final byte value) throws NoSuchIdentityException {
		final WoTIdentityManager identityManager = mFreetalk.getIdentityManager();
		final WoTOwnIdentity rater = (WoTOwnIdentity)getRater();
		final WoTIdentity messageAuthor = (WoTIdentity)getMessageAuthor();
		
		byte trustValue;
		
		try {
			trustValue = identityManager.getTrust(rater, messageAuthor);
		} catch(NoSuchIdentityException e) {
			throw e;
		} catch (NotTrustedException e) {
			trustValue = 0;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
					
		trustValue += value;
		
		int signum = Integer.signum(trustValue);
		
		if(trustValue*signum > 100)
			throw new IllegalArgumentException("The rating cannot be assigned because the limit of +/- 100 for trust values would be exceeded.");		
		
		try {
			identityManager.setTrust(rater, messageAuthor, trustValue, "Freetalk web interface");
		} catch(NoSuchIdentityException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void addValueToWoTTrust() throws NoSuchIdentityException {
		addValueToWoTTrust(getValue());
	}
	
	private void substractValueFromWoTTrust() throws NoSuchIdentityException {
		addValueToWoTTrust((byte)-getValue());
	}
	
	protected void storeAndCommit() {
		synchronized(Persistent.transactionLock(mDB)) {
			if(logDEBUG) Logger.debug(this, "Storing rating " + this);
			try {
				addValueToWoTTrust();
			} catch(NoSuchIdentityException e) {
				throw new RuntimeException(e);
			}
		
			try {
				super.storeWithoutCommit();
				super.checkedCommit(this);
			} catch(RuntimeException e1) {
				try {
					substractValueFromWoTTrust();
				} catch(Exception e2) {
					super.checkedRollbackAndThrow(
					 new RuntimeException("Fatal error: The rating was stored in WoT, storing in Freetalk failed and removing in WoT again also failed!" +
							"This means that you cannot remove the rating anymore using Freetalk." +
							"Please manually remove the rating from the trust value of the identity! The original error was: " + e1 + " and the second " + e2)
					);
				}
				super.checkedRollbackAndThrow(e1);
			}
		}
	}
	
	/**
	 * @param undoTrustChange Whether to undo the application of the trust value of this rating to WoT. Must not be set to true during automatic operation
	 * 		because it can fail if the user has manually adjusted the trust value.
	 */
	protected void deleteAndCommit(boolean undoTrustChange) {
		synchronized(Persistent.transactionLock(mDB)) {
			if(logDEBUG) Logger.debug(this, "Deleting rating " + this);
			if(undoTrustChange) {
			try {
				substractValueFromWoTTrust();
			} catch(NoSuchIdentityException e) {
				throw new RuntimeException(e);
			}
			}
		
			try {
				super.deleteWithoutCommit();
				super.checkedCommit(this);
			} catch(RuntimeException e1) {
				try {
					addValueToWoTTrust();
				} catch(NoSuchIdentityException e) {
					Logger.error(this, "Deleting the MessageRating failed and re-adding the trust value also failed because the identity was deleted already.", e);
				} catch(RuntimeException e2) {
					super.checkedRollbackAndThrow(
					 new RuntimeException("Fatal error: The rating was removed from WoT, deleting it in Freetalk failed and restoring the rating in WoT also failed!" +
							"This means that the rating still appears in Freetalk but the trust value of the identity does not contain it anymore." +
							"Please manually remove the rating from the trust value of the identity! The original error was: " + e1 + " and the second " + e2)
					);
				}
				super.checkedRollbackAndThrow(e1);
			}
		}
	}

	@Override protected void storeWithoutCommit() {
		throw new UnsupportedOperationException("Please use storeAndCommit()");
	}

	@Override protected void deleteWithoutCommit() {
		throw new UnsupportedOperationException("Please use deleteAndCommit()");
	}
}