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
		return assembleURI(mAuthor.getRequestURI(), mIndex);
	}
	
	protected FreenetURI generateURI(FreenetURI baseURI, int index) {
		return assembleURI(baseURI, index);
	}
	
	public static FreenetURI generateURI(WoTIdentity identity, int index) {
		return assembleURI(identity.getRequestURI(), index);
	}
	
	public static FreenetURI assembleURI(FreenetURI baseURI, int index) {
		baseURI = baseURI.setKeyType("USK");
		baseURI = baseURI.setDocName(Freetalk.PLUGIN_TITLE + "|" + "MessageList");
		baseURI = baseURI.setSuggestedEdition(index);
		baseURI = baseURI.setMetaString(new String[] {"messages.xml"});
		return baseURI;
	}

}
