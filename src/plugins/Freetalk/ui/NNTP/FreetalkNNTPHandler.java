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
import java.net.SocketException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Date;
import java.util.TimeZone;
import java.util.SimpleTimeZone;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

	/** Current message number within the group */
	private int currentMessageNum;

	private final static String CRLF = "\r\n";

	/** Date format used by the DATE command */
	private final static SimpleDateFormat serverDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

	private final static SimpleTimeZone utcTimeZone = new SimpleTimeZone(0, "UTC");

	/** Pattern for matching valid "range" arguments. */
	private final static Pattern rangePattern = Pattern.compile("(\\d+)(-(\\d+)?)?");


	public FreetalkNNTPHandler(Freetalk ft, Socket socket) throws SocketException {
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
	 * Get an iterator for the article or range of articles described
	 * by 'desc'.  (The description may either be null, indicating the
	 * current article; a message ID, enclosed in angle brackets; a
	 * single number, indicating that message number; a number
	 * followed by a dash, indicating an unbounded range; or a number
	 * followed by a dash and a second number, indicating a bounded
	 * range.)  Print an error message if it can't be found.
	 */
	private Iterator<FreetalkNNTPArticle> getArticleRangeIterator(String desc, boolean single) {
		if (desc == null) {
			if (currentGroup == null) {
				printStatusLine("412 No newsgroup selected");
				return null;
			}

			try {
				return currentGroup.getMessageIterator(currentMessageNum, currentMessageNum);
			}
			catch (NoSuchMessageException e) {
				printStatusLine("420 Current article number is invalid");
				return null;
			}
		}
		else if (desc.length() > 2 && desc.charAt(0) == '<'
				 && desc.charAt(desc.length() - 1) == '>') {

			String msgid = desc.substring(1, desc.length() - 1);
			try {
				Message msg = mMessageManager.get(msgid);
				ArrayList<FreetalkNNTPArticle> list = new ArrayList<FreetalkNNTPArticle>();
				list.add(new FreetalkNNTPArticle(msg));
				return list.iterator();
			}
			catch(NoSuchMessageException e) {
				printStatusLine("430 No such article");
				return null;
			}
		}
		else {
			try {
				Matcher matcher = rangePattern.matcher(desc);

				if (!matcher.matches()) {
					printStatusLine("501 Syntax error");
					return null;
				}

				String startStr = matcher.group(1);
				String dashStr = matcher.group(2);
				String endStr = matcher.group(3);

				int start = Integer.parseInt(startStr);
				int end;

				if (dashStr == null)
					end = start;
				else if (endStr == null)
					end = -1;
				else
					end = Integer.parseInt(endStr);

				if (dashStr != null && single) {
					printStatusLine("501 Syntax error");
					return null;
				}

				if (currentGroup == null) {
					printStatusLine("412 No newsgroup selected");
					return null;
				}

				try {
					return currentGroup.getMessageIterator(start, end);
				}
				catch (NoSuchMessageException e) {
					printStatusLine("423 No articles in that range");
					return null;
				}
			}
			catch (NumberFormatException e) {
				printStatusLine("501 Syntax error");
				return null;
			}
		}
	}


	/**
	 * Handle the ARTICLE / BODY / HEAD / STAT commands.
	 */
	private void selectArticle(String desc, boolean printHead, boolean printBody) {
		Iterator<FreetalkNNTPArticle> iter = getArticleRangeIterator(desc, true);

		if (iter == null)
			return;

		FreetalkNNTPArticle article = iter.next();

		if (article.getMessageNum() != 0)
			currentMessageNum = article.getMessageNum();

		if (printHead && printBody) {
			printStatusLine("220 " + article.getMessageNum()
							+ " <" + article.getMessage().getID() + ">");
			printText(article.getHead());
			printTextResponseLine("");
			printText(article.getBody());
			endTextResponse();
		}
		else if (printHead) {
			printStatusLine("221 " + article.getMessageNum()
							+ " <" + article.getMessage().getID() + ">");
			printText(article.getHead());
			endTextResponse();
		}
		else if (printBody) {
			printStatusLine("222 " + article.getMessageNum()
							+ " <" + article.getMessage().getID() + ">");
			printText(article.getBody());
			endTextResponse();
		}
		else {
			printStatusLine("223 " + article.getMessageNum()
							+ " <" + article.getMessage().getID() + ">");
		}
	}

	/**
	 * Handle the GROUP command.
	 */
	private void selectGroup(String name) {
		// FIXME: look up by "NNTP name"
		try {
			String boardName = FreetalkNNTPGroup.groupToBoardName(name);
			Board board = mMessageManager.getBoardByName(boardName);
			currentGroup = new FreetalkNNTPGroup(board);
			synchronized (board) {
				currentMessageNum = currentGroup.firstMessage();
				printStatusLine("211 " + currentGroup.messageCount()
								+ " " + currentGroup.firstMessage()
								+ " " + currentGroup.lastMessage()
								+ " " + currentGroup.getName()); /* FIXME: Implement FreetalkNNTPArticle.getNameNNTP() */
			}
		}
		catch(NoSuchBoardException e) {
			printStatusLine("411 No such group");
		}
	}

	/**
	 * Handle the LISTGROUP command.
	 */
	private void selectGroupWithList(String name, String range) {
		Matcher matcher = rangePattern.matcher(range);

		if (!matcher.matches()) {
			printStatusLine("501 Syntax error");
			return;
		}

		String startStr = matcher.group(1);
		String dashStr = matcher.group(2);
		String endStr = matcher.group(3);

		int start, end;

		try {
			start = Integer.parseInt(startStr);

			if (dashStr == null)
				end = start;
			else if (endStr == null)
				end = -1;
			else
				end = Integer.parseInt(endStr);
		}
		catch (NumberFormatException e) {
			printStatusLine("501 Syntax error");
			return;
		}

		if (name != null) {
			try {
				String boardName = FreetalkNNTPGroup.groupToBoardName(name);
				Board board = mMessageManager.getBoardByName(boardName);
				currentGroup = new FreetalkNNTPGroup(board);
			}
			catch (NoSuchBoardException e) {
				printStatusLine("411 No such group");
				return;
			}
		}
		else if (currentGroup == null) {
			printStatusLine("412 No newsgroup selected");
			return;
		}

		synchronized (currentGroup.getBoard()) {
			currentMessageNum = currentGroup.firstMessage();
			printStatusLine("211 " + currentGroup.messageCount()
							+ " " + currentGroup.firstMessage()
							+ " " + currentGroup.lastMessage()
							+ " " + currentGroup.getName());

			if (end == -1)
				end = currentGroup.lastMessage();

			List<Board.MessageReference> messages = currentGroup.getBoard().getAllMessages();

			for (Iterator<Board.MessageReference> i = messages.iterator(); i.hasNext(); ) {
				int index = i.next().getIndex();
				if (index > end)
					break;
				else if (index >= start)
					printTextResponseLine(Integer.toString(index));
			}

			endTextResponse();
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
			printTextResponseLine(group.getName()
								  + " " + group.lastMessage()
								  + " " + group.firstMessage()
								  + " " + group.postingStatus());
		}
		endTextResponse();
	}

	/**
	 * Handle the LIST NEWSGROUPS command.
	 */
	private void listGroupDescriptions(String pattern) {
		// FIXME: add filtering
		printStatusLine("215 Information follows:");
		for (Iterator<Board> i = mMessageManager.boardIterator(); i.hasNext(); ) {
			Board board = i.next();
			String groupName = FreetalkNNTPGroup.boardToGroupName(board.getName());
			printTextResponseLine(groupName	+ " " + board.getDescription(null));
		}
		endTextResponse();
	}

	/**
	 * Handle the NEWGROUPS command.
	 */
	private void listNewGroupsSince(String datestr, String format, boolean gmt) {
		SimpleDateFormat df = new SimpleDateFormat(format);
		if (gmt)
			df.setTimeZone(TimeZone.getTimeZone("UTC"));

		Date date = df.parse(datestr, new ParsePosition(0));
		for (Iterator<Board> i = mMessageManager.boardIteratorSortedByDate(date); i.hasNext(); ) {
			Board board = i.next();
			FreetalkNNTPGroup group = new FreetalkNNTPGroup(board);
			printTextResponseLine(board.getName()
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
		Iterator<FreetalkNNTPArticle> iter = getArticleRangeIterator(articleDesc, false);

		if (iter != null) {
			printStatusLine("224 Header contents follow");
			while (iter.hasNext()) {
				FreetalkNNTPArticle article = iter.next();

				if (header.equalsIgnoreCase(":bytes"))
					printTextResponseLine(article.getMessageNum() + " " + article.getByteCount());
				else if (header.equalsIgnoreCase(":lines"))
					printTextResponseLine(article.getMessageNum() + " " + article.getBodyLineCount());
				else
					printTextResponseLine(article.getMessageNum() + " " + article.getHeaderByName(header));
			}
			endTextResponse();
		}
	}

	/**
	 * Handle the OVER / XOVER command.
	 */
	private void printArticleOverview(String articleDesc) {
		Iterator<FreetalkNNTPArticle> iter = getArticleRangeIterator(articleDesc, false);

		if (iter != null) {
			printStatusLine("224 Overview follows");
			while (iter.hasNext()) {
				FreetalkNNTPArticle article = iter.next();

				printTextResponseLine(article.getMessageNum() + "\t" + article.getHeader(FreetalkNNTPArticle.Header.SUBJECT)
									  + "\t" + article.getHeader(FreetalkNNTPArticle.Header.FROM)
									  + "\t" + article.getHeader(FreetalkNNTPArticle.Header.DATE)
									  + "\t" + article.getHeader(FreetalkNNTPArticle.Header.MESSAGE_ID)
									  + "\t" + article.getHeader(FreetalkNNTPArticle.Header.REFERENCES)
									  + "\t" + article.getByteCount()
									  + "\t" + article.getBodyLineCount());
			}
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
	 * Handle the DATE command.
	 */
	private void printDate() {
		Date date = new Date();
		synchronized (serverDateFormat) {
			serverDateFormat.setTimeZone(utcTimeZone);
			printTextResponseLine("111 " + serverDateFormat.format(date));
		}
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
		else if (command.equalsIgnoreCase("DATE")) {
			printDate();
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
			else if (tokens[1].equalsIgnoreCase("NEWSGROUPS")) {
				if (tokens.length > 2)
					listGroupDescriptions(tokens[2]);
				else
					listGroupDescriptions(null);
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
		else if (command.equalsIgnoreCase("LISTGROUP")) {
			if (tokens.length == 1) {
				selectGroupWithList(null, "1-");
			}
			else if (tokens.length == 2) {
				selectGroupWithList(tokens[1], "1-");
			}
			else if (tokens.length == 3) {
				selectGroupWithList(tokens[1], tokens[2]);
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else if (command.equalsIgnoreCase("NEWGROUPS")) {
			boolean gmt = false;
			if ((tokens.length == 4 && (gmt = tokens[3].equalsIgnoreCase("GMT"))) ||
					(tokens.length == 3))
			{
				String date = tokens[1] + " " + tokens[2];
				if (date.length() == 15) {
					listNewGroupsSince(date, "yyyyMMdd HHmmss", gmt);
				}
				else if (date.length() == 13) {
					listNewGroupsSince(date, "yyMMdd HHmmss", gmt);
				}
				else {
					printStatusLine("501 Syntax error");
				}
			}
			else {
				printStatusLine("501 Syntax error");
			}
		}
		else if (command.equalsIgnoreCase("MODE")) {
			if (tokens.length == 2 && tokens[1].equalsIgnoreCase("READER")) {
				printStatusLine("200 Reader mode acknowledged, posting allowed");
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
			/* FIXME: This happens when trying to send a reply to a message with Thunderbird */
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
		/* FIXME: Implement the login command. People with a newsreader which always tries to login will receive command not recognized
		 * and therefore cannot use NNTP. */
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
				try {
					Message parentMessage;
					try {
						parentMessage = mMessageManager.get(parser.getParentID());
					}
					catch (NoSuchFieldException e) {
						parentMessage = null;
					}
					catch (NoSuchMessageException e) {
						parentMessage = null;
					}

					HashSet<String> boardSet = new HashSet<String>(parser.getBoards());
					OwnMessage message = mMessageManager.postMessage(parentMessage, boardSet, parser.getReplyToBoard(), myIdentity, parser.getTitle(), parser.getText(), null);
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
					buf.flip();
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
				buf.flip();
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
