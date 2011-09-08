/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.exceptions;

import plugins.Freetalk.WoT.WoTIdentity;

/**
 * Thrown when querying the Trust from an truster to a trustee shows that the truster does not have a {@link Trust} towards the trustee.
 * 
 * @author xor (xor@freenetproject.org)
 * @author Julien Cornuwel (batosai@freenetproject.org)
 */
public final class NotTrustedException extends NoSuchObjectException {
	
	private static final long serialVersionUID = -1;

	public NotTrustedException(WoTIdentity truster, WoTIdentity trustee) {
		super(truster.getNickname() + " does not trust " + trustee.getNickname());
	}
	
	public NotTrustedException(String trusterID, String trusteeID) {
		super(trusterID + " does not trust " + trusteeID);
	}
}
