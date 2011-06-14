/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.support.codeshortification.IfNull;

/**
 * For each pair of an OwnIdentity and a known Identity, an IdentityWantedState
 * is stored which tells whether the own identity wants to receive messages of the given identity
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class IdentityWantedState extends Persistent {
	
	@IndexedField
	private final OwnIdentity mOwner;
	
	@IndexedField
	private final Identity mRatedIdentity;
	
	private boolean mIsWanted;
	
	/**
	 * Null if the automatic wanted state computation shall be used.
	 * True if the user wants to forcefully set the wanted state to true, no matter which state is computed.
	 * False if the user wants to forcefully overwrite the wanted state to false.
	 */
	private Boolean mManualOverride; 
	
	
	public IdentityWantedState(final OwnIdentity myOwner, final Identity myRatedIdentity, final boolean isWanted, final Boolean manualOverride) {
		IfNull.thenThrow(myOwner);
		IfNull.thenThrow(myRatedIdentity);
		if(manualOverride != null && isWanted != manualOverride)
			throw new RuntimeException("myManualOverride != myIsWanted");
			
		mOwner = myOwner;
		mRatedIdentity = myRatedIdentity;
		mIsWanted = isWanted;
		mManualOverride = manualOverride;
	}

	@Override
	public void databaseIntegrityTest() throws Exception {
		checkedActivate(1);
		
		IfNull.thenThrow(mOwner, "mOwnIdentity");
		IfNull.thenThrow(mRatedIdentity, "mRatedIdentity");
		
		if(mManualOverride != null && mIsWanted != mManualOverride)
			throw new RuntimeException("mManualOverride == " + mManualOverride + " but mIsWanted == " + mIsWanted);
	}
	
	protected void deleteWithoutCommit() {
		try {
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(1);
			checkedDelete(mManualOverride); // TODO: Optimization: Does db4o require this?
			checkedDelete(this);
		}
		catch(final RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	/**
	 * Sets the computed wanted-state to the given value
	 * @return True, if the state was changed, false if it did not change.
	 */
	protected boolean set(boolean newWantedState) {
		checkedActivate(1);
		final boolean changed = (mManualOverride != null) ? (false) : (mIsWanted != newWantedState);
		mIsWanted = newWantedState;
		return changed;
	}
	
	protected boolean get() {
		checkedActivate(1); // TODO: Optimization: Does db4o require this for Boolean?
		return	mManualOverride != null ? mManualOverride : mIsWanted;
	}

}
