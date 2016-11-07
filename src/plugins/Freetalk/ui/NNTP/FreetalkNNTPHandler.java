/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import plugins.Freetalk.Board;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.MessageURI;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;

/**
 * Represents a connection to a single NNTP client.
 *
 * @author Benjamin Moody
 * @author bback
 * @author xor (xor@freenetproject.org)
 * 
 * TODO: allow board subscribe by NNTP server
 */
public final class FreetalkNNTPHandler implements Runnable {

    private final IdentityManager mIdentityManager;
    private final MessageManager mMessageManager;

    private final Socket mSocket;
    private BufferedWriter mOutput;

    /** Current board (selected by the GROUP command) */
    private FreetalkNNTPGroup mCurrentGroup;

    /** Current message number within the group */
    private int mCurrentMessageNum;
    
    /** Authenticated OwnIdentity **/
    private OwnIdentity mAuthenticatedUser = null;

    
    /** Line ending required by NNTP **/
    private static final String CRLF = "\r\n";
    
    /** Date format used by the DATE command */
    private static final SimpleDateFormat serverDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private static final SimpleTimeZone utcTimeZone = new SimpleTimeZone(0, "UTC");

    /** Pattern for matching valid "range" arguments. */
    private static final Pattern rangePattern = Pattern.compile("(\\d+)(-(\\d+)?)?");
    
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(FreetalkNNTPHandler.class);
	}


    public FreetalkNNTPHandler(final Freetalk ft, final Socket socket) throws SocketException {
        mIdentityManager = ft.getIdentityManager();
        mMessageManager = ft.getMessageManager();
        this.mSocket = socket;
    }

    /**
     * Check if handler is still active.
     */
    public boolean isAlive() {
        return !mSocket.isClosed(); // This is synchronized
    }

    /**
     * Close the connection to the client immediately.
     */
    public synchronized void terminate() {
        try {
            mSocket.close();
        }
        catch (IOException e) {
            // ignore
        }
    }
    
    /**
     * Print out a status response (numeric code plus additional
     * information.)
     */
    private void printStatusLine(final String line) throws IOException {
        mOutput.write(line);
        // NNTP spec requires all command and response lines end with CR+LF
        mOutput.write(CRLF);
        mOutput.flush();
    }

    /**
     * Print out a text response line.  The line will be "dot-stuffed"
     * if necessary (any line beginning with a dot will have a second
     * dot prepended.)
     */
    private void printTextResponseLine(final String line) throws IOException {
        if (line.length() > 0 && line.charAt(0) == '.') {
            mOutput.write(".");
        }
        mOutput.write(line);
        mOutput.write(CRLF);
    }
    
    /**
     * Print a single dot to indicate the end of a text response.
     */
    private void endTextResponse() throws IOException {
        mOutput.write(".");
        mOutput.write(CRLF);
        mOutput.flush();
    }

    /**
     * Print out a block of text (changing all line terminators to
     * CR+LF and dot-stuffing as necessary.)
     */
    private void printText(final String text) throws IOException {
        String[] lines = FreetalkNNTPArticle.mEndOfLinePattern.split(text);
        for (int i = 0; i < lines.length; i++) {
            printTextResponseLine(lines[i]);
        }
    }

    /**
     * Get an iterator for the article or range of articles described
     * by 'desc'.  (The description may either be null, indicating the
     * current article; a message ID, enclosed in angle brackets; a
     * single number, indicating that message number; a number
     * followed by a dash, indicating an unbounded range; or a number
     * followed by a dash and a second number, indicating a bounded
     * range.)  Print an error message if it can't be found.
     * 
     * You have to embed the call to this function and processing of the returned Iterator in a synchronized(mCurrentGroup.getBoard())!
     */
    private Iterator<FreetalkNNTPArticle> getArticleRangeIterator(final String desc, final boolean single) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return null;
		}
        if (desc == null) {
            if (mCurrentGroup == null) {
                printStatusLine("412 No newsgroup selected");
                return null;
            }

            try {
                return mCurrentGroup.getMessageIterator(mCurrentMessageNum, mCurrentMessageNum);
            }
            catch (NoSuchMessageException e) {
                printStatusLine("420 Current article number is invalid");
                return null;
            }
        }
        else if (desc.length() > 2 && desc.charAt(0) == '<' && desc.charAt(desc.length() - 1) == '>') {

        	final String msgid = desc.substring(1, desc.length() - 1);
            try {
            	final Message msg = mMessageManager.get(msgid);
            	final ArrayList<FreetalkNNTPArticle> list = new ArrayList<FreetalkNNTPArticle>(2);
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
            	final Matcher matcher = rangePattern.matcher(desc);

                if (!matcher.matches()) {
                    printStatusLine("501 Syntax error");
                    return null;
                }

                final String startStr = matcher.group(1);
                final String dashStr = matcher.group(2);
                final String endStr = matcher.group(3);

                final int start = Integer.parseInt(startStr);
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

                if (mCurrentGroup == null) {
                    printStatusLine("412 No newsgroup selected");
                    return null;
                }

                try {
                    return mCurrentGroup.getMessageIterator(start, end);
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
    private void selectArticle(final String desc, final boolean printHead, final boolean printBody) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return;
		}
		
        if (mCurrentGroup == null) {
            printStatusLine("412 No newsgroup selected");
            return;
        }
        synchronized(mCurrentGroup.getBoard())  {
		final Iterator<FreetalkNNTPArticle> iter = getArticleRangeIterator(desc, true);

        if (iter == null)
            return;

        final FreetalkNNTPArticle article = iter.next();

        if (article.getMessageNum() != 0)
            mCurrentMessageNum = article.getMessageNum();

        if (printHead && printBody) {
            printStatusLine("220 " + article.getMessageNum() + " <" + article.getMessage().getID() + ">");
            printText(article.getHead());
            printTextResponseLine("");
            printText(article.getBody());
            endTextResponse();
        }
        else if (printHead) {
            printStatusLine("221 " + article.getMessageNum() + " <" + article.getMessage().getID() + ">");
            printText(article.getHead());
            endTextResponse();
        }
        else if (printBody) {
            printStatusLine("222 " + article.getMessageNum() + " <" + article.getMessage().getID() + ">");
            printText(article.getBody());
            endTextResponse();
        }
        else {
            printStatusLine("223 " + article.getMessageNum() + " <" + article.getMessage().getID() + ">");
        }
        }
    }

    /**
     * Handle the GROUP command.
     */
    private void selectGroup(final String name) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return;
		}
        try {
        	final String boardName = FreetalkNNTPGroup.groupToBoardName(name);
        	final SubscribedBoard board = mMessageManager.getSubscription(mAuthenticatedUser, boardName);
            mCurrentGroup = new FreetalkNNTPGroup(board);
            synchronized (board) {
                mCurrentMessageNum = mCurrentGroup.firstMessage();
                printStatusLine("211 " + mCurrentGroup.messageCount()
                        + " " + mCurrentGroup.firstMessage()
                        + " " + mCurrentGroup.lastMessage()
                        + " " + mCurrentGroup.getGroupName());
            }
        }
        catch(NoSuchBoardException e) {
            printStatusLine("411 No such group");
        }
    }

    /**
     * Handle the LISTGROUP command.
     */
    private void selectGroupWithList(final String name, final String range) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return;
		}
		final Matcher matcher = rangePattern.matcher(range);

        if (!matcher.matches()) {
            printStatusLine("501 Syntax error");
            return;
        }

        final String startStr = matcher.group(1);
        final String dashStr = matcher.group(2);
        final String endStr = matcher.group(3);

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
            	final String boardName = FreetalkNNTPGroup.groupToBoardName(name);
            	final SubscribedBoard board = mMessageManager.getSubscription(mAuthenticatedUser, boardName);
                mCurrentGroup = new FreetalkNNTPGroup(board);
            }
            catch (NoSuchBoardException e) {
                printStatusLine("411 No such group");
                return;
            }
        }
        else if (mCurrentGroup == null) {
            printStatusLine("412 No newsgroup selected");
            return;
        }

        synchronized (mCurrentGroup.getBoard()) {
            mCurrentMessageNum = mCurrentGroup.firstMessage();
            printStatusLine("211 " + mCurrentGroup.messageCount()
                    + " " + mCurrentGroup.firstMessage()
                    + " " + mCurrentGroup.lastMessage()
                    + " " + mCurrentGroup.getGroupName());

            if (end == -1)
                end = mCurrentGroup.lastMessage();

            // TODO: Optimization: Write a getAllMessages() which allows the specification of a range!
            for(final SubscribedBoard.BoardMessageLink ref : mCurrentGroup.getBoard().getAllMessages(true)) {
                final int index = ref.getIndex();
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
    private void listActiveGroups(final String pattern) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return;
		}
        // TODO: filter by wildmat
        printStatusLine("215 List of newsgroups follows:");

        synchronized(mMessageManager) {
        // TODO: Optimization: Use a non sorting function
        for (final SubscribedBoard board : mMessageManager.subscribedBoardIteratorSortedByName(mAuthenticatedUser)) {
        	final FreetalkNNTPGroup group = new FreetalkNNTPGroup(board);
            printTextResponseLine(group.getGroupName()
                    + " " + group.lastMessage()
                    + " " + group.firstMessage()
                    + " " + group.postingStatus());
        }
        }
        endTextResponse();
    }

    /**
     * Handle the LIST NEWSGROUPS command.
     */
    private void listGroupDescriptions(final String pattern) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return;
		}
        // TODO: add filtering
        printStatusLine("215 Information follows:");
        synchronized(mMessageManager) {
        for (final Board board : mMessageManager.boardIteratorSortedByName()) { // TODO: Optimization: Use a non-sorting function.
            final String groupName = FreetalkNNTPGroup.boardToGroupName(board.getName());
            printTextResponseLine(groupName	+ " " + board.getDescription(mAuthenticatedUser));
        }
        }
        endTextResponse();
    }

    /**
     * Handle the NEWGROUPS command.
     */
    private void listNewGroupsSince(final String datestr, final String format, final boolean gmt) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return;
		}
		final SimpleDateFormat df = new SimpleDateFormat(format);
        if (gmt)
            df.setTimeZone(TimeZone.getTimeZone("UTC"));

        printStatusLine("231 List of new newsgroups follows");
        final Date date = df.parse(datestr, new ParsePosition(0));
        synchronized(mMessageManager) {
        for (SubscribedBoard board : mMessageManager.subscribedBoardIteratorSortedByDate(mAuthenticatedUser, date)) {
            final FreetalkNNTPGroup group = new FreetalkNNTPGroup(board);
            printTextResponseLine(board.getName()
                    + " " + group.lastMessage()
                    + " " + group.firstMessage()
                    + " " + group.postingStatus());
        }
        }
        endTextResponse();
    }

    /**
     * Handle the HDR / XHDR command.
     */
    private void printArticleHeader(final String header, final String articleDesc) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return;
		}
		
        if (mCurrentGroup == null) {
            printStatusLine("412 No newsgroup selected");
            return;
        }
        
		synchronized(mCurrentGroup.getBoard()) {
			final Iterator<FreetalkNNTPArticle> iter = getArticleRangeIterator(articleDesc, false);

			if (iter == null)
				return;
        
            printStatusLine("224 Header contents follow");
            while (iter.hasNext()) {
            	final FreetalkNNTPArticle article = iter.next();

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
    private void printArticleOverview(final String articleDesc) throws IOException {
		if (mAuthenticatedUser == null) {
			printStatusLine("480 Authentification required");
			return;
		}
		
        if (mCurrentGroup == null) {
            printStatusLine("412 No newsgroup selected");
            return;
        }
		
        synchronized(mCurrentGroup.getBoard()) {
        	final Iterator<FreetalkNNTPArticle> iter = getArticleRangeIterator(articleDesc, false);

        	if (iter == null)
        		return;
        
            printStatusLine("224 Overview follows");
            while (iter.hasNext()) {
            	final FreetalkNNTPArticle article = iter.next();

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
    private void printHeaderList() throws IOException {
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
    private void printOverviewFormat() throws IOException {
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
     * Handle the AUTHINFO command, authenticate provided own identity.
     * For USER we expect the Freetalk address. We extract the identity ID and lookup it.
     * 
     * @param subcmd  Must be USER or PASS  (PASS not yet supported!)
     * @param value   Value of subcmd
     */
    private void handleAuthInfo(final String subcmd, final String value) throws IOException {
        /*
         * For AUTHINFO example see here: http://tools.ietf.org/html/rfc4643#section-2.3.3
         */
        
        // For now, we don't require a PASS
        if (!subcmd.equalsIgnoreCase("USER")) {
            printStatusLine("502 Command unavailable");
            return;
        }

        // already authenticated?
        if (mAuthenticatedUser != null) {
            printStatusLine("502 Command unavailable");
            return;
        }
        
        OwnIdentity oi = null;
        try {
        	final String id = IdentityManager.extractIdFromFreetalkAddress(value);
            oi = mIdentityManager.getOwnIdentity(id);
        } catch (NoSuchIdentityException e) {
        }
        
        if (oi == null) {
            printStatusLine("481 Authentication failed");
        } else {
            printStatusLine("281 Authentication accepted");
            mAuthenticatedUser = oi; // assign authenticated id
        }
    }
    
    /**
     * Handle the CAPABILITIES command.
     */
    private void printCapabilities() throws IOException {
        printStatusLine("101 Capability list:");
        printText("VERSION 2");
        if (mAuthenticatedUser == null) {
            printText("AUTHINFO USER"); // we allow this on unsecured connections
        }
        printText("READER");
        printText("POST");
        printText("HDR");
        printText("OVER MSGID");
        printText("LIST ACTIVE NEWSGROUPS HEADERS OVERVIEW.FMT");
        endTextResponse();
    }
    
    /**
     * Handle the DATE command.
     */
    private void printDate() throws IOException {
    	final Date date = CurrentTimeUTC.get();
        synchronized (serverDateFormat) {
            serverDateFormat.setTimeZone(utcTimeZone);
            printTextResponseLine("111 " + serverDateFormat.format(date));
        }
        mOutput.flush();
    }
    
    /**
     * Handle a command from the client.  If the command requires a
     * text data section, this function returns true (and
     * finishCommand should be called after the text has been
     * received.)
     */
    private synchronized boolean beginCommand(final String line) throws IOException {
    	final String[] tokens = line.split("[ \t\r\n]+");
        if (tokens.length == 0)
            return false;

        final String command = tokens[0];

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
        else if (command.equalsIgnoreCase("AUTHINFO")) {
            if (tokens.length == 3) {
                handleAuthInfo(tokens[1], tokens[2]);
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
        else if (command.equalsIgnoreCase("CAPABILITIES")) {
            printCapabilities();
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
            /* This happens when trying to send a reply to a message with Thunderbird */
            /* Message arrives in finishCommand() */
            printStatusLine("340 Please send article to be posted");
            return true;
        }
        else if (command.equalsIgnoreCase("QUIT")) {
            printStatusLine("205 Have a nice day.");
            mSocket.close();
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
            Logger.warning(this, "Command not recognized: " + command);
        }

        return false;
    }

    /**
     * Handle a command that includes a text data block.
     */
    private synchronized void finishCommand(String line, ByteBuffer text) throws IOException {
    	final ArticleParser parser = new ArticleParser();

        if (!parser.parseMessage(text)) {
            printStatusLine("441 Unable to parse message");
        }
        else {
            // Freetalk address used during AUTH must match the email provided with POST
            String freetalkAddress = parser.getAuthorName() + "@" + parser.getAuthorDomain();
            if (freetalkAddress == null || mAuthenticatedUser == null || !freetalkAddress.equals(mAuthenticatedUser.getFreetalkAddress())) {
                Logger.normal(this, "Error posting message, invalid email address: " + freetalkAddress);
                printStatusLine("441 Posting failed, invalid email address");
                return;
            }

            // TODO: Optimization: We don't need this lock probably, see Javadoc of postMessage
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
                    
                	// FIXME: When replying to forked threads, this code will always sent the replies to the original thread. We need to find a way
                	// to figure out whether the user wanted to reply to a forked thread - does NNTP pass a thread ID?
                    
                    MessageURI parentMessageURI = null;
                    if (parentMessage != null) {
                        parentMessageURI = parentMessage.isThread() ? parentMessage.getURI() : parentMessage.getThreadURI();
                    }

                    final HashSet<String> boardSet = new HashSet<String>(parser.getBoards());
                    final OwnMessage message = mMessageManager.postMessage(parentMessageURI,
                    		parentMessage, boardSet, parser.getReplyToBoard(), mAuthenticatedUser, parser.getTitle(), parser.getDate(), parser.getText(), null);
                    printStatusLine("240 Message posted; ID is <" + message.getID() + ">");
                }
                catch (Exception e) {
                    Logger.error(this, "Error posting message: ", e);
                    printStatusLine("441 Posting failed");
                }
            }
        }
    }


    /**
     * Read an input line (terminated by the ASCII LF character) as a
     * byte sequence.
     */
    private ByteBuffer readLineBytes(final InputStream is) throws IOException {
    	// A minimal size > 0 is needed because is.available() might return 0.
        ByteBuffer buf = ByteBuffer.allocate(Math.max(4096, Math.min(is.available(), 64*1024)));
        int b;
        
        do {
            b = is.read();
            if (b >= 0) {
                if (!buf.hasRemaining()) {
                    // resize input buffer
                    ByteBuffer newbuf = ByteBuffer.allocate(buf.capacity() * 2);
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
    private ByteBuffer readTextDataBytes(final InputStream is) throws IOException {
    	// A minimal size > 0 is needed because is.available() might return 0.
        ByteBuffer buf = ByteBuffer.allocate(Math.max(4096, Math.min(is.available(), 64*1024)));
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
                ByteBuffer newbuf = ByteBuffer.allocate((buf.position() + line.remaining()) * 2);
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
    @Override public void run() {
        try {
        	final InputStream is = mSocket.getInputStream();
            mOutput = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream(), "UTF-8"), 8192);
            
            Charset utf8 = Charset.forName("UTF-8");

            printStatusLine("200 Welcome to Freetalk");
            while (!mSocket.isClosed()) {
                final String line = utf8.decode(readLineBytes(is)).toString();
                
                if(logDEBUG) Logger.debug(this, "Received NNTP command: " + line);
                
                synchronized(this) {
	                if (beginCommand(line)) {
	                    finishCommand(line, readTextDataBytes(is));
	                }
                }
            }
        }
        catch (Throwable e) {
            Logger.error(this, "Error in NNTP handler, closing socket: " + e.getMessage(), e);
            try {
                mSocket.close();
            } catch (IOException e1) {
            }
        }
    }
}
