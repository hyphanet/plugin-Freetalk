/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.Board;
import plugins.Freetalk.Message;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.HashSet;

import freenet.support.Logger;

/**
 * Represents a connection to a single NNTP client.
 *
 * @author Benjamin Moody
 */
public class FreetalkNNTPHandler implements Runnable {

	private IdentityManager mIdentityManager;
	private MessageManager mMessageManager;

	private Socket socket;
	private PrintStream out;

	/** Current board (selected by the GROUP command) */
	private FreetalkNNTPGroup currentGroup;
	
	private final static String CRLF = "\r\n";

	public FreetalkNNTPHandler(Freetalk ft, Socket socket) {
		mIdentityManager = ft.getIdentityManager();
		mMessageManager = ft.getMessageManager();
		this.socket = socket;
	}

	/**
	 * Check if handler is still active.
	 */
	public boolean isAlive() {
		return !socket.isClosed();
	}

	/**
	 * Close the connection to the client immediately.
	 */
	public synchronized void terminate() {
		try {
			socket.close();
		}
		catch (IOException e) {
			// ignore
		}
	}

	/**
	 * Print out a status response (numeric code plus additional
	 * information.)
	 */
	private void printStatusLine(String line) {
		out.print(line);
		// NNTP spec requires all command and response lines end with CR+LF
		out.print(CRLF);
		out.flush();
	}

	/**
	 * Print out a text response line.  The line will be "dot-stuffed"
	 * if necessary (any line beginning with a dot will have a second
	 * dot prepended.)
	 */
	private void printTextResponseLine(String line) {
		if (line.length() > 0 && line.charAt(0) == '.')
			out.print(".");
		out.print(line);
		out.print(CRLF);
	}

	/**
	 * Print out a block of text (changing all line terminators to
	 * CR+LF and dot-stuffing as necessary.)
	 */
	private void printText(String text) {
		String[] lines = FreetalkNNTPArticle.endOfLinePattern.split(text);
		for (int i = 0; i < lines.length; i++) {
			printTextResponseLine(lines[i]);
		}
	}

	/**
	 * Print a single dot to indicate the end of a text response.
	 */
	private void endTextResponse() {
		out.print("." + CRLF);
		out.flush();
	}

	/**
	 * Find the article named by 'desc'.  Print an error message if it
	 * can't be found.
	 */
	private FreetalkNNTPArticle getArticle(String desc, boolean setCurrent) {
		if (desc != null && desc.length() > 2 && desc.charAt(0) == '<'
			&& desc.charAt(desc.length() - 1) == '>') {

			String msgid = desc.substring(1, desc.length() - 1);
			try {
				return new FreetalkNNTPArticle(mMessageManager.get(msgid));
			}
			catch(NoSuchMessageException e) {
				printStatusLine("430 No such article");
				return null;
			}
		}
		else {
			// Other forms of these commands are not (yet) implemented
			printStatusLine("501 Syntax error");
			return null;
		}
	}


	/**
	 * Handle the ARTICLE / BODY / HEAD / STAT commands.
	 */
	private void selectArticle(String desc, boolean printHead, boolean printBody) {
		FreetalkNNTPArticle article = getArticle(desc, true);

		if (article == null)
			return;

		if (printHead && printBody) {
			printStatusLine("220 0 <" + article.getMessage().getID() + ">");
			printText(article.getHead());
			printTextResponseLine("");
			printText(article.getBody());
			endTextResponse();
		}
		else if (printHead) {
			printStatusLine("221 0 <" + article.getMessage().getID() + ">");
			printText(article.getHead());
			endTextResponse();
		}
		else if (printBody) {
			printStatusLine("222 0 <" + article.getMessage().getID() + ">");
			printText(article.getBody());
			endTextResponse();
		}
		else {
			printStatusLine("223 0 <" + article.getMessage().getID() + ">");
		}
	}

