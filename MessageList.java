/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;
import freenet.support.Base64;

public abstract class MessageList implements Iterable<MessageList.MessageReference> {
	
	protected final String mID;
	
	protected final FTIdentity mAuthor;
	
	protected final int mIndex;
	
	public class MessageReference {
		
		private final FreenetURI mURI;
		
		private boolean iWasDownloaded = false;
		
		public MessageReference(FreenetURI newURI) {
			mURI = newURI;
		}
		
		public FreenetURI getURI() {
			return mURI;
		}
		
		public synchronized boolean wasDownloaded() {
			return iWasDownloaded;
		}
		
		public synchronized void markAsDownloaded() {
			synchronized(MessageList.this) {
				assert(iWasDownloaded == false);
				if(!iWasDownloaded)
					--mNumberOfNotDownloadedMessages;
				iWasDownloaded = true;
			}
		}
		
	}
	
	protected final ArrayList<MessageReference> mMessages;
	
	protected int mNumberOfNotDownloadedMessages;
	
	public MessageList(FTIdentity newAuthor, int newIndex, List<FreenetURI> newMessages) {
		if(newAuthor == null)
			throw new IllegalArgumentException("Trying to construct a message list with no author.");
		
		if(newIndex < 0)
			throw new IllegalArgumentException("Trying to construct a message list with invalid index " + newIndex);
		
		if(newMessages == null || newMessages.size() < 1)
			throw new IllegalArgumentException("Trying to construct a message list with no messages.");
	
		mAuthor = newAuthor;
		mIndex = newIndex;
		mID = calculateID();
		mNumberOfNotDownloadedMessages = newMessages.size();
		mMessages = new ArrayList<MessageReference>(mNumberOfNotDownloadedMessages);
		for(FreenetURI u : newMessages) {
			mMessages.add(new MessageReference(u));
		}
	}
	
	protected transient ObjectContainer db;

	public void initializeTransient(ObjectContainer myDB) {
		db = myDB;
	}
	
	public void store() {
		db.store(this);
		db.commit();
	}
	
	protected String calculateID() {
		return calculateID(mAuthor, mIndex);
	}
	
	public static String calculateID(FTIdentity author, int index) {
		return index + "@" + Base64.encodeStandard(author.getRequestURI().getRoutingKey());
	}
	
	public String getID() {
		return mID;
	}
	
	public FreenetURI getURI() {
		return generateURI(mAuthor.getRequestURI());
	}
	
	protected abstract FreenetURI generateURI(FreenetURI baseURI);
	
	public FTIdentity getAuthor() {
		return mAuthor;
	}
	
	public int getIndex() {
		return mIndex;
	}
	
	/**
	 * You have to synchronize on the <code>MessageList</code> when using this method.
	 */
	public Iterator<MessageReference> iterator() {
		return mMessages.iterator();
	}
	
	public synchronized void markAsDownloaded(FreenetURI uri) {
		/* TODO: Figure out whether MessageLists are usually large enough so that we can gain speed by using a Hashtable instead of ArrayList */
		for(MessageReference ref : mMessages) {
			if(ref.getURI().equals(uri)) {
				ref.markAsDownloaded();
				return;
			}
		}
	}
	
}
