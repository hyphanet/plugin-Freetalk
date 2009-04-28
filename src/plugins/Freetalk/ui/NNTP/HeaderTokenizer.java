/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

/**
 * Class for breaking down a header field into tokens.  Tokens consist
 * of words (delimited by whitespace) and special characters (which
 * are each tokens in their own right.)  Comments and quoted strings
 * can be used, following RFC 822 syntax.
 */
public class HeaderTokenizer {
	/**
	 * Default list of special characters.  All control characters are
	 * also considered special, with the exception of tab, LF, and CR
	 * (which are considered whitespace.)
	 */
	public static final String DEFAULT_SPECIALS = "[]()<>@,;:\\.";

	/** Header text we are parsing */
	private final String text;

	/** List of characters that are considered "special" (i.e., delimiters) */
	private final String specials;

	/** Whether we should skip comments (delimited by parentheses.) */
	private final boolean useComments;

	/** Whether we should handle quoted strings. */
	private final boolean useQuotes;

	/** Current position in the input */
	private int position;

	/** Last word token */
	private String lastTokenText;

	public HeaderTokenizer(String text, boolean useComments, boolean useQuotes, String specials) {
		this.text = text;
		this.specials = specials;
		this.useComments = useComments;
		this.useQuotes = useQuotes;
		position = 0;
		lastTokenText = null;
	}

	public HeaderTokenizer(String text) {
		this.text = text;
		specials = DEFAULT_SPECIALS;
		useComments = useQuotes = true;
		position = 0;
		lastTokenText = null;
	}

	/**
	 * Move the current position past any whitespace and comments.
	 * Comments are delimited by parentheses and may be nested (ugh!)
	 */
	private void skipWhitespace() {
		int nparen = 0;

		while (position < text.length()) {
			char c = text.charAt(position);
			if (useComments && c == '(')
				// comment start
				nparen++;
			else if (c == ')' && nparen > 0)
				// comment end
				nparen--;
			else if (c == '\\' && nparen > 0)
				position++;
			else if (c != ' ' && c != '\t' && c != '\r' && c != '\n' && nparen == 0)
				break;
			position++;
		}
	}

	/**
	 * Check if any more tokens are available.
	 */
	public boolean tokensRemaining() {
		skipWhitespace();
		return (position < text.length());
	}

	/**
	 * Read the next token.  If we are at the end of the input,
	 * returns -1.  If the next token is a special character, returns
	 * that character as an integer.  If the next token is a quoted or
	 * unquoted word, returns 0 (call getTokenText() to read the
	 * contents.)
	 */
	public int getToken() {
		lastTokenText = null;

		if (!tokensRemaining())
			return -1;

		char c = text.charAt(position);
		position++;

		if (specials.indexOf(c) != -1 || c < ' ' || c == 127)
			return c;

		if (useQuotes && c == '"') {
			// Quoted string
			StringBuilder result = new StringBuilder();

			while (position < text.length()) {
				c = text.charAt(position);
				position++;
				if (c == '\\') {
					if (position < text.length()) {
						c = text.charAt(position);
						position++;
						result.append(c);
					}
				}
				else if (c == '"')
					break;
				else
					result.append(c);
			}
			lastTokenText = result.toString();
			return 0;
		}

		// Atom (unquoted string)
		int start = position - 1;
		while (position < text.length()) {
			c = text.charAt(position);
			if (c <= ' ' || c == 127 || (useQuotes && c == '"')
				|| specials.indexOf(c) != -1)
				break;
			position++;
		}
		lastTokenText = text.substring(start, position);
		return 0;
	}

	/**
	 * If the previous call to getToken() returned 0, return the
	 * contents of the previous word token.
	 */
	public String getTokenText() {
		return lastTokenText;
	}
}
