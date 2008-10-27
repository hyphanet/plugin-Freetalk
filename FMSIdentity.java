/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Date;

import freenet.keys.FreenetURI;

public interface FMSIdentity {

	/**
	 * @return The requestURI ({@link FreenetURI}) to fetch this Identity 
	 */
	public FreenetURI getRequestURI();
	
	public String getNickName();
	
	public Date getLastChange();
	
	/**
	 * @return Whether this Identity publishes its trustList or not
	 */
	public boolean doesPublishTrustList();
}
