/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.FCP;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.BoardMessageLink;
import plugins.Freetalk.Board.BoardThreadLink;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;
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
 *
 * @author bback
 */
public final class FCPInterface implements FredPluginFCP {

    private final Freetalk mFreetalk;
    private boolean isTerminated;

    public FCPInterface(final Freetalk myFreetalk) {
        mFreetalk = myFreetalk;
        isTerminated = false;
    }

    public void terminate() {
        isTerminated = true;
    }

    /**
     * @param replysender interface to send a reply
     * @param params parameters passed in, can be null
     * @param data a bucket of data passed in, can be null
     * @param access 0: direct call (plugin to plugin), 1: FCP restricted access,  2: FCP full access
     */
    public void handle(final PluginReplySender replysender, final SimpleFieldSet params, final Bucket data, final int accesstype) {

        try {
            if (isTerminated) {
                replysender.send(errorMessageFCP(params.get("Message"), new Exception("Plugin is terminated")));
            }

            if (params == null) {
                throw new Exception("Empty message received");
            }

            final String message = params.get("Message");
            if (message == null || message.trim().length() == 0) {
                throw new Exception("Specified message is empty");
            }

            if (message.equals("ListBoards")) {
                handleListBoards(replysender, params);
            } else if (message.equals("ListOwnIdentities")) {
                handleListOwnIdentities(replysender, params);
            } else if (message.equals("ListKnownIdentities")) {
                handleListKnownIdentities(replysender, params);
            } else if (message.equals("ListThreads")) {
                handleListThreads(replysender, params);
            } else if (message.equals("ListThreadMessages")) {
                handleListThreadMessages(replysender, params);
            } else if (message.equals("ListMessages")) {
                handleListMessages(replysender, params);

            } else if (message.equals("GetMessage")) {
                handleGetMessage(replysender, params);
            } else if (message.equals("PutMessage")) {
                handlePutMessage(replysender, params, data);

            } else if (message.equals("CreateBoard")) {
                handleCreateBoard(replysender, params);
            } else if (message.equals("CreateOwnIdentity")) {
                handleCreateOwnIdentity(replysender, params);

            } else if (message.equals("Status")) {
                handleStatus(replysender, params);

            } else if (message.equals("Ping")) {
                handlePing(replysender, params);
            } else {
                throw new Exception("Unknown message (" + message + ")");
            }
        } catch (final Exception e) {
            Logger.error(this, e.toString());
            try {
                if (!(e instanceof PluginNotFoundException)) {
                    replysender.send(errorMessageFCP(params.get("Message"), e));
                }
            } catch (final PluginNotFoundException e1) {
                Logger.normal(this, "Connection to request sender lost", e1);
            }
        }
    }

