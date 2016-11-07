/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP.MIME;

import java.nio.ByteBuffer;

/**
 * The MIME Quoted-Printable encoding.  (See RFC 2045.)
 */
public class QuotedPrintableEncoding extends TransferEncoding {

	private final byte[] extraQuotedChars;
	private final boolean spaceAsUnderscore;
	private final boolean literalCRLF;

	private boolean ignoreErrors;

	private ByteBuffer outputBuffer;
	private int outputLineWidth;

	public QuotedPrintableEncoding(String extraQuotedChars, boolean spaceAsUnderscore, boolean literalCRLF) {
		this.extraQuotedChars = extraQuotedChars.getBytes();
		this.spaceAsUnderscore = spaceAsUnderscore;
		this.literalCRLF = literalCRLF;
		ignoreErrors = false;
	}

	@Override public void setIgnoreErrors(boolean ignore) {
		ignoreErrors = ignore;
	}

	/**
	 * Output a soft line break (one the reader should ignore)
	 */
	private void putSoftBreak() {
		outputBuffer = appendByte(outputBuffer, (byte) '=');
		outputBuffer = appendByte(outputBuffer, (byte) '\r');
		outputBuffer = appendByte(outputBuffer, (byte) '\n');
		outputLineWidth = 0;
	}

	/**
	 * Output a literal byte
	 */
	private void putChar(byte b) {
		if (outputLineWidth >= 74)
			putSoftBreak();
		outputBuffer = appendByte(outputBuffer, b);
		outputLineWidth++;
	}

	/**
	 * Convert a value 0-15 to an ASCII hex digit
	 */
	private byte hexDigit(int v) {
		if (v < 10)
			return (byte) (0x30 + v);
		else
			return (byte) (0x41 + v - 10);
	}

	/**
	 * Output a byte in hex encoding
	 */
	private void putEncoded(byte b) {
		if (outputLineWidth >= 72)
			putSoftBreak();
		outputBuffer = appendByte(outputBuffer, (byte) '=');
		outputBuffer = appendByte(outputBuffer, hexDigit((b >> 4) & 0xf));
		outputBuffer = appendByte(outputBuffer, hexDigit((b & 0xf)));
		outputLineWidth += 3;
	}

	/**
	 * Determine if this byte value must be quoted
	 */
	private boolean mustQuoteByte(byte b) {
		if (b > 0x7e || b < 0x20 || b == (byte) '=')
			return true;

		if (spaceAsUnderscore && b == (byte) '_')
			return true;

		for (int i = 0; i < extraQuotedChars.length; i++)
			if (b == extraQuotedChars[i])
				return true;

		return false;
	}

	@Override public synchronized ByteBuffer encode(ByteBuffer input) {
		boolean cr = false;

		outputBuffer = ByteBuffer.allocate(100);
		outputLineWidth = 0;

		while (input.hasRemaining()) {
			byte b = input.get();

			if (cr && b != 10)
				putEncoded((byte) 13);

			if (!literalCRLF && b == 13) {
				cr = true;
			}
			else if (!literalCRLF && cr && b == 10) {
				putChar((byte) 13);
				putChar((byte) 10);
				outputLineWidth = 0;
				cr = false;
			}
			else if (mustQuoteByte(b)) {
				putEncoded(b);
				cr = false;
			}
			else {
				putChar(b);
				cr = false;
			}
		}

		outputBuffer.flip();
		return outputBuffer;
	}

	private int hexValue(byte b) {
		if (b >= 0x30 && b <= 0x39)
			return b - 0x30;
		else if (b >= 0x41 && b <= 0x46)
			return b - 0x41 + 10;
		else if (b >= 0x61 && b <= 0x66)
			return b - 0x61 + 10;
		else
			throw new IllegalArgumentException();
	}

	@Override public ByteBuffer decode(ByteBuffer input) throws InvalidEncodedTextException {
		ByteBuffer result = ByteBuffer.allocate(100);

		while (input.hasRemaining()) {
			byte b = input.get();

			if (b == (byte) '_' && spaceAsUnderscore)
				result = appendByte(result, (byte) ' ');
			else if (b != (byte) '=')
				result = appendByte(result, b);
			else {
				byte c, d;

				if (!input.hasRemaining()) {
					if (ignoreErrors) {
						result = appendByte(result, b);
						break;
					}
					else {
						throw new InvalidEncodedTextException("Quoted-printable data ends prematurely");
					}
				}

				c = input.get();

				// check for soft newline
				if (c == 13) {
					if (input.hasRemaining()) {
						d = input.get();
						if (d != 10)
							input.position(input.position() - 1);
					}
				}
				else if (c != 10) {
					if (!input.hasRemaining()) {
						if (ignoreErrors) {
							result = appendByte(result, b);
							result = appendByte(result, c);
							break;
						}
						else {
							throw new InvalidEncodedTextException("Quoted-printable data ends prematurely");
						}
					}

					d = input.get();

					try {
						int high = hexValue(c);
						int low = hexValue(d);
						result = appendByte(result, (byte) ((high << 4) + low));
					}
					catch (IllegalArgumentException e) {
						if (ignoreErrors) {
							result = appendByte(result, b);
							input.position(input.position() - 2);
						}
						else
							throw new InvalidEncodedTextException("Invalid quote sequence in quoted-printable data");
					}
				}
			}
		}

		result.flip();
		return result;
	}
}