	/**
	 * Handle the GROUP command.
	 */
	private void selectGroup(String name) {
		// FIXME: look up by "NNTP name"
		try {
			Board board = mMessageManager.getBoardByName(name);
			currentGroup = new FreetalkNNTPGroup(board);
			printStatusLine("211 " + currentGroup.messageCount()
							+ " " + currentGroup.firstMessage()
							+ " " + currentGroup.lastMessage()
							+ " " + board.getNameNNTP());
		}
		catch(NoSuchBoardException e) {
			printStatusLine("411 No such group");
		}
	}

	/**
	 * Handle the LIST / LIST ACTIVE command.
	 */
	private void listActiveGroups(String pattern) {
		// FIXME: filter by wildmat
		printStatusLine("215 List of newsgroups follows:");
		for (Iterator<Board> i = mMessageManager.boardIterator(); i.hasNext(); ) {
			Board board = i.next();
			FreetalkNNTPGroup group = new FreetalkNNTPGroup(board);
			printTextResponseLine(board.getNameNNTP()
								  + " " + group.lastMessage()
								  + " " + group.firstMessage()
								  + " " + group.postingStatus());
		}
		endTextResponse();
	}

	/**
	 * Handle the HDR / XHDR command.
	 */
	private void printArticleHeader(String header, String articleDesc) {
		FreetalkNNTPArticle article = getArticle(articleDesc, false);
		if (article != null) {
			printStatusLine("224 Header contents follow");
			if (header.equalsIgnoreCase(":bytes"))
				printTextResponseLine("0 " + article.getByteCount());
			else if (header.equalsIgnoreCase(":lines"))
				printTextResponseLine("0 " + article.getBodyLineCount());
			else
				printTextResponseLine("0 " + article.getHeaderByName(header));
			endTextResponse();
		}
	}

	/**
	 * Handle the OVER / XOVER command.
	 */
	private void printArticleOverview(String articleDesc) {
		FreetalkNNTPArticle article = getArticle(articleDesc, false);
		if (article != null) {
			printStatusLine("224 Overview follows");
			printTextResponseLine("0\t" + article.getHeader(FreetalkNNTPArticle.Header.SUBJECT)
								  + "\t" + article.getHeader(FreetalkNNTPArticle.Header.FROM)
								  + "\t" + article.getHeader(FreetalkNNTPArticle.Header.DATE)
								  + "\t" + article.getHeader(FreetalkNNTPArticle.Header.MESSAGE_ID)
								  + "\t" + article.getHeader(FreetalkNNTPArticle.Header.REFERENCES)
								  + "\t" + article.getByteCount()
								  + "\t" + article.getBodyLineCount());
			endTextResponse();
		}
	}

	/**
	 * Handle the LIST HEADERS command.
	 */
	private void printHeaderList() {
		printStatusLine("215 Header list follows");
		// We allow querying any header (:) as well as byte and line counts
		printTextResponseLine(":");
		printTextResponseLine(":bytes");
		printTextResponseLine(":lines");
		endTextResponse();
	}

	/**
	 * Handle the LIST OVERVIEW.FMT command.
	 */
	private void printOverviewFormat() {
		printStatusLine("215 Overview format follows");
		printTextResponseLine("Subject:");
		printTextResponseLine("From:");
		printTextResponseLine("Date:");
		printTextResponseLine("Message-ID:");
		printTextResponseLine("References:");
		printTextResponseLine(":bytes");
		printTextResponseLine(":lines");
		endTextResponse();
	}

