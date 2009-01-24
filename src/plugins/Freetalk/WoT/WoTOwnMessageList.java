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

	public synchronized void incrementInsertIndex() {
		synchronized(WoTOwnMessageList.class) {
			int freeIndex = ((WoTMessageManager)mMessageManager).getFreeOwnMessageListIndex((WoTOwnIdentity)mAuthor);
			mIndex = Math.max(mIndex+1, freeIndex);
			mID = calculateID();
			store();
		}
	}

}
