/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import plugins.Freetalk.WoT.WoTMessageURI;
import freenet.keys.FreenetURI;


/**
 * A message URI is a fully qualified reference to a message.
 * 
 * Typically (= in {@link WoTMessageURI}), it consists of the URI of the message list where the message was referenced (which contains the real URI of the message) 
 * and the ID of the message.
 * 
 * @author xor (xor@freenetproject.org)
 */
//@IndexedClass // I can't think of any query which would need to get all MessageURI objects.
public abstract class MessageURI extends Persistent implements Cloneable {

	/**
	 * Get the FreenetURI of the container which stores the message. Typically it is the URI of a message list, therefore it is not a fully qualified 
	 * reference for the message. For obtaining a fully qualified reference to the message, use {@link toString}.
	 */
	public abstract FreenetURI getFreenetURI();
	
	/**
	 * Get the ID of the message, it is globally unique for all messages - this is ensured by the fact that message IDs contain the routing keys of their author.
	 */
	public abstract String getMessageID();
	
	public abstract void throwIfAuthorDoesNotMatch(Identity newAuthor);

	@Override
	public abstract boolean equals(Object obj);

	
	/**
	 * Get a fully qualified reference to the message, containing all necessary information to create a MessageURI object from it.
	 */
	@Override
	public abstract String toString();
	
	@Override
	public abstract MessageURI clone();
	
	// TODO: Add a fromString() instead of the constructor in WoTMessageURI
	
}
