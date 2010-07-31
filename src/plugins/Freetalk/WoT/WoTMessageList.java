package plugins.Freetalk.WoT;

import java.util.List;

import plugins.Freetalk.Identity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import freenet.keys.FreenetURI;

//@IndexedField // I can't think of any query which would need to get all WoTMessageList objects.
public final class WoTMessageList extends MessageList {

	public WoTMessageList(Identity myAuthor, FreenetURI myURI, List<MessageReference> newMessages) throws InvalidParameterException,
			NoSuchIdentityException {
		super(myAuthor, myURI, newMessages);
		// TODO Auto-generated constructor stub
	}

	public WoTMessageList(Identity myAuthor, FreenetURI myURI) {
		super(myAuthor, myURI);
	}
	
	
	public WoTIdentity getAuthor() {
		WoTIdentity author = (WoTIdentity)mAuthor;
		author.initializeTransient(mFreetalk);
		return author;
	}

	public FreenetURI getURI() {
		return assembleURI(getAuthor().getRequestURI(), mIndex).sskForUSK();
	}
	
	protected FreenetURI generateURI(FreenetURI baseURI, long index) {
		return assembleURI(baseURI, index);
	}
	
	/**
	 * Get the USK URI of a message list with the given identity and index.
	 * @param identity
	 * @param index
	 * @return
	 */
	public static FreenetURI generateURI(WoTIdentity identity, long index) {
		return assembleURI(identity.getRequestURI(), index);
	}
	
	public static FreenetURI assembleURI(FreenetURI baseURI, long index) {
		baseURI = baseURI.setKeyType("USK");
		baseURI = baseURI.setDocName(Freetalk.PLUGIN_TITLE + "|" + "MessageList");
		baseURI = baseURI.setSuggestedEdition(index);
		baseURI = baseURI.setMetaString(null);
		return baseURI;
	}

}
