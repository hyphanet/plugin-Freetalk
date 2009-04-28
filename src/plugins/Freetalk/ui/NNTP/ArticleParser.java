/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import plugins.Freetalk.Board;
import plugins.Freetalk.Message;
import plugins.Freetalk.ui.NNTP.MIME.TransferEncoding;
import freenet.support.Logger;

/**
 * Class for parsing input messages (received from the client by way
 * of the POST command.)
 */
public class ArticleParser {

	private static final Pattern encodedWordPattern = Pattern.compile("=\\?([^\\]\\[()<>@,;:\"/?.=]+)" // charset
																	  + "\\?([^\\]\\[()<>@,;:\"/?.=]+)" // encoding
																	  + "\\?([^? ]*)\\?="); // text

	/** Author's nickname (local part of mail address) */
	private String authorName;

	/** Domain part of mail address */
	private String authorDomain;

	/** Message title (subject) */
	private String title;

	/** Message date */
	private Date date;

	/** List of board names */
	private ArrayList<String> boards;

	/** Board name to send replies */
	private String replyToBoard;

	/** Message-ID of previous message */
	private String parentID;

	/** Body of message */
	private String text;


	public ArticleParser() {
		authorName = null;
		authorDomain = null;
		title = null;
		date = null;
		boards = null;
		replyToBoard = null;
		parentID = null;
		text = null;
	}

	public String getAuthorName() {
		return authorName;
	}

	public String getAuthorDomain() {
		return authorDomain;
	}

	public String getTitle() {
		return title;
	}

	public List<String> getBoards() {
		return boards;
	}

	public String getReplyToBoard() {
		return replyToBoard;
	}

	public String getParentID() throws NoSuchFieldException {
		if(parentID == null)
			throw new NoSuchFieldException(); /* FIXME: Also throw this in the other getter functions */
		return parentID;
	}

	public String getText() {
		return text;
	}


	/**
	 * A MIME content type.
	 */
	private static class ContentType {
		public String type;
		public String subtype;
		public String charset;
		public String boundary;

		public ContentType(String type, String subtype, String charset, String boundary) {
			this.type = type;
			this.subtype = subtype;
			this.charset = charset;
			this.boundary = boundary;
		}

		/**
		 * Parse a Content-Type header.
		 */
		public static ContentType parseHeader(String hdr) {
			HeaderTokenizer tokenizer = new HeaderTokenizer(hdr, true, true, "[]()<>@,;:\\/?=");
			ContentType result = new ContentType(null, null, "us-ascii", null);

			if (tokenizer.getToken() != 0)
				return null;
			result.type = tokenizer.getTokenText();

			if (tokenizer.getToken() != '/')
				return null;

			if (tokenizer.getToken() != 0)
				return null;
			result.subtype = tokenizer.getTokenText();

			while (tokenizer.getToken() == ';') {
				if (tokenizer.getToken() != 0)
					break;
				String param = tokenizer.getTokenText();

				if (tokenizer.getToken() != '=')
					break;

				if (tokenizer.getToken() != 0)
					break;
				String value = tokenizer.getTokenText();

				if (param.equalsIgnoreCase("charset"))
					result.charset = value;
				else if (param.equalsIgnoreCase("boundary"))
					result.boundary = value;
			}

			return result;
		}
	}

	/**
	 * A single mailbox (user at hostname.)
	 */
	private static class Mailbox {
		public String name;
		public String local;
		public String domain;

		public Mailbox(String name, String local, String domain) {
			this.name = name;
			this.local = local;
			this.domain = domain;
		}

		/**
		 * Parse a From header and extract the first address.  (This does
		 * not fully parse the header, so it will accept strings that are
		 * not actually valid mailing addresses.)
		 */
		public static Mailbox parseHeader(String hdr) {
			HeaderTokenizer tokenizer = new HeaderTokenizer(hdr);
			String name = null, local = null, domain = null;
			StringBuilder current = new StringBuilder();
			boolean inDomain = false, inAngles = false;

			while (tokenizer.tokensRemaining()) {
				int c = tokenizer.getToken();
				if (c == '@') {
					if (inDomain)
						return null;

					local = current.toString();
					inDomain = true;
					current = new StringBuilder();
				}
				else if (c == '.') {
					current.append('.');
				}
				else if (c == 0) {
					String word = tokenizer.getTokenText();
					current.append(word);
				}
				else if (c == '<') {
					if (inAngles || inDomain)
						return null;
					name = current.toString();
					inAngles = true;
					current = new StringBuilder();
				}
				else if (inDomain && c == '>') {
					inAngles = false;
					break;
				}
				else if (inDomain && (c == ',' || c == ';')) {
					break;
				}
				else {
					name = local = domain = null;
					inDomain = inAngles = false;
					current = new StringBuilder();
				}
			}

			if (inDomain && !inAngles) {
				domain = current.toString();
				return new Mailbox(name, local, domain);
			}
			else
				return null;
		}
	}

