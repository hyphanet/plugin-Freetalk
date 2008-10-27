/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.WoT;

import java.util.Date;

import freenet.keys.FreenetURI;
import plugins.FMSPlugin.FMSIdentity;

import plugins.WoT.Identity;

/**
 * @author xor
 *
 */
public class FMSIdentityWoT implements FMSIdentity {
	
	protected final Identity mIdentity;

	public FMSIdentityWoT(Identity newIndentity) {
		mIdentity = newIndentity;
	}

	public boolean doesPublishTrustList() {
		return mIdentity.doesPublishTrustList();
	}

	public Date getLastChange() {
		return mIdentity.getLastChange();
	}

	public String getNickName() {
		return mIdentity.getNickName();
	}

	public FreenetURI getRequestURI() {
		return mIdentity.getRequestURI();
	}

}
