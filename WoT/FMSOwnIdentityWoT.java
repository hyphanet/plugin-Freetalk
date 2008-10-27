/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.WoT;

import java.util.Date;

import freenet.keys.FreenetURI;
import plugins.FMSPlugin.FMSOwnIdentity;

/**
 * @author xor
 *
 */
public class FMSOwnIdentityWoT extends FMSOwnIdentity {

	/**
	 * @param newNickname
	 * @param newRequestURI
	 * @param newInsertURI
	 * @param publishTrustList
	 */
	public FMSOwnIdentityWoT(String newNickname, FreenetURI newRequestURI, FreenetURI newInsertURI, boolean publishTrustList) {
		super(newNickname, newRequestURI, newInsertURI, publishTrustList);
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see plugins.FMSPlugin.FMSOwnIdentity#getLastChange()
	 */
	@Override
	public Date getLastChange() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see plugins.FMSPlugin.FMSOwnIdentity#getLastInsert()
	 */
	@Override
	public Date getLastInsert() {
		// TODO Auto-generated method stub
		return null;
	}

}
