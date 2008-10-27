/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import freenet.keys.FreenetURI;

public abstract class FMSIdentity {
	
	private final String mNickname;
	private final FreenetURI mRequestURI;
	
	public FMSIdentity(String newNickname, FreenetURI newRequestURI) {
		mNickname = newNickname;
		mRequestURI = newRequestURI;
	}
	
	public String getNickName() {
		return mNickname;
	}

	public FreenetURI getRequestURI() {
		return mRequestURI;
	}
}
