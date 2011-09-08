/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.keys.FreenetURI;

/**
 * @author xor (xor@freenetproject.org)
 * @author saces
 */
public interface OwnIdentity extends Identity {
	
	public FreenetURI getInsertURI();
	
	/**
	 * Checks whether this identity should be auto-subscribed to new boards as they are discovered.
	 */
	public boolean wantsAutoSubscribeToNewBoards();
	
	/**
	 * Sets whether this identity should be auto-subscribed to new boards when they are discovered.
	 */
	public void setAutoSubscribeToNewboards(boolean autoSubscribeToNewBoards);
	
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
	 * Checks whether this identity wants to allow the displaying of image which other users uploaded
	 */
	public boolean wantsImageDisplay();
	
	/**
	 * Sets whether this identity wants to allow the displaying of image which other users uploaded
	 */
	public void setWantsImageDisplay(boolean wantsImageDisplay);
	
}
