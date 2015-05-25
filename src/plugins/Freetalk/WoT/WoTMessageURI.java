/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.net.MalformedURLException;
import java.util.StringTokenizer;
import java.util.UUID;

import plugins.Freetalk.Identity;
import plugins.Freetalk.Identity.IdentityID;
import plugins.Freetalk.Message.MessageID;
import plugins.Freetalk.MessageURI;
import freenet.keys.FreenetURI;
import freenet.support.codeshortification.IfNotEquals;
import freenet.support.codeshortification.IfNull;


/**
 * The WoT-based implementation of a message URI:
 * 
 * The raw messages are inserted as CHK. Therefore, they cannot be associated with an author, and because CHK-URIs are dependent on the content, they cannot be
 * guessed by someone who wants to download messages of a given author.
 * 
 * An author is a SSK URI.  Each CHK URI of a message therefore must be referenced by a message list of the author - message lists are stored under the SSK URI of 
 * the author so the URIs of message lists can be guessed for downloading them.
 * 
 * A WoTMessageURI is a fully qualified reference to a message: It stores the SSK URI of the message list which contains the CHK URI of the message, and the ID 
 * of the message so you know which CHK URI in the given message list is being referenced. 
 * 
 * @author xor (xor@freenetproject.org)
 */
// @IndexedField // I can't think of any query which would need to get all WoTMessageURI objects.
public final class WoTMessageURI extends MessageURI implements Cloneable {

	private final FreenetURI mFreenetURI;
	private final String mMessageID;

	public WoTMessageURI(FreenetURI myFreenetURI, MessageID myMessageID) {
		if(myFreenetURI == null)
			throw new IllegalArgumentException("Trying to create a WoTMessageURI without a FreenetURI.");
		
		if(myMessageID == null)
			throw new IllegalArgumentException("Trying to create a WoTMessageURI without an ID.");
			
		
		mFreenetURI = myFreenetURI.isUSK() ? myFreenetURI.sskForUSK() : myFreenetURI.clone();
		if(!mFreenetURI.isSSK())
			throw new IllegalArgumentException("Trying to create a WoTMessageURI with illegal key type: " + myFreenetURI.getKeyType());
		
		myMessageID.throwIfAuthorDoesNotMatch(IdentityID.constructFromURI(myFreenetURI));
		 		
		mMessageID = myMessageID.toString();
	}

	/**
	 * Decodes a WoTMessageURI which was encoded by the toString() method.
	 * @param uri
	 * @throws MalformedURLException The URI does not start with a valid SSK FreenetURI or there is no valid UUID attached after the "#".
	 */
	public WoTMessageURI(String uri) throws MalformedURLException {
		if(uri == null)
			throw new IllegalArgumentException("Trying to create an empty WoTMessageURI");
		
		StringTokenizer tokenizer = new StringTokenizer(uri, "#");
		
		if(tokenizer.countTokens() < 2)
			throw new MalformedURLException("Invalid Message URI: Message list specified but no UUID given: " + uri);
		
		FreenetURI tempURI = new FreenetURI(tokenizer.nextToken());
		mFreenetURI = tempURI.isUSK() ? tempURI.sskForUSK() : tempURI;
		if(!mFreenetURI.isSSK()) /* TODO: USK is only allowed for legacy because there are broken message lists in the network. Remove */
			throw new MalformedURLException("Trying to create a WoTMessageURI with illegal key type " + mFreenetURI.getKeyType());
		
		String stringUUID = tokenizer.nextToken();
		UUID uuid;
		
		try {
			uuid = UUID.fromString(stringUUID);
		}
		catch(IllegalArgumentException e) {
			throw new MalformedURLException("Invalid UUID: " + stringUUID);
		}
		
		mMessageID = MessageID.construct(uuid, mFreenetURI).toString();
	}
	
	@Override
	public void databaseIntegrityTest() throws Exception {
		checkedActivate(1);
		
		IfNull.thenThrow(mFreenetURI, "mFreenetURI");
		IfNull.thenThrow(mMessageID, "mMessageID");
		
		checkedActivate(mFreenetURI, 2);
		
		if(!mFreenetURI.isSSK())
			throw new IllegalStateException("mFreenetURI == " + mFreenetURI);
		
		IfNotEquals.thenThrow(MessageID.construct(mMessageID).getAuthorID(), IdentityID.constructFromURI(mFreenetURI), "author ID from mMessageID");
	}

	@Override
	public FreenetURI getFreenetURI() {
		checkedActivate(1);
		checkedActivate(mFreenetURI, 2);
		return mFreenetURI;
	}
	
	@Override
	public String getMessageID() {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		return mMessageID;
	}
	
	@Override
	public void throwIfAuthorDoesNotMatch(Identity newAuthor) {
		checkedActivate(1); // String is a db4o primitive type so 1 is enough
		MessageID.construct(mMessageID).throwIfAuthorDoesNotMatch(newAuthor);
	}
	

	@Override
	public boolean equals(Object obj) {
		final WoTMessageURI uri = (WoTMessageURI)obj;
		checkedActivate(1);
		checkedActivate(mFreenetURI, 2);
		return uri.getFreenetURI().equals(mFreenetURI) && uri.getMessageID().equals(mMessageID);
	}

	/**
	 * Returns the SSK URI of the message list in which the message appeared + "#" + the UUID part of the message ID (the part before the "@").
	 * Message IDs are constructed as: Random UUID + "@" + Base64 encoded author SSK routing key.
	 * Therefore, we only need to include the random UUID in the String-representation of the WoTMessageURI because the routing key is already
	 * contained in the SSK part and can be obtained from there when decoding the String. 
	 */
	@Override
	public String toString() {
		checkedActivate(1);
		checkedActivate(mFreenetURI, 2);
		return mFreenetURI.toString() + "#" + MessageID.construct(mMessageID).getUUID();
	}

	@Override
	protected void deleteWithoutCommit() {
		try {
			checkedActivate(1);
			
			checkedDelete();
			
			checkedActivate(mFreenetURI, 2);
			mDB.delete(mFreenetURI);
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	@Override
	protected void storeWithoutCommit() {
		try {
			checkedActivate(1);
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in removeFrom() !
			
			checkedActivate(mFreenetURI, 2);
			checkedStore(mFreenetURI);
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	@Override
	public WoTMessageURI clone() {
		checkedActivate(1);
		checkedActivate(mFreenetURI, 2);
		final WoTMessageURI clone = new WoTMessageURI(mFreenetURI, MessageID.construct(mMessageID));
		clone.initializeTransient(mFreetalk);
		return clone;
	}
	
}
