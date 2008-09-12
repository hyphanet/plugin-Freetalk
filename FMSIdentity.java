/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

public class FMSIdentity {
	
	private final String nickName;
	private final String requestUri;
	
	public FMSIdentity(String nickname, String requesturi) {
		nickName = nickname;
		requestUri = requesturi;
	}
	
	public String getNickName() {
		return nickName;
	}

	public String getRequestURI() {
		return requestUri;
	}
}
