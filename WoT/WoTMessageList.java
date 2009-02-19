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

	public WoTMessageList(FTIdentity myAuthor, FreenetURI myURI) {
		super(myAuthor, myURI);
	}

	public FreenetURI getURI() {
		return assembleURI(mAuthor.getRequestURI(), mIndex).sskForUSK();
	}
	
	protected FreenetURI generateURI(FreenetURI baseURI, int index) {
		return assembleURI(baseURI, index);
	}
	
	/**
	 * Get the USK URI of a message list with the given identity and index.
	 * @param identity
	 * @param index
	 * @return
	 */
	public static FreenetURI generateURI(WoTIdentity identity, int index) {
		return assembleURI(identity.getRequestURI(), index);
	}
	
	public static FreenetURI assembleURI(FreenetURI baseURI, int index) {
		baseURI = baseURI.setKeyType("USK");
		baseURI = baseURI.setDocName(Freetalk.PLUGIN_TITLE + "|" + "MessageList");
		baseURI = baseURI.setSuggestedEdition(index);
		baseURI = baseURI.setMetaString(null);
		return baseURI;
	}

}
