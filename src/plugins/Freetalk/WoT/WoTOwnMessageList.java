package plugins.Freetalk.WoT;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.OwnMessageList;
import freenet.keys.FreenetURI;

//@Indexed // I can't think of any query which would need to get all WoTOwnMessageList objects.
public final class WoTOwnMessageList extends OwnMessageList {

	public WoTOwnMessageList(FTOwnIdentity newAuthor, int newIndex) {
		super(newAuthor, newIndex);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected FreenetURI generateURI(FreenetURI baseURI, int index) {
		return WoTMessageList.assembleURI(baseURI, index);
	}

	/**
	 * Only to be used by the WoTMessageManager, which provides the necessary synchronization.
	 */
	protected synchronized void incrementInsertIndex() {
		int freeIndex = mFreetalk.getMessageManager().getFreeOwnMessageListIndex(getAuthor());
		mIndex = Math.max(mIndex+1, freeIndex);
		mID = calculateID();
		storeWithoutCommit();
	}
	
	public WoTOwnIdentity getAuthor() {
		return (WoTOwnIdentity)super.getAuthor();
	}

	/**
	 * Returns true if the XML of this message list fits into a single SSK block.
	 */
	protected boolean fitsIntoContainer() {
		if(!super.fitsIntoContainer())
			return false;
	
		if(getMessageCount() > 5)
			return false;
		
		// TODO: Implement a real fitsIntoContainer which compresses the XML and checks the size. (Bug 4041) 
		return true;
	}

}
