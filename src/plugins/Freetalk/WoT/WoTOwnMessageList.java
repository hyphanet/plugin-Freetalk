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


}
