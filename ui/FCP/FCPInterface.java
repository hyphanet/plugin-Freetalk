/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.FCP;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

/**
 * FCP interface implementation for Freetalk.
 *
 *  NOTE: The interface is currently UNSTABLE, use it only for development purposes!
 *
 * FCP message format:
 *   Command=name
 *   ...
 */
public final class FCPInterface implements FredPluginFCP {

    private final Freetalk mFreetalk;

    public FCPInterface(final Freetalk myFreetalk) {
        mFreetalk = myFreetalk;
    }

    /**
     * @param replysender interface to send a reply
     * @param params parameters passed in, can be null
     * @param data a bucket of data passed in, can be null
     * @param access 0: direct call (plugin to plugin), 1: FCP restricted access,  2: FCP full access
     */
    public void handle(final PluginReplySender replysender, final SimpleFieldSet params, final Bucket data, final int accesstype) {
        try {
            if (params == null) {
                throw new Exception("Empty message received");
            }

            final String message = params.get("Message");
            if (message == null || message.trim().length() == 0) {
                throw new Exception("Specified message is empty");
            }

            if (message.equals("Ping")) {
                handlePing(replysender, params);
            } else if (message.equals("ListBoards")) {
                handleListBoards(replysender, params);
            } else if (message.equals("ListOwnIdentities")) {
                handleListOwnIdentities(replysender, params);
            } else if (message.equals("ListKnownIdentities")) {
                handleListKnownIdentities(replysender, params);
            } else if (message.equals("ListThreads")) {
                handleListThreads(replysender, params);
            } else if (message.equals("ListMessages")) {
                handleListMessages(replysender, params);
            } else if (message.equals("GetMessage")) {
                handleGetMessage(replysender, params);
            } else if (message.equals("CreateBoard")) {
                handleCreateBoard(replysender, params);
            } else {
                throw new Exception("Unknown message (" + message + ")");
            }
        } catch (final Exception e) {
            Logger.error(this, e.toString());
            try {
                if (!(e instanceof PluginNotFoundException)) {
                    replysender.send(errorMessageFCP(params.get("Message"), e), data);
                }
            } catch (final PluginNotFoundException e1) {
                Logger.normal(this, "Connection to request sender lost", e1);
            }
        }
    }

