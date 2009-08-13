/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.exceptions;

import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTOwnIdentity;

/**
 * Thrown when querying the {@link Score} of a target {@link Identity} in the trust tree of a tree owner {@link Identity} shows that there is no {@link Score} for
 * the target in the tree owner's trust tree.  
 * 
 * @author Julien Cornuwel (batosai@freenetproject.org)
 *
 */
public class NotInTrustTreeException extends Exception {
	
	private static final long serialVersionUID = -1;

	public NotInTrustTreeException(WoTOwnIdentity treeOwner, WoTIdentity target) {
		super(target + " is not in the trust treee of " + treeOwner);
	}

}
