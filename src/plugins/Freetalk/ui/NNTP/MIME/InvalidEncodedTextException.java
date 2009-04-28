/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP.MIME;

import java.io.IOException;

/**
 * Exception indicating that encoded text contains invalid data and
 * cannot be decoded.
 */
public class InvalidEncodedTextException extends IOException {

	private static final long serialVersionUID = 1L;

	public InvalidEncodedTextException(String desc) {
		super(desc);
	}
}
