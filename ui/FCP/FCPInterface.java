/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.FCP;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;

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

	public FCPInterface(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
	}

    /**
     * @param replysender interface to send a reply
     * @param params parameters passed in, can be null
     * @param data a bucket of data passed in, can be null
     * @param access 0: direct call (plugin to plugin), 1: FCP restricted access,  2: FCP full access
     */
	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
	    try {
    	    if (params == null) {
                throw new Exception("Empty message received");
            }

            String message = params.get("Message");
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
            } else {
                throw new Exception("Unknown message (" + message + ")");
            }
        } catch (Exception e) {
            Logger.error(this, e.toString());
            try {
                if (!(e instanceof PluginNotFoundException)) {
                    replysender.send(errorMessageFCP(params.get("Message"), e), data);
                }
            } catch (PluginNotFoundException e1) {
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
    private void handleListBoards(PluginReplySender replysender, SimpleFieldSet params)
    throws PluginNotFoundException
    {
        synchronized(mFreetalk.getMessageManager()) {
            Iterator<Board> boards = mFreetalk.getMessageManager().boardIterator();
            while(boards.hasNext()) {
                Board board = boards.next();

                SimpleFieldSet sfs = new SimpleFieldSet(true);
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

        SimpleFieldSet sfs = new SimpleFieldSet(true);
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
     */
    private void handleListThreads(PluginReplySender replysender, SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchBoardException, NoSuchIdentityException
    {
        String boardName = params.get("BoardName");
        if (boardName == null) {
            throw new InvalidParameterException("Boardname parameter not specified");
        }
        String ownIdentityUID = params.get("OwnIdentityUID");
        if (ownIdentityUID == null) {
            throw new InvalidParameterException("OwnIdentityUID parameter not specified");
        }

        FTOwnIdentity ownIdentity = mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityUID); // throws exception when not found
        Board board = mFreetalk.getMessageManager().getBoardByName(boardName); // throws exception when not found

        synchronized(board) { /* FIXME: Is this enough synchronization or should we lock the message manager? */
            Iterator<MessageReference> threads = board.threadIterator(ownIdentity);
            while(threads.hasNext()) {
                Message thread = threads.next().getMessage();

                SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "MessageThread");
                sfs.putOverwrite("ID", thread.getID());
                sfs.putOverwrite("Title", thread.getTitle());
                sfs.putOverwrite("Author", thread.getAuthor().getFreetalkAddress());
                sfs.put("Date", thread.getDate().getTime());
                sfs.put("ReplyCount", board.threadReplyCount(ownIdentity, thread));
                replysender.send(sfs);
            }
        }

        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListThreads");
        replysender.send(sfs);
    }

    /**
     * Handle ListMessages command.
     * Send a number of Message messages and finally an EndListMessages message.
     * Format of request:
     *   Message=ListMessages
     *   BoardName=abc
     *   ThreadUID=UID
     * Format of reply:
     *   Message=Message
     *   ID=id
     *   Title=title
     *   Author=freetalkAddr
     *   Date=utcMillis
     *   ParentID=id         (optional)
     *   DataLength=123      (NOTE: no leading 'Replies.'!)
     *   Data                (NOTE: no leading 'Replies.'!)
     *   <123 bytes of utf8 text>
     *   (no EndMessage!)
     */
    private void handleListMessages(PluginReplySender replysender, SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchBoardException, NoSuchMessageException,
           UnsupportedEncodingException
    {
        String boardName = params.get("BoardName");
        if (boardName == null) {
            throw new InvalidParameterException("Boardname parameter not specified");
        }
        String threadID = params.get("ThreadID");
        if (threadID == null) {
            throw new InvalidParameterException("ThreadID parameter not specified");
        }

        Board board = mFreetalk.getMessageManager().getBoardByName(boardName); // throws exception when not found
        Message thread = mFreetalk.getMessageManager().get(threadID); // throws exception when not found

        synchronized(board) {  /* FIXME: Is this enough synchronization or should we lock the message manager? */
            sendSingleMessage(replysender, thread);

            for(MessageReference reference : board.getAllThreadReplies(thread)) {
                sendSingleMessage(replysender, reference.getMessage());
            }
        }

        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListMessages");
        replysender.send(sfs);
    }

    private void sendSingleMessage(PluginReplySender replysender, Message message)
    throws PluginNotFoundException, UnsupportedEncodingException
    {
        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Message");
        sfs.putOverwrite("ID", message.getID());
        sfs.putOverwrite("Title", message.getTitle());
        sfs.putOverwrite("Author", message.getAuthor().getFreetalkAddress());
        sfs.put("Date", message.getDate().getTime());
        try {
            sfs.putOverwrite("ParentID", message.getParentID());
        } catch(NoSuchMessageException e) {
        }
        if (message.getText() != null && message.getText().length() > 0) {
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
    private void handleListKnownIdentities(PluginReplySender replysender, SimpleFieldSet params)
    throws PluginNotFoundException
    {
        for(FTIdentity id : mFreetalk.getIdentityManager().getAllIdentities()) {
            if (id instanceof FTOwnIdentity) {
                continue;
            }
            SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "KnownIdentity");
            sfs.putOverwrite("UID", id.getUID());
            sfs.putOverwrite("Nickname", id.getNickname());
            sfs.putOverwrite("FreetalkAddress", id.getFreetalkAddress());
            replysender.send(sfs);
        }

        SimpleFieldSet sfs = new SimpleFieldSet(true);
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
    private void handleListOwnIdentities(PluginReplySender replysender, SimpleFieldSet params)
    throws PluginNotFoundException
    {
        Iterator<FTOwnIdentity> ownIdentities = mFreetalk.getIdentityManager().ownIdentityIterator();
        while (ownIdentities.hasNext()) {
            FTOwnIdentity id = ownIdentities.next();

            SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "OwnIdentity");
            sfs.putOverwrite("UID", id.getUID());
            sfs.putOverwrite("Nickname", id.getNickname());
            sfs.putOverwrite("FreetalkAddress", id.getFreetalkAddress());
            replysender.send(sfs);
        }

        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListOwnIdentities");
        replysender.send(sfs);
    }

	/**
	 * Simple Ping command handler. Returns a Pong.
	 * Format:
	 *   Message=Pong
	 *   UTCMillis=utcMillis
	 */
    private void handlePing(PluginReplySender replysender, SimpleFieldSet params)
    throws PluginNotFoundException
    {
        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Pong");
        sfs.put("UTCMillis", System.currentTimeMillis());
        replysender.send(sfs);
    }

    /**
     * Sends an error message to the client.
     * Format:
     *   Message=Error
     *   OriginalMessage=msg or null
     *   Description=msg or null
     */
    private SimpleFieldSet errorMessageFCP(String originalMessage, Exception e) {

        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Error");
        sfs.putOverwrite("OriginalMessage", (originalMessage == null) ? "null" : originalMessage);
        sfs.putOverwrite("Description", (e.getLocalizedMessage() == null) ? "null" : e.getLocalizedMessage());
        e.printStackTrace();
        return sfs;
    }
}
