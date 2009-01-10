package plugins.Freetalk.WoT;

import freenet.keys.FreenetURI;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.OwnMessageList;

public final class WoTOwnMessageList extends OwnMessageList {

	public WoTOwnMessageList(FTOwnIdentity newAuthor, int newIndex) {
		super(newAuthor, newIndex);
		// TODO Auto-generated constructor stub
	}

	/* Attention: This code is duplicated in WoTMessageList */
	protected FreenetURI generateURI(FreenetURI baseURI) {
		baseURI = baseURI.setKeyType("USK");
		baseURI = baseURI.setDocName(Freetalk.PLUGIN_TITLE + "|" + "MessageList");
		baseURI = baseURI.setSuggestedEdition(mIndex);
		baseURI = baseURI.setMetaString(new String[] {"messages.xml"});
		return baseURI;
	}

}