	/**
	 * Handle a command from the client.  If the command requires a
	 * text data section, this function returns true (and
	 * finishCommand should be called after the text has been
	 * received.)
	 */
	private synchronized boolean beginCommand(String line) throws IOException {
		String[] tokens = line.split("[ \t\r\n]+");
		if (tokens.length == 0)
			return false;

		String command = tokens[0];

		if (command.equalsIgnoreCase("ARTICLE")) {
			if (tokens.length == 2) {
				selectArticle(tokens[1], true, true);
			}
			else if (tokens.length == 1) {
				selectArticle(null, true, true);
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else if (command.equalsIgnoreCase("BODY")) {
			if (tokens.length == 2) {
				selectArticle(tokens[1], false, true);
			}
			else if (tokens.length == 1) {
				selectArticle(null, false, true);
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else if (command.equalsIgnoreCase("GROUP")) {
			if (tokens.length == 2) {
				selectGroup(tokens[1]);
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else if (command.equalsIgnoreCase("HDR") || command.equalsIgnoreCase("XHDR")) {
			if (tokens.length == 3) {
				printArticleHeader(tokens[1], tokens[2]);
			}
			else if (tokens.length == 2) {
				printArticleHeader(tokens[1], null);
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else if (command.equalsIgnoreCase("HEAD")) {
			if (tokens.length == 2) {
				selectArticle(tokens[1], true, false);
			}
			else if (tokens.length == 1) {
				selectArticle(null, true, false);
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else if (command.equalsIgnoreCase("LIST")) {
			if (tokens.length == 1 || tokens[1].equalsIgnoreCase("ACTIVE")) {
				if (tokens.length > 2)
					listActiveGroups(tokens[2]);
				else
					listActiveGroups(null);
			}
			else if (tokens[1].equalsIgnoreCase("HEADERS")) {
				printHeaderList();
			}
			else if (tokens[1].equalsIgnoreCase("OVERVIEW.FMT")) {
				printOverviewFormat();
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		// We accept XOVER for OVER because a lot of (broken)
		// newsreaders expect us to
		else if (command.equalsIgnoreCase("OVER") || command.equalsIgnoreCase("XOVER")) {
			if (tokens.length == 2) {
				printArticleOverview(tokens[1]);
			}
			else if (tokens.length == 1) {
				printArticleOverview(null);
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else if (command.equalsIgnoreCase("POST")) {
			printStatusLine("340 Please send article to be posted");
			return true;
		}
		else if (command.equalsIgnoreCase("QUIT")) {
			printStatusLine("205 Have a nice day.");
			socket.close();
		}
		else if (command.equalsIgnoreCase("STAT")) {
			if (tokens.length == 2) {
				selectArticle(tokens[1], false, false);
			}
			else if (tokens.length == 1) {
				selectArticle(null, false, false);
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else {
			printStatusLine("500 Command not recognized");
		}

		return false;
	}

	/**
	 * Find the user's OwnIdentity corresponding to the given mail
	 * address.  Use domain part to disambiguate if we have multiple
	 * identities with the same nickname.
	 */
	private FTOwnIdentity getAuthorIdentity(String name, String domain) {
		FTOwnIdentity bestMatch = null;
		boolean matchName = false, matchDomain = false, multiple = false;

		Logger.debug(this, "Received message from " + name + "@" + domain);

		Iterator<FTOwnIdentity> i = mIdentityManager.ownIdentityIterator();
		while (i.hasNext()) {
			FTOwnIdentity identity = i.next();
			if (identity.getNickname().equals(name)) {
				String uid = identity.getUID();
				if (uid.startsWith(domain) || domain.startsWith(uid)) {
					if (matchDomain)
						multiple = true;
					bestMatch = identity;
					matchName = matchDomain = true;
				}
				else if (!matchDomain) {
					if (matchName)
						multiple = true;
					bestMatch = identity;
					matchName = true;
				}
			}
		}

		if (multiple) {
			printStatusLine("441 Multiple identities matching sender");
			return null;
		}
		else if (bestMatch == null) {
			printStatusLine("441 Unknown sender <" + name + "@" + domain + ">");
			return null;
		}
		else
			return bestMatch;
	}

	/**
	 * Handle a command that includes a text data block.
	 */
	private synchronized void finishCommand(String line, ByteBuffer text) {
		ArticleParser parser = new ArticleParser();

		if (!parser.parseMessage(text)) {
			printStatusLine("441 Unable to parse message");
		}
		else {
			FTOwnIdentity myIdentity = getAuthorIdentity(parser.getAuthorName(), parser.getAuthorDomain());
			if (myIdentity == null)
				return;

			synchronized(mMessageManager) {
				HashSet<Board> boardSet = new HashSet<Board>();
				for (Iterator<String> i = parser.getBoards().iterator(); i.hasNext(); ) {
					String name = i.next();
					try {
						Board board = mMessageManager.getBoardByName(name);
						boardSet.add(board);
					}
					catch (NoSuchBoardException e) {
						// FIXME: what to do here?  Create the board,
						// as FMS does?  Silently ignore it?  Reject
						// the message?
					}
				}

				Board replyBoard = null;
				String replyBoardName = parser.getReplyToBoard();
				if (replyBoardName != null) {
					try {
						replyBoard = mMessageManager.getBoardByName(replyBoardName);
						boardSet.add(replyBoard);
					}
					catch (NoSuchBoardException e) {
						// FIXME: same question as above...
					}
				}

				Message parentMessage = null;
				String parentMessageID = parser.getParentID();
				if (parentMessageID != null) {
					try {
						parentMessage = mMessageManager.get(parentMessageID);
					}
					catch (NoSuchMessageException e) {
						// ignore (?)
					}
				}

				try {
					OwnMessage message = mMessageManager.postMessage(parentMessage, boardSet, replyBoard, myIdentity, parser.getTitle(), parser.getText(), null);
					printStatusLine("240 Message posted; ID is <" + message.getID() + ">");
				}
				catch (Exception e) {
					Logger.normal(this, "Error posting message: ", e);
					printStatusLine("441 Posting failed");
				}
			}
		}
	}


	/**
	 * Read an input line (terminated by the ASCII LF character) as a
	 * byte sequence.
	 */
	private ByteBuffer readLineBytes(InputStream is) throws IOException {
		ByteBuffer buf = ByteBuffer.allocateDirect(100);
		int b;

		do {
			b = is.read();
			if (b >= 0) {
				if (!buf.hasRemaining()) {
					// resize input buffer
					ByteBuffer newbuf = ByteBuffer.allocateDirect(buf.capacity() * 2);
					newbuf.put(buf);
					buf = newbuf;
				}
				buf.put((byte) b);
			}
		} while (b >= 0 && b != '\n');

		buf.flip();
		return buf;
	}

	/**
	 * Read a complete text block (terminated by a '.' on a line by
	 * itself).
	 */
	private ByteBuffer readTextDataBytes(InputStream is) throws IOException {
		ByteBuffer buf = ByteBuffer.allocateDirect(1024);
		ByteBuffer line;

		while (true) {
			line = readLineBytes(is);

			if (!line.hasRemaining())
				return null;	// text block not completed --
								// consider message aborted.

			if (line.get(0) == '.') {
				if ((line.remaining() == 2 && line.get(1) == '\n')
					|| (line.remaining() == 3 && line.get(1) == '\r' && line.get(2) == '\n')) {
					buf.flip();
					return buf;
				}
				else {
					// Initial dot must always be skipped (even if the
					// second character isn't a dot)
					line.get();
				}
			}

			// append line to the end of the buffer
			if (line.remaining() > buf.remaining()) {
				ByteBuffer newbuf = ByteBuffer.allocateDirect((buf.position() + line.remaining()) * 2);
				newbuf.put(buf);
				buf = newbuf;
			}
			buf.put(line);
		}
	}


	/**
	 * Main command loop
	 */
	public void run() {
		try {
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			ByteBuffer bytes;
			String line;
			Charset utf8 = Charset.forName("UTF-8");

			out = new PrintStream(os, false, "UTF-8");

			printStatusLine("200 Welcome to Freetalk");
			while (!socket.isClosed()) {
				bytes = readLineBytes(is);
				line = utf8.decode(bytes).toString();
				if (beginCommand(line)) {
					bytes = readTextDataBytes(is);
					finishCommand(line, bytes);
				}
			}
		}
		catch (IOException e) {
			Logger.error(this, "Error in NNTP handler: " + e.getMessage());
		}
	}
}