    /**
     * Handle ListBoards command.
     * Send a number of Board messages and finally an EndListBoards message.
     * Format:
     *   Message=Board
     *   Name=name
     *   MessageCount=123
     *   FirstSeenDate=utcMillis      (optional)
     *   LatestMessageDate=utcMillis  (optional)
     */
    private void handleListBoards(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
        synchronized(mFreetalk.getMessageManager()) {
            final Iterator<Board> boards = mFreetalk.getMessageManager().boardIterator();
            while(boards.hasNext()) {
                final Board board = boards.next();

                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "Board");
                sfs.putOverwrite("Name", board.getName());
                sfs.put("MessageCount", board.messageCount());
                if (board.getFirstSeenDate() != null) {
                    sfs.put("FirstSeenDate", board.getFirstSeenDate().getTime());
                }
                if (board.getLatestMessageDate() != null) {
                    sfs.put("LatestMessageDate", board.getLatestMessageDate().getTime());
                }

                replysender.send(sfs);
            }
        }

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListBoards");
        replysender.send(sfs);
    }

    /**
     * Handle ListThreads command.
     * Send a number of KnownIdentity messages and finally an EndListKnownIdentities message.
     * Format of request:
     *   Message=ListThreads
     *   BoardName=abc
     *   OwnIdentityUID=UID
     * Format of reply:
     *   Message=MessageThread
     *   ID=id
     *   Title=title
     *   Author=freetalkAddr
     *   Date=utcMillis
     *   ReplyCount=123
     *   FetchDate=utcMillis
     *   IsThread=true|false  (all returned messages should be thread root messages)
     */
    private void handleListThreads(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchBoardException, NoSuchIdentityException
    {
        // TODO: allow to request threads with all thread messages, with or without message text

        final String boardName = params.get("BoardName");
        if (boardName == null) {
            throw new InvalidParameterException("Boardname parameter not specified");
        }
        final String ownIdentityUID = params.get("OwnIdentityUID");
        if (ownIdentityUID == null) {
            throw new InvalidParameterException("OwnIdentityUID parameter not specified");
        }

        final FTOwnIdentity ownIdentity = mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityUID); // throws exception when not found
        final Board board = mFreetalk.getMessageManager().getBoardByName(boardName); // throws exception when not found

        synchronized(board) { /* FIXME: Is this enough synchronization or should we lock the message manager? */
            final Iterator<MessageReference> threads = board.threadIterator(ownIdentity);
            while(threads.hasNext()) {
                final Message thread = threads.next().getMessage();

                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "MessageThread");
                sfs.putOverwrite("ID", thread.getID());
                sfs.putOverwrite("Title", thread.getTitle());
                sfs.putOverwrite("Author", thread.getAuthor().getFreetalkAddress());
                sfs.put("Date", thread.getDate().getTime());
                sfs.put("ReplyCount", board.threadReplyCount(ownIdentity, thread));
                sfs.put("FetchDate", thread.getFetchDate().getTime());
                sfs.put("IsThread", thread.isThread());
                replysender.send(sfs);
            }
        }

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListThreads");
        replysender.send(sfs);
    }

    /**
     * Handle ListMessages command.
     * Send a number of Message messages and finally an EndListMessages message.
     * Format of request:
     *   Message=ListMessages
     *   BoardName=abc
     *   ThreadID=ID                     (optional, if not specified retrieves all Messages of Board)
     *   EarliestFetchDate=utcMillis     (optional, default is 0)
     *   IncludeMessageText=true|false   (optional, default is false)
     * Format of reply: see sendSingleMessage()
     */
    private void handleListMessages(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchBoardException, NoSuchMessageException,
    UnsupportedEncodingException
    {
        final String boardName = params.get("BoardName");
        if (boardName == null) {
            throw new InvalidParameterException("Boardname parameter not specified");
        }
        final String threadID = params.get("ThreadID");

        long earliestFetchDate;
        try {
            earliestFetchDate = Long.parseLong(params.get("EarliestFetchDate"));
        } catch(final NumberFormatException e) {
            earliestFetchDate = 0;
        }
        final boolean includeMessageText = Boolean.parseBoolean(params.get("IncludeMessageText"));

        final Board board = mFreetalk.getMessageManager().getBoardByName(boardName); // throws exception when not found

        synchronized(board) {  /* FIXME: Is this enough synchronization or should we lock the message manager? */

            final List<MessageReference> messageRefList;
            if (threadID == null) {
                messageRefList = board.getAllMessages();
            } else {
                final Message thread = mFreetalk.getMessageManager().get(threadID); // throws exception when not found
                if (thread.getFetchDate().getTime() >= earliestFetchDate) {
                    sendSingleMessage(replysender, thread, includeMessageText);
                }
                messageRefList = board.getAllThreadReplies(thread);
            }

            for(final MessageReference reference : messageRefList) {
                final Message msg = reference.getMessage();
                if (msg.getFetchDate().getTime() >= earliestFetchDate) {
                    sendSingleMessage(replysender, msg, includeMessageText);
                }
            }
        }

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListMessages");
        replysender.send(sfs);
    }

    /**
     * Handle GetMessage command.
     * Send the requested Message.
     * Format of request:
     *   Message=GetMessage
     *   BoardName=abc
     *   MessageID=ID
     *   IncludeMessageText=true|false   (optional, default is false)
     * Format of reply: see sendSingleMessage()
     * Reply when messageID or boardName is not found:
     *   Message=Error
     *   OriginalMessage=GetMessage
     *   Description=Unknown message ID abc
     *   OR
     *   Description=Unknown board: abc
     */
    private void handleGetMessage(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchBoardException, NoSuchMessageException,
    UnsupportedEncodingException
    {
        final String boardName = params.get("BoardName");
        if (boardName == null) {
            throw new InvalidParameterException("Boardname parameter not specified");
        }
        final String messageID = params.get("MessageID");
        if (messageID == null) {
            throw new InvalidParameterException("MessageID parameter not specified");
        }
        final boolean includeMessageText = Boolean.parseBoolean(params.get("IncludeMessageText"));

        final Message message = mFreetalk.getMessageManager().get(messageID); // throws exception when not found

        sendSingleMessage(replysender, message, includeMessageText);
    }

    /**
     * Sends a single message.
     *
     * Format of reply:
     *   Message=Message
     *   ID=id
     *   Title=title
     *   Author=freetalkAddr
     *   Date=utcMillis
     *   FetchDate=utcMillis
     *   IsThread=true|false
     *   ParentID=id         (optional)
     *   (following is only sent when IncludeMessageText=true)
     *   DataLength=123      (NOTE: no leading 'Replies.'!)
     *   Data                (NOTE: no leading 'Replies.'!)
     *   <123 bytes of utf8 text>
     *   (no EndMessage!)
     */
    private void sendSingleMessage(final PluginReplySender replysender, final Message message, final boolean includeMessageText)
    throws PluginNotFoundException, UnsupportedEncodingException
    {
        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Message");
        sfs.putOverwrite("ID", message.getID());
        sfs.putOverwrite("Title", message.getTitle());
        sfs.putOverwrite("Author", message.getAuthor().getFreetalkAddress());
        sfs.put("Date", message.getDate().getTime());
        sfs.put("FetchDate", message.getFetchDate().getTime());
        sfs.put("IsThread", message.isThread());
        try {
            sfs.putOverwrite("ParentID", message.getParentID());
        } catch(final NoSuchMessageException e) {
        }
        if (includeMessageText
                && message.getText() != null
                && message.getText().length() > 0)
        {
            // sending data sets 'DataLength' and 'Data' without preceeding 'Replies.'
            replysender.send(sfs, message.getText().getBytes("UTF-8"));
        } else {
            replysender.send(sfs);
        }
    }

    /**
     * Handle ListKnownIdentities command.
     * Send a number of KnownIdentity messages and finally an EndListKnownIdentities message.
     * Format:
     *   Message=KnownIdentity
     *   UID=uid
     *   Nickname=name
     *   FreetalkAddress=freetalkAddr
     */
    private void handleListKnownIdentities(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
        for(final FTIdentity id : mFreetalk.getIdentityManager().getAllIdentities()) {
            if (id instanceof FTOwnIdentity) {
                continue;
            }
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "KnownIdentity");
            sfs.putOverwrite("UID", id.getUID());
            sfs.putOverwrite("Nickname", id.getNickname());
            sfs.putOverwrite("FreetalkAddress", id.getFreetalkAddress());
            replysender.send(sfs);
        }

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListKnownIdentities");
        replysender.send(sfs);
    }

    /**
     * Handle ListOwnIdentities command.
     * Send a number of OwnIdentity messages and finally an EndListOwnIdentities message.
     * Format:
     *   Message=OwnIdentity
     *   UID=uid
     *   Nickname=name
     *   FreetalkAddress=freetalkAddr
     */
    private void handleListOwnIdentities(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
        final Iterator<FTOwnIdentity> ownIdentities = mFreetalk.getIdentityManager().ownIdentityIterator();
        while (ownIdentities.hasNext()) {
            final FTOwnIdentity id = ownIdentities.next();

            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "OwnIdentity");
            sfs.putOverwrite("UID", id.getUID());
            sfs.putOverwrite("Nickname", id.getNickname());
            sfs.putOverwrite("FreetalkAddress", id.getFreetalkAddress());
            replysender.send(sfs);
        }

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListOwnIdentities");
        replysender.send(sfs);
    }

    /**
     * Handle CreateBoard command.
     * Creates a new board with name. The name must be valid, see Board() constructor.
     * Format of request:
     *   Message=CreateBoard
     *   BoardName=abc
     * Format of reply:
     *   Message=CreateBoardReply
     *   BoardCreated=true|false
     *   ErrorDescription=abc    (set when BoardCreated=false)
     *   StoredBoardName=abc     (set when BoardCreated=true)
     */
    private void handleCreateBoard(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException
    {
        final String boardName = params.get("BoardName");
        if (boardName == null) {
            throw new InvalidParameterException("Boardname parameter not specified");
        }

        Board board;
        synchronized(mFreetalk.getMessageManager()) {

            try {
                board = mFreetalk.getMessageManager().getBoardByName(boardName);
            } catch (final NoSuchBoardException e) {
                board = null;
            }

            if (board != null) {
                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "CreateBoardReply");
                sfs.putOverwrite("BoardCreated", "false");
                sfs.putOverwrite("ErrorDescription", "Board with same name already exists.");
                replysender.send(sfs);
                return;
            }

            try {
                board = mFreetalk.getMessageManager().getOrCreateBoard(boardName);
            } catch (final InvalidParameterException e) {
                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "CreateBoardReply");
                sfs.putOverwrite("BoardCreated", "false");
                sfs.putOverwrite("ErrorDescription", e.getLocalizedMessage());
                replysender.send(sfs);
                return;
            }
        }

        // board can't be null when we come here
        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "CreateBoardReply");
        sfs.putOverwrite("BoardCreated", "true");
        sfs.putOverwrite("StoredBoardName", board.getName());
        replysender.send(sfs);
    }

    /**
     * Simple Ping command handler. Returns a Pong.
     * Format:
     *   Message=Pong
     *   CurrentTime=utcMillis
     */
    private void handlePing(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Pong");
        sfs.put("CurrentTime", System.currentTimeMillis());
        replysender.send(sfs);
    }

    /**
     * Sends an error message to the client.
     * Format:
     *   Message=Error
     *   OriginalMessage=msg or null
     *   Description=msg or null
     *
     * FIXME: provide numerical return codes for all possible error messages (Board not found,...)
     */
    private SimpleFieldSet errorMessageFCP(final String originalMessage, final Exception e) {

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Error");
        sfs.putOverwrite("OriginalMessage", (originalMessage == null) ? "null" : originalMessage);
        sfs.putOverwrite("Description", (e.getLocalizedMessage() == null) ? "null" : e.getLocalizedMessage());
        e.printStackTrace();
        return sfs;
    }
}
