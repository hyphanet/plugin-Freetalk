package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.db4o.ObjectContainer;

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