    /**
     * Handle ListBoards command.
     * Send a number of Board messages and finally an EndListBoards message.
     * Format of request:
     *   Message=ListBoards
     * Format of reply:
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
     *   LastReplyDate=utcMillis
     *   Date=utcMillis
     *   ReplyCount=123
     *   FetchDate=utcMillis
     *   IsThread=true|false  (all returned messages should be thread root messages)
     */
    private void handleListThreads(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchBoardException, NoSuchIdentityException
    {
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
            for(BoardThreadLink threadReference : board.getThreads(ownIdentity)) {
                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "MessageThread");
                sfs.putOverwrite("ID", threadReference.getThreadID());
                sfs.put("ReplyCount", board.threadReplyCount(ownIdentity, threadReference.getThreadID()));
                sfs.put("LastReplyDate", threadReference.getLastReplyDate().getTime());
                
                final Message thread = threadReference.getMessage();
                
                if(thread != null) {
	                sfs.putOverwrite("Title", thread.getTitle());
	                sfs.putOverwrite("Author", thread.getAuthor().getFreetalkAddress());
	                sfs.put("Date", thread.getDate().getTime());
	                sfs.put("FetchDate", thread.getFetchDate().getTime());
	                sfs.put("IsThread", thread.isThread());
                } else {
                	// The thread was not downloaded yet.
                	// TODO: Maybe Add title guess = title of first reply. See BoardPage for how to obtain.
                	// FIXME: The author can be reconstructed from the thread id because it contains the id of the author. We just need to figure out
                	// what the proper place for a function "getIdentityIDFromThreadID" is and whether I have already written one which can do that, and if
                	// yes, where it is.
                }
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
     *
     *   (only one SortByMessageXXX is allowed to be true)
     *   SortByMessageIndexAscending=true|false   (Optional, default is false)
     *   SortByMessageDateAscending=true|false    (Optional, default is false)
     *
     *   (only one MinimumMessageXXX constraint is allowed)
     *   MinimumMessageIndex=123         (optional, datatype int, default is 0)
     *   MinimumMessageDate=utcMillis    (optional, datatype long, default is 0)
     *
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
        final boolean sortByMessageIndexAscending = Boolean.parseBoolean(params.get("SortByMessageIndexAscending"));
        final boolean sortByMessageDateAscending = Boolean.parseBoolean(params.get("SortByMessageDateAscending"));
        if (sortByMessageIndexAscending && sortByMessageDateAscending) {
            throw new InvalidParameterException("Only one of SortByMessageIndexAscending and SortByMessageDateAscending is allowed to be true");
        }

        int minimumMessageIndex;
        try {
            minimumMessageIndex = Integer.parseInt(params.get("MinimumMessageIndex"));
        } catch(final NumberFormatException e) {
            minimumMessageIndex = 0;
        }
        long minimumMessageDate;
        try {
            minimumMessageDate = Long.parseLong(params.get("MinimumMessageDate"));
        } catch(final NumberFormatException e) {
            minimumMessageDate = 0;
        }
        if (minimumMessageIndex > 0 && minimumMessageDate > 0) {
            throw new InvalidParameterException("MinimumMessageIndex and MinimumMessageDate must not be specified together");
        }
        final boolean includeMessageText = Boolean.parseBoolean(params.get("IncludeMessageText"));

        final Board board = mFreetalk.getMessageManager().getBoardByName(boardName); // throws exception when not found

        synchronized(board) {  /* FIXME: Is this enough synchronization or should we lock the message manager? */

            final List<MessageReference> messageRefList;
            if (minimumMessageIndex > 0) {
                messageRefList = board.getMessagesByMinimumIndex(minimumMessageIndex, sortByMessageIndexAscending, sortByMessageDateAscending);
            } else if (minimumMessageDate > 0) {
                messageRefList = board.getMessagesByMinimumDate(minimumMessageDate, sortByMessageIndexAscending, sortByMessageDateAscending);
            } else {
                messageRefList = board.getAllMessages(sortByMessageIndexAscending);
            }

            // send all messages
            for(final MessageReference reference : messageRefList) {
                final Message msg = reference.getMessage();
                final int messageIndex = board.getMessageIndex(msg); // throws exception when not found
                sendSingleMessage(replysender, msg, messageIndex, includeMessageText);
            }
        }

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListMessages");
        replysender.send(sfs);
    }

    /**
     * Handle ListThreadMessages command.
     * Send a number of Message messages and finally an EndListMessages message.
     * Format of request:
     *   Message=ListThreadMessages
     *   BoardName=abc
     *   ThreadID=ID                     (optional, if not specified retrieves all Messages of Board)
     *   SortByMessageIndexAscending=true|false   (Optional, default is false)
     *   IncludeMessageText=true|false   (optional, default is false)
     * Format of reply: see sendSingleMessage()
     */
    private void handleListThreadMessages(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchBoardException, NoSuchMessageException,
    UnsupportedEncodingException
    {
        final String boardName = params.get("BoardName");
        if (boardName == null) {
            throw new InvalidParameterException("Boardname parameter not specified");
        }
        final String threadID = params.get("ThreadID");
        final boolean sortByMessageIndexAscending = Boolean.parseBoolean(params.get("SortByMessageIndexAscending"));
        final boolean includeMessageText = Boolean.parseBoolean(params.get("IncludeMessageText"));

        final Board board = mFreetalk.getMessageManager().getBoardByName(boardName); // throws exception when not found

        synchronized(board) {  /* FIXME: Is this enough synchronization or should we lock the message manager? */

            final List<BoardMessageLink> messageRefList;
            final Message thread = mFreetalk.getMessageManager().get(threadID); // throws exception when not found
            {
                // send thread root message
                final int messageIndex = board.getMessageIndex(thread); // throws exception when not found
                sendSingleMessage(replysender, thread, messageIndex, includeMessageText);
            }

            /* FIXME: This actually sorts by date! Is the sorting by message index needed anyway?? */
            messageRefList = board.getAllThreadReplies(thread.getID(), sortByMessageIndexAscending);

            // send all messages of thread
            for(final MessageReference reference : messageRefList) {
                final Message msg = reference.getMessage();
                final int messageIndex = board.getMessageIndex(msg); // throws exception when not found
                sendSingleMessage(replysender, msg, messageIndex, includeMessageText);
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
     *   MessageIndex=123                (message index in board)
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

        final Board specifiedBoard;
        try {
            specifiedBoard = mFreetalk.getMessageManager().getBoardByName(boardName);
        } catch(final NoSuchBoardException e) {
            throw new InvalidParameterException("Board '"+boardName+"' does not exist");
        }

        final String messageIndexString = params.get("MessageIndex");
        if (messageIndexString == null) {
            throw new InvalidParameterException("MessageIndex parameter not specified");
        }
        final int messageIndex;
        try {
            messageIndex = Integer.parseInt(messageIndexString);
        } catch(final NumberFormatException e) {
            throw new InvalidParameterException("MessageIndex ist not a number");
        }

        final boolean includeMessageText = Boolean.parseBoolean(params.get("IncludeMessageText"));

        final Message message = specifiedBoard.getMessageByIndex(messageIndex); // throws exception when not found

        sendSingleMessage(replysender, message, messageIndex, includeMessageText);
    }

    /**
     * Sends a single message.
     *
     * Format of reply:
     *   Message=Message
     *   ID=id
     *   MessageIndex=123         (unique per board)
     *   Title=title
     *   Author=freetalkAddr
     *   Date=utcMillis
     *   FetchDate=utcMillis
     *   IsThread=true|false
     *   ParentID=id              (optional)
     *   FileAttachmentCount=2    (optional, not send if value is 0)
     *   FileAttachmentURI.1=CHK@abc
     *   FileAttachmentSize.1=123 (datatype long)
     *   FileAttachmentURI.2=CHK@def
     *   FileAttachmentSize.2=456
     *   (following is only sent when IncludeMessageText=true)
     *   DataLength=123      (NOTE: no leading 'Replies.'!)
     *   Data                (NOTE: no leading 'Replies.'!)
     *   <123 bytes of utf8 text>
     *   (no EndMessage!)
     */
    private void sendSingleMessage(
            final PluginReplySender replysender,
            final Message message,
            final int messageIndex,
            final boolean includeMessageText)
    throws PluginNotFoundException, UnsupportedEncodingException
    {
        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Message");
        sfs.putOverwrite("ID", message.getID());
        sfs.put("MessageIndex", messageIndex);
        sfs.putOverwrite("Title", message.getTitle());
        sfs.putOverwrite("Author", message.getAuthor().getFreetalkAddress());
        sfs.put("Date", message.getDate().getTime());
        sfs.put("FetchDate", message.getFetchDate().getTime());
        sfs.put("IsThread", message.isThread());
        try {
            sfs.putOverwrite("ParentID", message.getParentID());
        } catch(final NoSuchMessageException e) {
        }
        final Attachment[] attachments = message.getAttachments();
        if (attachments != null && attachments.length > 0) {
            sfs.put("FileAttachmentCount", attachments.length);
            for(int x=1; x <= attachments.length; x++) {
                sfs.putOverwrite("FileAttachmentURI."+x, attachments[x-1].getURI().toString());
                sfs.put("FileAttachmentSize."+x, attachments[x-1].getSize());
            }
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
     * Handle CreateOwnIdentity command.
     * Format of request:
     *   Message=CreateOwnIdentity
     *   Nickname=name
     *   PublishTrustList=true|false            (optional, default is true)
     *   PublishIntroductionPuzzles=true|false  (optional, default is true)
     *   RequestURI=...                         (optional)
     *   InsertURI=...                          (optional)
     * Format of reply:
     *   Message=CreateOwnIdentityReply
     *   OwnIdentityCreated=true|false
     *   UID=uid
     *   FreetalkAddress=addr
     *   InsertURI=...
     *   RequestURI=...
     *   ErrorDescription=abc             (set if OwnIdentityCreated=false)
     */
    private void handleCreateOwnIdentity(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
        try {
            final String nickName = params.get("Nickname");
            if (nickName == null || nickName.length() == 0) {
                throw new InvalidParameterException("Nickname parameter not specified");
            }
            WoTIdentity.validateNickname(nickName); // throws Exception if invalid

            boolean publishTrustList = true;
            {
                final String publishTrustListString = params.get("PublishTrustList");
                if (publishTrustListString != null) {
                    publishTrustList = Boolean.parseBoolean(publishTrustListString);
                }
            }

            boolean publishIntroductionPuzzles = true;
            {
                final String publishIntroductionPuzzlesString = params.get("PublishIntroductionPuzzles");
                if (publishIntroductionPuzzlesString != null) {
                    publishIntroductionPuzzles = Boolean.parseBoolean(publishIntroductionPuzzlesString);
                }
            }

            final String requestUriString = params.get("RequestURI");
            final String insertUriString = params.get("InsertURI");
            if ((requestUriString == null || insertUriString == null)
                    && requestUriString != insertUriString)
            {
                throw new InvalidParameterException("RequestURI and InsertURI must be set together");
            }

            final WoTOwnIdentity id;
            if (requestUriString != null) {
                final FreenetURI requestUri = new FreenetURI(requestUriString); // throws Exception if malformed
                final FreenetURI insertUri = new FreenetURI(insertUriString);   // throws Exception if malformed

                id = (WoTOwnIdentity)mFreetalk.getIdentityManager().createOwnIdentity(
                        nickName,
                        publishTrustList,
                        publishIntroductionPuzzles,
                        requestUri,
                        insertUri);
            } else {
                id = (WoTOwnIdentity)mFreetalk.getIdentityManager().createOwnIdentity(
                        nickName,
                        publishTrustList,
                        publishIntroductionPuzzles);
            }

            // id can't be null when we come here
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "CreateOwnIdentityReply");
            sfs.putOverwrite("OwnIdentityCreated", "true");
            sfs.putOverwrite("UID", id.getUID());
            sfs.putOverwrite("FreetalkAddress", id.getFreetalkAddress());
            sfs.putOverwrite("InsertURI", id.getInsertURI().toString());
            sfs.putOverwrite("RequestURI", id.getRequestURI().toString());
            replysender.send(sfs);

        } catch(final Exception e) {
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "CreateOwnIdentityReply");
            sfs.putOverwrite("OwnIdentityCreated", "false");
            sfs.putOverwrite("ErrorDescription", e.getLocalizedMessage());
            replysender.send(sfs);
            return;
        }
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
        try {
            final String boardName = params.get("BoardName");
            if (boardName == null || boardName.length() == 0) {
                throw new InvalidParameterException("BoardName parameter not specified");
            }
            if (!Board.isNameValid(boardName)) {
                throw new InvalidParameterException("BoardName parameter is not valid");
            }

            Board board;
            synchronized(mFreetalk.getMessageManager()) {

                try {
                    board = mFreetalk.getMessageManager().getBoardByName(boardName);
                } catch (final NoSuchBoardException e) {
                    board = null;
                }

                if (board != null) {
                    throw new InvalidParameterException("Board with same name already exists");
                }

                board = mFreetalk.getMessageManager().getOrCreateBoard(boardName);
            }

            // board can't be null when we come here
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "CreateBoardReply");
            sfs.putOverwrite("BoardCreated", "true");
            sfs.putOverwrite("StoredBoardName", board.getName());
            replysender.send(sfs);

        } catch(final Exception e) {
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "CreateBoardReply");
            sfs.putOverwrite("BoardCreated", "false");
            sfs.putOverwrite("ErrorDescription", e.getLocalizedMessage());
            replysender.send(sfs);
            return;
        }
    }

    /**
     * Handle PutMessage command.
     * Sends a message.
     * Format of request:
     *   Message=PutMessage
     *   ParentID=ID             (optional, when set the msg is a reply)
     *   TargetBoards=abc,def    (comma separated list of target boards. one is required.)
     *   ReplyToBoard=abc        (optional, must be in TargetBoards)
     *   AuthorIdentityUID=UID   (UID of an own identity)
     *   FileAttachmentCount=2    (optional, not send if value is 0)
     *   FileAttachmentURI.1=CHK@abc
     *   FileAttachmentSize.1=123 (datatype long)
     *   FileAttachmentURI.2=CHK@def
     *   FileAttachmentSize.2=456
     *   Title=abc def           (message title)
     *
     * Format of reply:
     *   Message=PutMessageReply
     *   MessageEnqueued=true|false
     *   ErrorDescription=abc    (set when MessageEnqueued=false)
     */
    private void handlePutMessage(final PluginReplySender replysender, final SimpleFieldSet params, final Bucket data)
    throws PluginNotFoundException, InvalidParameterException
    {
        synchronized(mFreetalk.getMessageManager()) {

            try {
                // evaluate parentMessage
                final String parentMsgId = params.get("ParentID"); // may be null
                Message parentMessage = null;
                if (parentMsgId != null) {
                    try {
                        parentMessage = mFreetalk.getMessageManager().get(parentMsgId);
                    } catch(final NoSuchMessageException e) {
                        throw new InvalidParameterException("Message specified by ParentID was not found");
                    }
                }

                // evaluate targetBoards
                final String targetBoardsString = params.get("TargetBoards");
                if (targetBoardsString == null) {
                    throw new InvalidParameterException("TargetBoards parameter not specified");
                }
                final String[] targetBoardsArray = targetBoardsString.split(",");
                if (targetBoardsArray.length == 0) {
                    throw new InvalidParameterException("Invalid TargetBoards parameter specified");
                }
                final Set<Board> targetBoards = new HashSet<Board>();
                for(String targetBoardName : targetBoardsArray) {
                    targetBoardName = targetBoardName.trim();
                    if (targetBoardName.length() == 0) {
                        throw new InvalidParameterException("Invalid TargetBoards parameter specified");
                    }
                    try {
                        final Board board = mFreetalk.getMessageManager().getBoardByName(targetBoardName);
                        targetBoards.add(board);
                    } catch(final NoSuchBoardException e) {
                        throw new InvalidParameterException("TargetBoard '"+targetBoardName+"' does not exist");
                    }
                }

                // evaluate replyToBoard
                final String replyToBoardName = params.get("ReplyToBoard"); // may be null
                Board replyToBoard = null;
                if (replyToBoardName != null ) {
                    try {
                        replyToBoard = mFreetalk.getMessageManager().getBoardByName(replyToBoardName);
                    } catch(final NoSuchBoardException e) {
                        throw new InvalidParameterException("ReplyToBoard '"+replyToBoardName+"' does not exist");
                    }
                    if (!targetBoards.contains(replyToBoard)) {
                        throw new InvalidParameterException("ReplyToBoard is not contained in TargetBoards");
                    }
                }

                // evaluate authorIdentity
                final String authorIdentityUidString = params.get("AuthorIdentityUID");
                if (authorIdentityUidString == null) {
                    throw new InvalidParameterException("AuthorIdentityUID parameter not specified");
                }
                final FTOwnIdentity authorIdentity;
                try {
                    authorIdentity = mFreetalk.getIdentityManager().getOwnIdentity(authorIdentityUidString);
                } catch(final NoSuchIdentityException e) {
                    throw new InvalidParameterException("No own identity found for AuthorIdentityUID");
                }

                // evaluate attachments
                int attachmentCount;
                try {
                    attachmentCount = Integer.parseInt(params.get("FileAttachmentCount"));
                } catch(final Exception e) {
                    attachmentCount = 0;
                }
                final List<Attachment> attachments = new ArrayList<Attachment>(attachmentCount);
                for (int x=1; x <= attachmentCount; x++) {
                    final String uriString = params.get("FileAttachmentURI."+x);
                    final String sizeString = params.get("FileAttachmentSize."+x);
                    if (uriString == null || sizeString == null) {
                        throw new InvalidParameterException("Invalid FileAttachment specified ("+x+")");
                    }
                    long fileSize;
                    FreenetURI freenetUri;
                    try {
                        freenetUri = new FreenetURI(uriString);
                        fileSize = Long.parseLong(sizeString);
                    } catch(final Exception e) {
                        throw new InvalidParameterException("Invalid FileAttachment specified ("+x+")");
                    }
                    attachments.add(new Attachment(freenetUri, fileSize));
                }

                // evaluate messageTitle
                final String messageTitle = params.get("Title");
                if (messageTitle == null) {
                    throw new InvalidParameterException("Title parameter not specified");
                }
                if (messageTitle.length() > Message.MAX_MESSAGE_TITLE_TEXT_LENGTH) {
                    throw new InvalidParameterException("Message title is longer than 256 characters");
                }

                // evaluate messageText
                // we expect Data containing the message text
                if (data == null) {
                    throw new InvalidParameterException("No Message text sent");
                }
                if (data.size() > Message.MAX_MESSAGE_TEXT_BYTE_LENGTH) {
                    throw new InvalidParameterException("Message text is longer than 64KB");
                }

                // convert to UTF-8
                final byte[] utf8Bytes = new byte[(int)data.size()];
                final InputStream is = data.getInputStream();
                try {
                    if (is.read(utf8Bytes) != utf8Bytes.length) {
                        throw new InvalidParameterException("Internal error reading data from Bucket");
                    }
                } finally {
                    is.close();
                }

                final String messageText = new String(utf8Bytes, "UTF-8");

                // post new message
                mFreetalk.getMessageManager().postMessage(
                        parentMessage,
                        targetBoards,
                        replyToBoard,
                        authorIdentity,
                        messageTitle,
                        null,           // date, use current
                        messageText,
                        attachments);

                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "PutMessageReply");
                sfs.putOverwrite("MessageEnqueued", "true");
                replysender.send(sfs);

            } catch(final Exception e) {
                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "PutMessageReply");
                sfs.putOverwrite("MessageEnqueued", "false");
                sfs.putOverwrite("ErrorDescription", e.getLocalizedMessage());
                replysender.send(sfs);
            }
        } // synchronized(mFreetalk.getMessageManager())
    }

    /**
     * Status command handler.
     * Format of reply:
     *   Message=StatusReply
     *   UnsentMessageCount=123
     */
    private void handleStatus(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "StatusReply");
        sfs.put("UnsentMessageCount", mFreetalk.getMessageManager().countUnsentMessages());
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
