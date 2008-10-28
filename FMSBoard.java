/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Iterator;

/**
 * @author xor
 *
 */
public class FMSBoard {

	private final FMSMessageManager mMessageManager;
	private final String mName;
	private String mDescription;
	
	public FMSBoard(FMSMessageManager newMessageManager, String newName, String newDescription) {
		if(newName==null || newName.isEmpty())
			throw new IllegalArgumentException("Empty board name.");
		
		assert(newMessageManager != null);
		mMessageManager = newMessageManager;
		// FIXME: Remove anything dangerous from name and description.
		mName = newName;
		setDescription(newDescription);
	}

	/**
	 * @return the Description
	 */
	public String getDescription() {
		return mDescription;
	}
	
	/**
	 * @param description The description to set.
	 */
	public void setDescription(String newDescription) {
		//FIXME: Remove anything dangerous from description.
		mDescription = newDescription!=null ? newDescription : "";
	}

	/**
	 * @return The name
	 */
	public String getName() {
		return mName;
	}

	/**
	 * Get all messages in the board. The view is specified to the FMSOwnIdentity displaying it, therefore you have to pass one as parameter.
	 * @param identity The identity viewing the board.
	 * @return An iterator of the message which the identity will see (based on its trust levels).
	 */
	public Iterator<FMSMessage> messageIterator(FMSOwnIdentity identity) {
		return mMessageManager.messageIterator(identity);
	}
	
}
