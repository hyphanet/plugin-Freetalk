/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.FTIdentityManager;
import plugins.Freetalk.FTMessageManager;
import plugins.Freetalk.FTBoard;
import plugins.Freetalk.FTMessage;
import plugins.Freetalk.Freetalk;

import java.net.Socket;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.Iterator;

import freenet.support.Logger;

/**
 * Represents a connection to a single NNTP client.
 *
 * @author Benjamin Moody
 */
public class FreetalkNNTPHandler implements Runnable {

	private FTIdentityManager mIdentityManager;
	private FTMessageManager mMessageManager;

	private Socket socket;
	private BufferedReader in;
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
		String[] lines = text.split("\r\n?|\n");
		for (int i = 0; i < lines.length; i++) {
			printTextResponseLine(lines[i]);
		}
	}

	/**
	 * Print a single dot to indicate the end of a text response.
	 */
	private void endTextResponse() {
		out.print("." + CRLF);
	}

	/**
	 * Handle the ARTICLE / BODY / HEAD / STAT commands.
	 */
	private void selectArticle(String name, boolean printHead, boolean printBody) {

		// Look up by message ID
		if (name != null && name.length() > 2 && name.charAt(0) == '<'
			&& name.charAt(name.length() - 1) == '>') {

			String msgid = name.substring(1, name.length() - 1);
			FTMessage msg = mMessageManager.get(msgid);

			if (msg == null) {
				printStatusLine("430 No such article");
				return;
			}

			FreetalkNNTPArticle article = new FreetalkNNTPArticle(msg);

			if (printHead && printBody) {
				printStatusLine("220 0 <" + msgid + ">");
				printText(article.getHead());
				printTextResponseLine("");
				printText(article.getBody());
				endTextResponse();
			}
			else if (printHead) {
				printStatusLine("221 0 <" + msgid + ">");
				printText(article.getHead());
				endTextResponse();
			}
			else if (printBody) {
				printStatusLine("222 0 <" + msgid + ">");
				printText(article.getBody());
				endTextResponse();
			}
			else {
				printStatusLine("223 0 <" + msgid + ">");
			}
		}
		else {
			// Other forms of these commands are not (yet) implemented
			printStatusLine("501 Syntax error");
			return;
		}
	}

	/**
	 * Handle the GROUP command.
	 */
	private void selectGroup(String name) {
		// FIXME: look up by "NNTP name"
		FTBoard board = mMessageManager.getBoardByName(name);
		if (board == null) {
			printStatusLine("411 No such group");
		}
		else {
			currentGroup = new FreetalkNNTPGroup(board);
			printStatusLine("211 " + currentGroup.messageCount()
							+ " " + currentGroup.firstMessage()
							+ " " + currentGroup.lastMessage()
							+ " " + board.getNameNNTP());
		}
	}

	/**
	 * Handle the LIST / LIST ACTIVE command.
	 */
	private void listActiveGroups(String pattern) {
		// FIXME: filter by wildmat
		printStatusLine("215 List of newsgroups follows:");
		for (Iterator<FTBoard> i = mMessageManager.boardIterator(); i.hasNext(); ) {
			FTBoard board = i.next();
			FreetalkNNTPGroup group = new FreetalkNNTPGroup(board);
			printTextResponseLine(board.getNameNNTP()
								  + " " + group.lastMessage()
								  + " " + group.firstMessage()
								  + " " + group.postingStatus());
		}
		endTextResponse();
	}

	/**
	 * Handle a command from the client.
	 */
	private synchronized void handleCommand(String line) throws IOException {
		String[] tokens = line.split("[ \t\r\n]+");
		if (tokens.length == 0)
			return;

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
			else {
				printStatusLine("501 Syntax error");
			}
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
	}

	/**
	 * Main command loop
	 */
	public void run() {
		try {
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			String line;

			in = new BufferedReader(new InputStreamReader(is));
			out = new PrintStream(os);

			printStatusLine("200 Welcome to Freetalk");
			while (!socket.isClosed()) {
				line = in.readLine();
				if (line != null)
					handleCommand(line);
			}
		}
		catch (IOException e) {
			Logger.error(this, "Error in NNTP handler: " + e.getMessage());
		}
	}
}
