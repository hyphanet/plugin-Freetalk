/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.exceptions;

/**
 * Thrown when the connection to WoT is lost while calling a function which accesses it in blocking mode, for example when setting a trust value.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class WoTDisconnectedException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public WoTDisconnectedException() {
		super("The connection to the web of trust plugin was lost.");
	}

}
