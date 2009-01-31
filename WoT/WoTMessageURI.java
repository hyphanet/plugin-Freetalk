package plugins.Freetalk.WoT;

import java.net.MalformedURLException;
import java.util.UUID;

import plugins.Freetalk.MessageURI;
import freenet.keys.FreenetURI;
import freenet.support.Base64;

public final class WoTMessageURI extends MessageURI {

	private final FreenetURI mFreenetURI;
	private final String mMessageID;

	public WoTMessageURI(FreenetURI myFreenetURI, String myMessageID) {
		if(myFreenetURI == null)
			throw new IllegalArgumentException("Trying to create an empty WoTMessageURI");
		
		String keyType = myFreenetURI.getKeyType();
		if(!keyType.equals("USK") || keyType.equals("SSK"))
			throw new IllegalArgumentException("Trying to create a WoTMessageURI with illegal key type " + keyType);
		
		mFreenetURI = myFreenetURI.sskForUSK(); /* Just to make sure */
		mMessageID = myMessageID;
		if(mMessageID.endsWith(Base64.encode(mFreenetURI.getRoutingKey())) == false)
			throw new IllegalArgumentException("Illegal id:" + mMessageID);
	}

	/**
	 * Decodes a WoTMessageURI which was encoded by the toString() method.
	 * @param uri
	 * @throws MalformedURLException The URI does not start with a valid SSK FreenetURI or there is no valid UUID attached after the "#".
	 */
	public WoTMessageURI(String uri) throws MalformedURLException {
		String[] tokens = uri.split("[#]", 1);
		
		mFreenetURI = new FreenetURI(tokens[0]);
		String keyType = mFreenetURI.getKeyType();
		if(!keyType.equals("SSK"))
			throw new IllegalArgumentException("Trying to create a WoTMessageURI with illegal key type " + keyType);
		
		try {
			mMessageID = UUID.fromString(tokens[1]) + "@" + Base64.encode(mFreenetURI.getRoutingKey());
		}
		catch(IllegalArgumentException e) {
			throw new MalformedURLException(e.getMessage());
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
		return mFreenetURI.toString() + "#" + mMessageID.split("[@]", 1)[0];
	}


}
