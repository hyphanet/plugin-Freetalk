/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.net.MalformedURLException;
import java.util.UUID;

import plugins.Freetalk.DBUtil;
import plugins.Freetalk.MessageURI;

import com.db4o.ext.ExtObjectContainer;

import freenet.keys.FreenetURI;
import freenet.support.Base64;


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
public final class WoTMessageURI extends MessageURI {

	private final FreenetURI mFreenetURI;
	private final String mMessageID;

	public WoTMessageURI(FreenetURI myFreenetURI, String myMessageID) {
		if(myFreenetURI == null)
			throw new IllegalArgumentException("Trying to create a WoTMessageURI without a FreenetURI.");
		
		if(myMessageID == null)
			throw new IllegalArgumentException("Trying to create a WoTMessageURI without an ID.");
			
		
		mFreenetURI = myFreenetURI.isUSK() ? myFreenetURI.sskForUSK() : myFreenetURI;
		if(!mFreenetURI.isSSK())
			throw new IllegalArgumentException("Trying to create a WoTMessageURI with illegal key type: " + myFreenetURI.getKeyType());
		 		
		mMessageID = myMessageID;
		String[] tokens = mMessageID.split("[@]", 2);
		
		try {
			UUID.fromString(tokens[0]);
		}
		catch(IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid UUID in message ID:" + mMessageID);
		}
		
		if(tokens[1].equals(Base64.encode(mFreenetURI.getRoutingKey())) == false)
			throw new IllegalArgumentException("ID does not match URI: " + mMessageID);
	}

	/**
	 * Decodes a WoTMessageURI which was encoded by the toString() method.
	 * @param uri
	 * @throws MalformedURLException The URI does not start with a valid SSK FreenetURI or there is no valid UUID attached after the "#".
	 */
	public WoTMessageURI(String uri) throws MalformedURLException {
		if(uri == null)
			throw new IllegalArgumentException("Trying to create an empty WoTMessageURI");
		
		String[] tokens = uri.split("[#]", 2);
		
		if(tokens.length < 2)
			throw new MalformedURLException("Invalid Message URI: Message list specified but no UUID given: " + uri);
		
		FreenetURI tempURI = new FreenetURI(tokens[0]);
		mFreenetURI = tempURI.isUSK() ? tempURI.sskForUSK() : tempURI;
		if(!mFreenetURI.isSSK()) /* FIXME: USK is only allowed for legacy because there are broken message lists in the network. Remove */
			throw new MalformedURLException("Trying to create a WoTMessageURI with illegal key type " + mFreenetURI.getKeyType());
		
		try {
			mMessageID = UUID.fromString(tokens[1]) + "@" + Base64.encode(mFreenetURI.getRoutingKey());
		}
		catch(IllegalArgumentException e) {
			throw new MalformedURLException("Invalid UUID: " + tokens[1]);
		}
	}

	@Override
	public FreenetURI getFreenetURI() {
		return mFreenetURI;
	}
	
	@Override
	public String getMessageID() {
		return mMessageID;
	}

	@Override
	public boolean equals(Object obj) {
		WoTMessageURI uri = (WoTMessageURI)obj;
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
		return mFreenetURI.toString() + "#" + mMessageID.split("[@]", 2)[0];
	}

	@Override
	public void removeFrom(ExtObjectContainer db) {
		try {
			DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
			
			DBUtil.checkedDelete(db, this);
			
			mFreenetURI.removeFrom(db);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(db, this, e);
		}
	}

	@Override
	public void storeWithoutCommit(ExtObjectContainer db) {
		try {
			DBUtil.checkedActivate(db, this, 3); // TODO: Figure out a suitable depth.
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in removeFrom() !
			
			db.store(mFreenetURI);
			db.store(this);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(db, this, e);
		}
	}

}
