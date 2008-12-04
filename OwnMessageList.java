/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.List;

import com.db4o.ObjectContainer;

public class OwnMessageList {
	
	private final FTOwnIdentity mAuthor;
	private final List<OwnMessage> mMessages = new ArrayList<OwnMessage>();
	
	private boolean iAmBeingInserted = false;
	private boolean iWasInserted = false;
	
	private transient ObjectContainer db;

	public OwnMessageList(FTOwnIdentity myAuthor) {
		assert(myAuthor != null);
		
		mAuthor = myAuthor;
	}
	
	public void initializeTransient(ObjectContainer myDB) {
		db = myDB;
	}
	
	public void store() {
		db.store(this);
		db.commit();
	}
	
	public synchronized void addMessage(OwnMessage message) {
		if(iAmBeingInserted || iWasInserted)
			throw new IllegalArgumentException("Trying to add a message to an already inserted messagelist.");
		
		mMessages.add(message);
	}
	
	/**
	 * Has to be called before the insertion of a MessageList begins. This prevents the rest of the code from appending messages to the list
	 * while it is already being inserted.
	 */
	public synchronized void beginInsert() {
		if(iAmBeingInserted)
			throw new IllegalArgumentException("Trying to begin insertion of a messagelist which is already being inserted.");
		
		iAmBeingInserted = true;
	}
	
	/**
	 * Has to be called when a MessageList was successfully inserted.
	 */
	public synchronized void finishInsert() {
		iWasInserted = true;
	}

}
