/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP.MIME;

import java.nio.ByteBuffer;

/**
 * The identity encoding.
 */
public class IdentityEncoding extends TransferEncoding {

	//private final boolean requireShortLines;
	//private final boolean require7Bit;

	public IdentityEncoding(boolean requireShortLines, boolean require7Bit) {
		//this.requireShortLines = requireShortLines;
		//this.require7Bit = require7Bit;
	}

	@Override public void setIgnoreErrors(boolean ignore) {

	}

	@Override public ByteBuffer encode(ByteBuffer input) {
		// FIXME: maybe we should validate the input here?
		return input.slice();
	}

	@Override public ByteBuffer decode(ByteBuffer input) {
		return input.slice();
	}
}
