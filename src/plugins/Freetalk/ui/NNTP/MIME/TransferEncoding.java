/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP.MIME;

import java.nio.ByteBuffer;

/**
 * Class representing a transfer encoding (a method of representing
 * binary data as an ASCII text stream.)
 */
public abstract class TransferEncoding {

	/**
	 * Set whether decoding should proceed on a best-effort basis if
	 * the input data is invalid.
	 */
	public abstract void setIgnoreErrors(boolean ignore);

	/**
	 * Encode text into this representation.
	 */
	public abstract ByteBuffer encode(ByteBuffer input);

	/**
	 * Decode text from this representation.
	 */
	public abstract ByteBuffer decode(ByteBuffer input)
		throws InvalidEncodedTextException;

	/**
	 * Get a MIMETransferEncoding object for encoding and decoding
	 * message body parts.  (Names are "7bit", "8bit", "binary",
	 * "quoted-printable", "base64".)
	 */
	public static TransferEncoding bodyEncoding(String name) {
		if (name.equalsIgnoreCase("quoted-printable"))
			return new QuotedPrintableEncoding("", false, false);
		else if (name.equalsIgnoreCase("base64"))
			return new Base64Encoding();
		else if (name.equalsIgnoreCase("7bit"))
			return new IdentityEncoding(true, true);
		else if (name.equalsIgnoreCase("8bit"))
			return new IdentityEncoding(true, false);
		else if (name.equalsIgnoreCase("binary"))
			return new IdentityEncoding(false, false);
		else
			throw new IllegalArgumentException("unknown encoding " + name);
	}

	/**
	 * Get a MIMETransferEncoding object for encoding and decoding
	 * mail headers.  Note that semantics of quoted-printable encoding
	 * differ slightly between body text and header encoded-words.  In
	 * particular, encoded-words can have underscore representing
	 * space.
	 */
	public static TransferEncoding headerWordEncoding(String name) {
		if (name.equalsIgnoreCase("Q"))
			return new QuotedPrintableEncoding("?[]()<>@,;:\\.\"", true, false);
		else if (name.equalsIgnoreCase("B"))
			return new Base64Encoding();
		else
			throw new IllegalArgumentException("unknown encoding " + name);
	}

	/**
	 * Convenience function for appending a byte to a ByteBuffer.
	 */
	protected static ByteBuffer appendByte(ByteBuffer buffer, byte b) {
		if (buffer.hasRemaining()) {
			buffer.put(b);
			return buffer;
		}
		else {
			ByteBuffer newbuf = ByteBuffer.allocateDirect(buffer.capacity() * 2);
			buffer.flip();
			newbuf.put(buffer);
			newbuf.put(b);
			return newbuf;
		}
	}

}
