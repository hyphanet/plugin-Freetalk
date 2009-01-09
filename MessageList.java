/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;

public class MessageList implements Iterable<FreenetURI> {
	
	protected final FTIdentity mAuthor;
	
	protected final int mIndex;
	
	protected final ArrayList<FreenetURI> mMessages;
	
	public MessageList(FTIdentity newAuthor, int newIndex, List<FreenetURI> newMessages) {
		if(newAuthor == null)
			throw new IllegalArgumentException("Trying to construct a message list with no author.");
		
		if(newIndex < 0)
			throw new IllegalArgumentException("Trying to construct a message list with invalid index " + newIndex);
		
		if(newMessages == null || newMessages.size() < 1)
			throw new IllegalArgumentException("Trying to construct a message list with no messages.");
	
		mAuthor = newAuthor;
		mIndex = newIndex;
		mMessages = new ArrayList<FreenetURI>(newMessages);
	}
	
	protected transient ObjectContainer db;

	public void initializeTransient(ObjectContainer myDB) {
		db = myDB;
	}
	
	public void store() {
		db.store(this);
		db.commit();
	}
	
	public FTIdentity getAuthor() {
		return mAuthor;
	}
	
	public int getIndex() {
		return mIndex;
	}
	
	public FreenetURI getURI() {
		return generateURI(mAuthor.getRequestURI());
	}
	
	protected FreenetURI generateURI(FreenetURI baseURI) {
		baseURI = baseURI.setKeyType("USK");
		baseURI = baseURI.setDocName(Freetalk.PLUGIN_TITLE + "|" + "MessageList" + "-" + mIndex + ".xml");
		baseURI = baseURI.setMetaString(null);
		return baseURI;
	}
	
	public Iterator<FreenetURI> iterator() {
		return mMessages.iterator();
	}
	
}
