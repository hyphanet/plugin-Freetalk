/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Iterator;

/**
 * @author xor
 *
 */
public class FMSBoard implements Iterable<FMSMessage> {

	private final String mName;
	private String mDescription;
	
	public FMSBoard(String newName, String newDescription) {
		mName = newName;
		mDescription = newDescription;
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
		mDescription = newDescription;
	}

	/**
	 * @return The name
	 */
	public String getName() {
		return mName;
	}

	public Iterator<FMSMessage> iterator() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