	/**
	 * Parse a Newsgroups or Follow-To header and return a list of
	 * newsgroup names.
	 */
	private static ArrayList<String> parseNewsgroups(String hdr) {
		HeaderTokenizer tokenizer = new HeaderTokenizer(hdr, false, false, ",");
		ArrayList<String> result = new ArrayList<String>();

		while (tokenizer.tokensRemaining()) {
			int c = tokenizer.getToken();
			if (c == 0) {
				String name = tokenizer.getTokenText();
				String boardName = FreetalkNNTPGroup.groupToBoardName(name);
				if (Board.isNameValid(boardName))
					result.add(boardName);
			}
		}

		return result;
	}

	/**
	 * Parse a References header and return a list of message IDs.
	 */
	private static ArrayList<String> parseReferences(String hdr) {
		HeaderTokenizer tokenizer = new HeaderTokenizer(hdr);
		StringBuilder current = null;
		ArrayList<String> result = new ArrayList<String>();

		while (tokenizer.tokensRemaining()) {
			int c = tokenizer.getToken();
			if (c == '<') {
				current = new StringBuilder();
			}
			else if (c == '>') {
				if (current != null)
					result.add(current.toString());
				current = null;
			}
			else if (c == 0) {
				if (current != null)
					current.append(tokenizer.getTokenText());
			}
			else if (c > 0) {
				if (current != null)
					current.append((char) c);
			}
		}

		return result;
	}

	/**
	 * Decode any encoded words in the header as per RFC 2047
	 */
	private static String decodeMIMEHeader(String str) {
		StringBuilder result = new StringBuilder();
		Matcher matcher = encodedWordPattern.matcher(str);
		int pos = 0;

		while (matcher.find()) {
			String charsetName = matcher.group(1);
			String encodingName = matcher.group(2);
			String data = matcher.group(3);

			result.append(str.substring(pos, matcher.start()));

			try {
				Charset charset = Charset.forName(charsetName);
				TransferEncoding encoding = TransferEncoding.headerWordEncoding(encodingName);
				byte[] encodedBytes = data.getBytes("US-ASCII");
				ByteBuffer decodedBytes = encoding.decode(ByteBuffer.wrap(encodedBytes));
				result.append(charset.decode(decodedBytes));
			}
			catch (Exception e) {
				result.append(matcher.group());
			}

			pos = matcher.end();
		}

		result.append(str.substring(pos));
		return result.toString();
	}

	/**
	 * Get the named header contents.
	 */
	private static String getHeader(String[] headLines, String name) {
		int i, j;
		for (i = 0; i < headLines.length; i++) {
			for (j = 0; j < name.length() && j < headLines[i].length(); j++) {
				if (name.charAt(j) != Character.toLowerCase(headLines[i].charAt(j)))
					break;
			}

			if (j < name.length())
				continue;

			while (j < headLines[i].length()
				   && (headLines[i].charAt(j) == ' '
					   || headLines[i].charAt(j) == '\t'))
				j++;

			if (j >= headLines[i].length()
				|| headLines[i].charAt(j) != ':')
				continue;
			j++;

			// Skip initial whitespace
			while (j < headLines[i].length()
				   && (headLines[i].charAt(j) == ' '
					   || headLines[i].charAt(j) == '\t'))
				j++;

			StringBuilder result = new StringBuilder(decodeMIMEHeader(headLines[i].substring(j)));

			// Find continuation lines
			i++;
			while (i < headLines.length
				   && headLines[i].length() > 0
				   && (headLines[i].charAt(0) == ' '
					   || headLines[i].charAt(0) == '\t')) {
				result.append('\n');
				result.append(decodeMIMEHeader(headLines[i]));
				i++;
			}

			return result.toString();
		}

		return null;
	}

	/**
	 * Parse the message body.
	 */
	private void parseBody(ByteBuffer bytes, ContentType type, String encodingName) {
		// FIXME: handle multi-part content, upload non-text parts as
		// attachments, etc.

		Charset bodyCharset;
		try {
			bodyCharset = Charset.forName(type.charset);
		}
		catch (IllegalArgumentException e) {
			bodyCharset = Charset.forName("UTF-8");
		}

		try {
			TransferEncoding encoding = TransferEncoding.bodyEncoding(encodingName);
			ByteBuffer decodedBytes = encoding.decode(bytes);
			text = bodyCharset.decode(decodedBytes).toString();
		}
		catch (Exception e) {
			text = bodyCharset.decode(bytes).toString();
		}
	}

