/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.FTMessage;
import plugins.Freetalk.FTBoard;

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
		if (name.equalsIgnoreCase("From")) {
			// FIXME: what is the format of the UID?  Is it something
			// that looks like an address?
			return message.getAuthor().getUID();
		}
		else if (name.equalsIgnoreCase("Subject")) {
			// FIXME: do we need to clean this up?  (No control
			// characters allowed)
			return message.getTitle();
		}
		else if (name.equalsIgnoreCase("Newsgroups")) {
			FTBoard boards[] = message.getBoards();
			StringBuilder builder = new StringBuilder();

			if (boards.length > 0)
				builder.append(boards[0].getNameNNTP());

			for (int i = 1; i < boards.length; i++) {
				builder.append(", ");
				builder.append(boards[i].getNameNNTP());
			}

			return builder.toString();
		}
		else if (name.equalsIgnoreCase("Date")) {
			SimpleDateFormat fmt = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
			return fmt.format(message.getDate());
		}
		else if (name.equalsIgnoreCase("Message-ID")) {
			return "<" + message.getID() + ">";
		}
		else if (name.equalsIgnoreCase("References")) {
			// FIXME: it would be good for the message to include a
			// list of earlier messages in the thread, in case the
			// parent message can't be retrieved.

			FreenetURI parentURI = message.getParentURI();
			if (parentURI == null)
				return "";
			else
				return "<" + FTMessage.generateID(parentURI) + ">";
		}
		else if (name.equalsIgnoreCase("Path")) {
			return "freenet";
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
