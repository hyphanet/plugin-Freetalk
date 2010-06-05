/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.keys.FreenetURI;

public interface Identity {
	
	public String getID();
	
	/**
	 * @return The requestURI ({@link FreenetURI}) to fetch this Identity 
	 */
	public FreenetURI getRequestURI();
	
	public String getNickname();

	public String getShortestUniqueName();
	
	public String getFreetalkAddress();
	
}
