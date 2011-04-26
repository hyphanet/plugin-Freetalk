/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.exceptions;

@SuppressWarnings("serial")
public final class NoSuchWantedStateException extends NoSuchObjectException {

	public NoSuchWantedStateException(String message) {
		super(message);
	}

}
