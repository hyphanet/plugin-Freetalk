/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.FTMessage;
import plugins.Freetalk.FTBoard;
import plugins.Freetalk.Freetalk;

import freenet.keys.FreenetURI;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Object representing a single news article.
 *
 * @author Benjamin Moody
 */
public class FreetalkNNTPArticle {
	public static final String[] ALL_HEADERS = { "From", "Subject", "Newsgroups",
												 "Date", "Message-ID", "References",
												 "Path", "Content-Type" };
	
	/* FIXME: Message.getDate() returns UTC time. If newsreaders expect UTC, this is correct. If they expect to receive their local time
	 * then we need to convert to the local time of the newsreader by specifying the time zone when creating the SimpleDateFormat. 
	 * SimpleDateFormat interprets Date objects given to it as UTC and converts them to the specified timezone automaticall. */
	public static final SimpleDateFormat mDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);

	private final FTMessage message;

	public FreetalkNNTPArticle(FTMessage message) {
		this.message = message;
	}

	/**
	 * Get the FTMessage object associated with this group.
	 */
	public FTMessage getMessage() {
		return message;
	}

	/**
	 * Get the contents of the named header; if the header is not
	 * present, return the empty string.
	 */
	public String getHeaderByName(String name) {
		
		/* FIXME: To speed this up, do not use String name but create an enum Headers which has an entry for each header and index the 
		 * String[] ALL_HEADERS by the elements of that enum. Then this function will just have to switch() on a value of the enum instead
		 * of comparing strings */
		
		if (name.equalsIgnoreCase("From")) {
			/* The UID of an author is the base64 encoded routing key of his SSK keypair. We append ".freetalk" to the UID to make it look 
			 * like a valid domain. */
			return message.getAuthor().getNickname() + "@" + message.getAuthor().getUID() + "." + Freetalk.WOT_CONTEXT;
		}
		else if (name.equalsIgnoreCase("Subject")) {
			/* FIXME: The title is not cleaned up yet. Please give me (xor) the list of the control characters which the RFC forbids and I
			 * will provide a function to return a cleaned up title */ 
			return message.getTitle();
		}
		else if (name.equalsIgnoreCase("Newsgroups")) {
			FTBoard boards[] = message.getBoards();
			StringBuilder builder = new StringBuilder();

			builder.append(boards[0].getNameNNTP());

			for (int i = 1; i < boards.length; i++) {
				builder.append(", ");
				builder.append(boards[i].getNameNNTP());
			}

			return builder.toString();
		}
		else if (name.equalsIgnoreCase("Date")) {
			synchronized(mDateFormat) {
				return mDateFormat.format(message.getDate());
			}
		}
		else if (name.equalsIgnoreCase("Message-ID")) {
			return "<" + message.getID() + ">";
		}
		else if (name.equalsIgnoreCase("References")) {
			// FIXME: it would be good for the message to include a
			// list of earlier messages in the thread, in case the
			// parent message can't be retrieved.

			if (message.isThread())
				return "";
			else
				return "<" + message.getParentID() + ">";
		}
		else if (name.equalsIgnoreCase("Path")) {
			return Freetalk.WOT_CONTEXT;
		}
		else if (name.equalsIgnoreCase("Content-Type")) {
			return "text/plain; charset=UTF-8";
		}
		else
			return "";
	}

	/**
	 * Get the complete list of headers.
	 */
	public String getHead() {
		StringBuilder builder = new StringBuilder();

		synchronized (message) {
			for (int i = 0; i < ALL_HEADERS.length; i++) {
				String hdr = getHeaderByName(ALL_HEADERS[i]);
				if (!hdr.equals("")) {
					builder.append(ALL_HEADERS[i]);
					builder.append(": ");
					builder.append(hdr);
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
}
