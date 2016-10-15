/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.List;

import plugins.Freetalk.Identity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import freenet.keys.FreenetURI;

/**
 * 
 * @author xor (xor@freenetproject.org)
 */
//@IndexedField // I can't think of any query which would need to get all WoTMessageList objects.
public final class WoTMessageList extends MessageList {

	public WoTMessageList(Freetalk myFreetalk, Identity myAuthor, FreenetURI myURI, List<MessageReference> newMessages) throws InvalidParameterException,
			NoSuchIdentityException {
		super(myFreetalk, myAuthor, myURI, newMessages);
		// TODO Auto-generated constructor stub
	}

	public WoTMessageList(Freetalk myFreetalk, Identity myAuthor, FreenetURI myURI) {
		super(myFreetalk, myAuthor, myURI);
	}

	@Override public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		if(!(getAuthor() instanceof WoTIdentity))
			throw new IllegalStateException("mAuthor == " + getAuthor());
	}

	@Override public WoTIdentity getAuthor() {
		checkedActivate(1);
		final WoTIdentity author = (WoTIdentity)mAuthor;
		author.initializeTransient(mFreetalk);
		return author;
	}

	@Override public FreenetURI getURI() {
		return assembleURI(getAuthor().getRequestURI(), getIndex()).sskForUSK();
	}

	@Override protected FreenetURI generateURI(FreenetURI baseURI, long index) {
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
