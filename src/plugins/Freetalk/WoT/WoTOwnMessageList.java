package plugins.Freetalk.WoT;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.OwnMessageList;
import freenet.keys.FreenetURI;

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
			int freeIndex = ((WoTMessageManager)mMessageManager).getFreeOwnMessageListIndex(getAuthor());
			mIndex = Math.max(mIndex+1, freeIndex);
			mID = calculateID();
			storeWithoutCommit();
	}
	
	public WoTOwnIdentity getAuthor() {
		return (WoTOwnIdentity)mAuthor;
	}

	/**
	 * Returns true if the XML of this message list fits into a single SSK block.
	 */
	protected boolean fitsIntoContainer() {
		/* FIXME: Implement. */
		return true;
	}

}
