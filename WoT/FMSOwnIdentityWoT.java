/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.WoT;

import java.util.Date;

import freenet.keys.FreenetURI;
import plugins.FMSPlugin.FMSOwnIdentity;
import plugins.WoT.Identity;
import plugins.WoT.OwnIdentity;

/**
 * @author xor
 *
 */
public class FMSOwnIdentityWoT extends FMSIdentityWoT implements FMSOwnIdentity {

	public FMSOwnIdentityWoT(OwnIdentity newIndentity) {
		super(newIndentity);
	}
	
	protected OwnIdentity getOwnIdentity() {
		return (OwnIdentity)mIdentity;
	}

	public FreenetURI getInsertURI() {
		return getOwnIdentity().getInsertURI();
	}

	public Date getLastInsert() {
		return getOwnIdentity().getLastInsert();
	}
}
