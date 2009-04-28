/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.Logger;

/**
 * Object representing a single news article.
 *
 * @author Benjamin Moody
 */
public class FreetalkNNTPArticle {
	public enum Header {
		FROM ("From"), SUBJECT ("Subject"), NEWSGROUPS ("Newsgroups"),
		DATE ("Date"), MESSAGE_ID ("Message-ID"), REFERENCES ("References"),
		PATH ("Path"), CONTENT_TYPE ("Content-Type"),
		FOLLOWUP_TO ("Followup-To");

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

	private final int messageNum;

	public FreetalkNNTPArticle(Message message) {
		this.message = message;
		messageNum = 0;
	}

	public FreetalkNNTPArticle(Message message, int messageNum) {
		this.message = message;
		this.messageNum = messageNum;
	}

	/**
	 * Get the FTMessage object associated with this group.
	 */
	public Message getMessage() {
		return message;
	}

	/**
	 * Get the message number, or 0 if none was set.
	 */
	public int getMessageNum() {
		return messageNum;
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
	 * Wrap header contents onto multiple lines.  Wrapping is done so
	 * as to limit the number of bytes (of UTF-8) on a single line.
	 */
	private static String wrapHeader(String name, String text, int softLimit, int hardLimit) {
		StringBuilder result = new StringBuilder();
		int lineStart, wordPos, width, i;

		lineStart = 0;
		width = name.length() + 2;
		while (lineStart < text.length()) {
			wordPos = lineStart;

			for (i = lineStart; i < text.length(); i++) {
				int c = text.codePointAt(i);
				int cwidth;

				if (Character.isSpaceChar(c))
					wordPos = i;

				if (c < 0x80)
					cwidth = 1;
				else if (c < 0x800)
					cwidth = 2;
				else if (c < 0x10000)
					cwidth = 3;
				else {
					cwidth = 4;
					i++;
				}

				if (width + cwidth > softLimit && wordPos != lineStart)
					break;
				else if (width + cwidth > hardLimit)
					break;
				else
					width += cwidth;
			}


			if (i == text.length() || wordPos == lineStart) {
				result.append(text.substring(lineStart, i));
				lineStart = i;
			}
			else {
				result.append(text.substring(lineStart, wordPos));
				lineStart = wordPos + 1;
			}

			if (lineStart < text.length())
				result.append("\n ");
			width = 1;
		}

		return result.toString();
	}

	/**
	 * Get the contents of the given header.  The header is not
	 * wrapped (it may be arbitrarily long, but will not contain any
	 * line feeds, tabs, or other control characters.)
	 */
	public String getHeader(Header hdr) {
		switch (hdr) {
		case FROM:
			return message.getAuthor().getFreetalkAddress();

		case SUBJECT:
			return message.getTitle();

		case NEWSGROUPS:
			Board boards[] = message.getBoards();
			StringBuilder builder = new StringBuilder();

			builder.append(FreetalkNNTPGroup.boardToGroupName(boards[0].getName()));

			for (int i = 1; i < boards.length; i++) {
				builder.append(", ");
				builder.append(FreetalkNNTPGroup.boardToGroupName(boards[i].getName()));
			}

			return builder.toString();

		case FOLLOWUP_TO:
			Board board = message.getReplyToBoard();
			if (board == null)
				return "";
			else
				return FreetalkNNTPGroup.boardToGroupName(board.getName());

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
			else {
				try {
					return "<" + message.getParentID() + ">";
				}
				catch(NoSuchMessageException e) {
					Logger.error(this, "Should not happen", e);
					return "";
				}
			}

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
					builder.append(wrapHeader(hdr.getName(), text, 72, 998));
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
