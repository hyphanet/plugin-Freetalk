package plugins.Freetalk.WoT;

import java.util.List;

import freenet.keys.FreenetURI;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;

public class WoTMessageList extends MessageList {
	
	public WoTMessageList(FTIdentity newAuthor, int newIndex, List<FreenetURI> newMessages) {
		super(newAuthor, newIndex, newMessages);
		// TODO Auto-generated constructor stub
	}

	public FreenetURI getURI() {
		return generateURI(mAuthor.getRequestURI());
	}
	
	/* Attention: This code is duplicated in WoTOwnMessageList */
	protected FreenetURI generateURI(FreenetURI baseURI) {
		baseURI = baseURI.setKeyType("USK");
		baseURI = baseURI.setDocName(Freetalk.PLUGIN_TITLE + "|" + "MessageList" + "-" + mIndex + ".xml");
		baseURI = baseURI.setMetaString(null);
		return baseURI;
	}
}
