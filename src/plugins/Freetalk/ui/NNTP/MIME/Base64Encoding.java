/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP.MIME;

import java.nio.ByteBuffer;

/**
 * The MIME Base64 encoding.  (See RFC 2045.)
 * 
 * TODO: Use fred's base64, we should not duplicate code!
 * I have asked the original author of this code why he did not use it and he said:
 * Uhh... because I didn't think of it. :)
 * Also, to use that code for decoding base64 messages you'd have to
 * strip out non-base64 characters (such as line breaks) beforehand; to
 * use it for encoding messages, you'd need to insert line breaks; and
 * either way involves another byte[] <-> String conversion step.  So
 * rewriting it probably was easier
 */
public class Base64Encoding extends TransferEncoding {

	private boolean ignoreErrors;

	private static final char[] encodedChars = {
		'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
		'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
		'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
		'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
		'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
		'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
		'w', 'x', 'y', 'z', '0', '1', '2', '3',
		'4', '5', '6', '7', '8', '9', '+', '/' };

	public Base64Encoding() {
		ignoreErrors = false;
	}

	@Override public void setIgnoreErrors(boolean ignore) {
		ignoreErrors = ignore;
	}

	private byte encodeChar(int bits) {
		return (byte) encodedChars[bits & 0x3f];
	}

	@Override public ByteBuffer encode(ByteBuffer input) {
		ByteBuffer result = ByteBuffer.allocate(100);
		int bits = 0;
		int byteCount = 0;
		int charCount = 0;

		while (input.hasRemaining()) {
			int b = (input.get() & 0xff);
			bits = (bits << 8) | b;
			byteCount++;

			if (byteCount == 3) {
				result = appendByte(result, encodeChar(bits >> 18));
				result = appendByte(result, encodeChar(bits >> 12));
				result = appendByte(result, encodeChar(bits >> 6));
				result = appendByte(result, encodeChar(bits));
				byteCount = 0;
				charCount += 4;
				
				// FIXME: toad said: "Should bits be reset here?"
				// Either fix this OR just use fred's base64.

				if (charCount >= 76) {
					result = appendByte(result, (byte) '\r');
					result = appendByte(result, (byte) '\n');
					charCount = 0;
				}
			}
		}

		if (byteCount == 1) {
			result = appendByte(result, encodeChar(bits >> 2));
			result = appendByte(result, encodeChar(bits << 4));
			result = appendByte(result, (byte) '=');
			result = appendByte(result, (byte) '=');
		}
		else if (byteCount == 2) {
			result = appendByte(result, encodeChar(bits >> 10));
			result = appendByte(result, encodeChar(bits >> 4));
			result = appendByte(result, encodeChar(bits << 2));
			result = appendByte(result, (byte) '=');
		}

		result = appendByte(result, (byte) '\r');
		result = appendByte(result, (byte) '\n');
		result.flip();
		return result;
	}

	@Override public ByteBuffer decode(ByteBuffer input) throws InvalidEncodedTextException {
		ByteBuffer result = ByteBuffer.allocate(100);
		int bits = 0;
		int bitCount = 0;
		boolean atEOF = false;

		while (input.hasRemaining()) {
			byte b = input.get();

			if (b >= 'A' && b <= 'Z') {
				bits = (bits << 6) | (b - 'A');
				bitCount += 6;
			}
			else if (b >= 'a' && b <= 'z') {
				bits = (bits << 6) | (b + 26 - 'a');
				bitCount += 6;
			}
			else if (b >= '0' && b <= '9') {
				bits = (bits << 6) | (b + 52 - '0');
				bitCount += 6;
			}
			else if (b == '+') {
				bits = (bits << 6) | 62;
				bitCount += 6;
			}
			else if (b == '/') {
				bits = (bits << 6) | 63;
				bitCount += 6;
			}
			else if (b == '=')
				atEOF = true;

			while (!atEOF && bitCount >= 8) {
				result = appendByte(result, (byte) (bits >> (bitCount - 8)));
				bitCount -= 8;
			}
		}

		if (!ignoreErrors && bitCount >= 6) {
			throw new InvalidEncodedTextException("Extraneous characters following end of base64 data");
		}

		result.flip();
		return result;
	}
}
