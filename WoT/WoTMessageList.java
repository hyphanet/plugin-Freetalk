package plugins.Freetalk.WoT;

import java.util.List;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import freenet.keys.FreenetURI;

public class WoTMessageList extends MessageList {

	public WoTMessageList(FTIdentity myAuthor, FreenetURI myURI, List<MessageReference> newMessages) throws InvalidParameterException,
			NoSuchIdentityException {
		super(myAuthor, myURI, newMessages);
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
