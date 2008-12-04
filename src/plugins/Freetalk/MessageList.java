/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import freenet.keys.FreenetURI;


public class MessageList implements Iterable<FreenetURI> {

	private final FreenetURI mURI;
	private final FTIdentity mAuthor;
	private final ArrayList<FreenetURI> mMessages;
	
	public MessageList(FreenetURI myURI, FTIdentity myAuthor, List<FreenetURI> myMessages) {
		mURI = myURI;
		mAuthor = myAuthor;
		
		if(mURI.getRoutingKey().equals(mAuthor.getRequestURI().getRoutingKey()) == false)
			throw new IllegalArgumentException("Trying to create a MessageList of author " + mAuthor.getRequestURI() + " with different routing key: " + mURI);
		
		mMessages = new ArrayList<FreenetURI>(myMessages.size());
		
		for(FreenetURI m : myMessages) {
			if(m.getRoutingKey().equals(myAuthor.getRequestURI().getRoutingKey()) == false)
				throw new IllegalArgumentException("Trying to create a MessageList of author " + myAuthor.getRequestURI() + " with a message having a different routing key: " + m);
			
			mMessages.add(m);
		}
	}

	public Iterator<FreenetURI> iterator() {
		return mMessages.iterator();
	}
}