	/**
	 * Parse a complete message.  The input is given as a byte buffer
	 * since we do not know what encoding has been used until we parse
	 * the header.  Return true if the message was parsed successfully.
	 */
	public boolean parseMessage(ByteBuffer bytes) {
		// split up message into head + body
		ByteBuffer headBytes, bodyBytes;
		boolean linestart = true;

		// Find the blank line separating head from body
		while (bytes.hasRemaining()) {
			byte b = bytes.get();
			if (bytes.hasRemaining() && b == '\r')
				b = bytes.get();
			if (b == '\n') {
				if (linestart)
					break;
				else
					linestart = true;
			}
			else
				linestart = false;
		}

		if (!bytes.hasRemaining()) {
			Logger.debug(this, "Unable to find start of message body");
			return false;
		}

		// Save remaining (unread) bytes in bodyBytes...
		bodyBytes = bytes.slice();
		// and previous bytes in headBytes
		bytes.flip();
		headBytes = bytes.slice();

		// First try decoding headers as UTF-8.

		Charset utf8 = Charset.forName("UTF-8");
		String headUTF8 = utf8.decode(headBytes).toString();
		String[] headLines = headUTF8.split("\r?\n");

		// Read Newsgroups, Followup-To, and References headers
		String newsgroupsHeader = getHeader(headLines, "newsgroups");
		String followupToHeader = getHeader(headLines, "followup-to");
		String referencesHeader = getHeader(headLines, "references");
		String inReplyToHeader = getHeader(headLines, "in-reply-to");
		String transferEncodingHeader = getHeader(headLines, "content-transfer-encoding");

		if (newsgroupsHeader == null) {
			Logger.debug(this, "Unable to find Newsgroups header");
			return false;
		}

		// Read Content-Type header...
		String typeHeader = getHeader(headLines, "content-type");
		ContentType bodyType = null;
		if (typeHeader != null)
			bodyType = ContentType.parseHeader(typeHeader);

		if (bodyType == null)
			bodyType = new ContentType("text", "plain", "UTF-8", null);

		// ... and then try decoding the headers again using the body
		// charset.

		Charset bodyCharset;
		try {
			bodyCharset = Charset.forName(bodyType.charset);
		}
		catch (IllegalArgumentException e) {
			bodyCharset = Charset.forName("UTF-8");
		}

		headBytes.rewind();
		String head = bodyCharset.decode(headBytes).toString();
		headLines = head.split("\r?\n");

		// Read From and Subject headers using the body charset
		String fromHeader = getHeader(headLines, "from");
		String subjectHeader = getHeader(headLines, "subject");

		if (fromHeader == null) {
			Logger.debug(this, "Unable to find From header");
			return false;
		}

		if (subjectHeader == null) {
			Logger.debug(this, "Unable to find Subject header");
			return false;
		}

		// Try using the body charset for Newsgroups and Followup-To
		// if UTF-8 didn't work.
		if (newsgroupsHeader.indexOf(0xfffd) != -1
			|| (followupToHeader != null && followupToHeader.indexOf(0xfffd) != -1)) {
			newsgroupsHeader = getHeader(headLines, "newsgroups");
			followupToHeader = getHeader(headLines, "followup-to");
		}

		Mailbox addr = Mailbox.parseHeader(fromHeader);
		if (addr == null) {
			Logger.debug(this, "Unable to parse From header");
			return false;
		}

		authorName = addr.local;
		authorDomain = addr.domain;

		title = Message.makeTitleValid(subjectHeader);

		boards = parseNewsgroups(newsgroupsHeader);

		if (followupToHeader != null) {
			ArrayList<String> followups = parseNewsgroups(followupToHeader);
			if (!followups.isEmpty())
				replyToBoard = followups.get(0);
		}

		if (inReplyToHeader != null) {
			ArrayList<String> refs = parseReferences(inReplyToHeader);
			if (!refs.isEmpty())
				parentID = refs.get(refs.size() - 1);
		}
		else if (referencesHeader != null) {
			ArrayList<String> refs = parseReferences(referencesHeader);
			if (!refs.isEmpty())
				parentID = refs.get(refs.size() - 1);
		}

		if (transferEncodingHeader != null)
			parseBody(bodyBytes, bodyType, transferEncodingHeader);
		else
			parseBody(bodyBytes, bodyType, "8bit");

		return true;
	}
}
