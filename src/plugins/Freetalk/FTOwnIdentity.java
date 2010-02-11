/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.keys.FreenetURI;

/**
 * @author saces, xor
 *
 */
public interface FTOwnIdentity extends FTIdentity { // FIXME: Change to abstract class so we can extend Persistent.
	
	public FreenetURI getInsertURI();
	
    /**
     * Checks whether this Identity auto-subscribes to boards subscribed in NNTP client.
     * 
     * @return Whether this Identity auto-subscribes to boards subscribed in NNTP client or not.
     */
    public boolean nntpAutoSubscribeBoards();
    
    /**
     * Sets if this Identity auto-subscribes to boards subscribed in NNTP client. 
     */
    public void setNntpAutoSubscribeBoards(boolean nntpAutoSubscribeBoards);

	
	/**
	 * @throws Exception If the decision cannot be made right now. Practically this means that the connection to the WoT plugin is not working right now.
	 */
	public boolean wantsMessagesFrom(FTIdentity identity) throws Exception;
}
