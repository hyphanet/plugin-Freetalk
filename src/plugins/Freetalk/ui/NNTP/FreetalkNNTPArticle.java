/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.Message;
import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;

import freenet.keys.FreenetURI;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import java.io.UnsupportedEncodingException;

/**
 * Object representing a single news article.
 *
 * @author Benjamin Moody
 */
public class FreetalkNNTPArticle {
	public enum Header {
		FROM ("From"), SUBJECT ("Subject"), NEWSGROUPS ("Newsgroups"),
		DATE ("Date"), MESSAGE_ID ("Message-ID"), REFERENCES ("References"),
		PATH ("Path"), CONTENT_TYPE ("Content-Type");

		private String name;

		Header(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	/* FIXME: Message.getDate() returns UTC time. If newsreaders expect UTC, this is correct. If they expect to receive their local time
	 * then we need to convert to the local time of the newsreader by specifying the time zone when creating the SimpleDateFormat. 
	 * SimpleDateFormat interprets Date objects given to it as UTC and converts them to the specified timezone automaticall. */
	public static final SimpleDateFormat mDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);

	public static final Pattern endOfLinePattern = Pattern.compile("\r\n?|\n");

	private final Message message;

	public FreetalkNNTPArticle(Message message) {
		this.message = message;
	}

	/**
	 * Get the FTMessage object associated with this group.
	 */
	public Message getMessage() {
		return message;
	}

	/**
	 * Get the contents of the named header; if the header is not
	 * present, return the empty string.
	 */
	public String getHeaderByName(String name) {
		for (Header hdr : Header.values())
			if (name.equalsIgnoreCase(hdr.getName()))
				return getHeader(hdr);

		return "";
	}

	/**
	 * Get the contents of the given header.
	 */
	public String getHeader(Header hdr) {
		switch (hdr) {
		case FROM:
			/* The UID of an author is the base64 encoded routing key of his SSK keypair. We append ".freetalk" to the UID to make it look 
			 * like a valid domain. */
			return message.getAuthor().getNickname() + "@" + message.getAuthor().getUID() + "." + Freetalk.WOT_CONTEXT;

		case SUBJECT:
			/* FIXME: The title is not cleaned up yet. Please give me (xor) the list of the control characters which the RFC forbids and I
			 * will provide a function to return a cleaned up title */ 
			return message.getTitle();

		case NEWSGROUPS:
			Board boards[] = message.getBoards();
			StringBuilder builder = new StringBuilder();

			builder.append(boards[0].getNameNNTP());

			for (int i = 1; i < boards.length; i++) {
				builder.append(", ");
				builder.append(boards[i].getNameNNTP());
			}

			return builder.toString();

		case DATE:
			synchronized(mDateFormat) {
				return mDateFormat.format(message.getDate());
			}

		case MESSAGE_ID:
			return "<" + message.getID() + ">";

		case REFERENCES:
			// FIXME: it would be good for the message to include a
			// list of earlier messages in the thread, in case the
			// parent message can't be retrieved.

			if (message.isThread())
				return "";
			else
				return "<" + message.getParentID() + ">";

		case PATH:
			return Freetalk.WOT_CONTEXT;

		case CONTENT_TYPE:
			return "text/plain; charset=UTF-8";

		default:
			return "";
		}
	}

	/**
	 * Get the complete list of headers.
	 */
	public String getHead() {
		StringBuilder builder = new StringBuilder();

		synchronized (message) {
			for (Header hdr : Header.values()) {
				String text = getHeader(hdr);
				if (!text.equals("")) {
					builder.append(hdr.getName());
					builder.append(": ");
					builder.append(text);
					builder.append("\n");
				}
			}
		}

		return builder.toString();
	}

	/**
	 * Get the message body.
	 */
	public String getBody() {
		return message.getText();
	}

	/**
	 * Get the number of lines in the article's body.
	 */
	public long getBodyLineCount() {
		String[] bodyLines = endOfLinePattern.split(getBody());
		return bodyLines.length;
	}

	/**
	 * Get number of bytes to encode string as UTF-8
	 */
	private long byteCountUTF8(String s) {
		// GAH!  There must be a simpler way to do this
		try {
			byte[] b = s.getBytes("UTF-8");
			return b.length;
		}
		catch (UnsupportedEncodingException e) {
			return 0;
		}
	}

	/**
	 * Get the total size of the article.
	 */
	public long getByteCount() {
		String[] headLines = endOfLinePattern.split(getHead());
		String[] bodyLines = endOfLinePattern.split(getBody());
		long count = 2;
		int i;

		for (i = 0; i < headLines.length; i++)
			count += byteCountUTF8(headLines[i]) + 2;
		for (i = 0; i < bodyLines.length; i++)
			count += byteCountUTF8(bodyLines[i]) + 2;

		return count;
	}
}
