/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import plugins.Freetalk.MessageList;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.OwnMessageList;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import freenet.keys.FreenetURI;

//@IndexedField // I can't think of any query which would need to get all WoTOwnMessageList objects.
public final class WoTOwnMessageList extends OwnMessageList {

	public WoTOwnMessageList(OwnIdentity newAuthor, long newIndex) {
		super(newAuthor, newIndex);
		// TODO Auto-generated constructor stub
	}

	@Override public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		if(!(getAuthor() instanceof WoTIdentity))
			throw new IllegalStateException("mAuthor == " + getAuthor());
	}

	@Override
	protected FreenetURI generateURI(FreenetURI baseURI, long index) {
		return WoTMessageList.assembleURI(baseURI, index);
	}

	/**
	 * Only to be used by the WoTMessageManager, which provides the necessary synchronization.
	 */
	protected synchronized void incrementInsertIndex() {
		long freeIndex = mFreetalk.getMessageManager().getFreeOwnMessageListIndex(getAuthor());
		
		checkedActivate(1);
		
		mIndex = Math.max(mIndex+1, freeIndex);
		mID = MessageListID.construct(getAuthor(), mIndex).toString();
		
		// TODO: Optimization: This is debug code which was added on 2011-02-13 for preventing DuplicateMessageListException, it can be removed after some months if they do not happen.
		try {
			final MessageList existingList = mFreetalk.getMessageManager().getOwnMessageList(mID);
			throw new RuntimeException("getFreeOwnMessageListIndex reported non-free index, taken by: " + existingList);
		} catch(NoSuchMessageListException e) {}
		
		storeWithoutCommit();
	}

	@Override public WoTOwnIdentity getAuthor() {
		return (WoTOwnIdentity)super.getAuthor();
	}

	/**
	 * Returns true if the XML of this message list fits into a single SSK block.
	 */
	@Override protected boolean fitsIntoContainer() {
		if(!super.fitsIntoContainer())
			return false;
	
		if(getMessageCount() > 5)
			return false;
		
		// TODO: Implement a real fitsIntoContainer which compresses the XML and checks the size. (Bug 4041) 
		return true;
	}

}
