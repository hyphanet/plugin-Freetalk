/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.FCP;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.activation.MimeType;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.Message;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardReplyLink;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.SubscribedBoard.BoardMessageLink;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
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
    @Override public void handle(final PluginReplySender replysender, final SimpleFieldSet params,
            final Bucket data, final int accesstype) {

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
            } else if (message.equals("ListSubscribedBoards")) {
                handleListSubscribedBoards(replysender, params);
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
            } else if (message.equals("SubscribeToBoard")) {
            	handleSubscribeToBoard(replysender, params);
        	} else if (message.equals("UnsubscribeFromBoard")) {
            	handleUnsubscribeFromBoard(replysender, params);
        	}
            else if (message.equals("Status")) {
                handleStatus(replysender, params);
            } else if (message.equals("Ping")) {
                handlePing(replysender, params);
            } else {
                throw new Exception("Unknown message (" + message + ")");
            }
        }
        catch (final Exception e) {
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
    
    private String getMandatoryParameter(final SimpleFieldSet sfs, final String name) throws InvalidParameterException {
    	final String result = sfs.get(name);
    	if(result == null) {
    		throw new IllegalArgumentException("Missing mandatory parameter: " + name);
    	}
    	return result;
    }

    /**
     * Handle ListBoards command.
     * Send a number of Board messages and finally an EndListBoards message.
     * Format of request:
     *   Message=ListBoards
     * Format of reply:
     *   Message=Board
     *   Name=name
     *   ID=id                        a unique ID that could have changed for the same board name. In this case the board is new, old was deleted.
     *   FirstSeenDate=utcMillis      (optional)
     */
    private void handleListBoards(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
        synchronized(mFreetalk.getMessageManager()) {
            for(final Board board : mFreetalk.getMessageManager().boardIteratorSortedByName()) { // TODO: Optimization: Use a non-sorting function.
                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "Board");
                sfs.putOverwrite("ID", board.getID());
                sfs.putOverwrite("Name", board.getName());
                sfs.put("FirstSeenDate", board.getFirstSeenDate().getTime());

                replysender.send(sfs);
            }
        }

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListBoards");
        replysender.send(sfs);
    }

    /**
     * Handle ListSubscribedBoards command. Retrieves all Boards the specified OwnIdentityID is subscribed to.
     * Sends a number of SubscribedBoard messages and finally an EndListSubscribedBoards message.
     * 
     * Format of request:
     *   Message=ListSubscribedBoards
     *   OwnIdentityID=ID
     * Format of reply:
     *   Message=SubscribedBoard
     *   Name=name
     *   ID=id                        a unique ID that could have changed for the same board name. In this case the board is new, old was deleted.
     *   FirstSeenDate=utcMillis      (optional)
     *   Description=txt              (optional)
     */
    private void handleListSubscribedBoards(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchIdentityException
    {
        final String ownIdentityID = getMandatoryParameter(params, "OwnIdentityID");
        OwnIdentity ownIdentity = mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityID);
        
        synchronized(mFreetalk.getMessageManager()) {
            for(final SubscribedBoard board : mFreetalk.getMessageManager().subscribedBoardIteratorSortedByName(ownIdentity)) {	// TODO: Optimization: Use a non sorting function.
                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "SubscribedBoard");
                sfs.putOverwrite("Name", board.getName());
                sfs.putOverwrite("ID", board.getID());
                sfs.put("FirstSeenDate", board.getFirstSeenDate().getTime());
                String desc = board.getDescription();
                if (desc != null) {
                    sfs.putOverwrite("Description", desc);
                }
                
                replysender.send(sfs);
            }
        }
        
        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListSubscribedBoards");
        replysender.send(sfs);
    }
    
    /**
     * Handle ListThreads command.
     * Sends a number of MessageThread messages and finally an EndListThreads message.
     * Format of request:
     *   Message=ListThreads
     *   BoardName=abc
     *   OwnIdentityID=ID
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
        final String boardName = getMandatoryParameter(params, "BoardName");
        final String ownIdentityID = getMandatoryParameter(params, "OwnIdentityID");

        //throws exception when not found
        final SubscribedBoard board = mFreetalk.getMessageManager().getSubscription(mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityID), boardName);

        synchronized(board) {
            for(BoardThreadLink threadReference : board.getThreads()) {
                final SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "MessageThread");
                sfs.putOverwrite("ID", threadReference.getThreadID());
                sfs.put("ReplyCount", board.threadReplyCount(threadReference.getThreadID()));
                sfs.put("LastReplyDate", threadReference.getLastReplyDate().getTime());
                
                try {
                final Message thread = threadReference.getMessage();
	                sfs.putOverwrite("Title", thread.getTitle());
	                sfs.putOverwrite("Author", thread.getAuthor().getFreetalkAddress());
	                sfs.put("Date", thread.getDate().getTime());
	                sfs.put("FetchDate", thread.getFetchDate().getTime());
	                sfs.put("IsThread", thread.isThread());
                }
                catch(MessageNotFetchedException e) {
                	// The thread was not downloaded yet.
                	// TODO: Add guesses for title and author ID.
                	// Title guess = title of first reply. See BoardPage for how to obtain.
                	// Further, the author can be reconstructed from the thread id because it contains the id of the author. We just need to figure out
                	// what the proper place for a function "getIdentityIDFromThreadID" is and whether I have already written one which can do that, and if
                	// yes, where it is.
                	// IMPORTANT: Those guesses should be marked as guesses in the reply (by using different field names) because it is not guranteed that
                	// the author of the thread reply did not specify a faked thread ID / thread title.
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
     *   OwnIdentityID=ID
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
    UnsupportedEncodingException, NoSuchIdentityException
    {
        final String boardName = getMandatoryParameter(params, "BoardName");
        final String ownIdentityID = getMandatoryParameter(params, "OwnIdentityID");
        
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
        
        //throws exception when not found
        final SubscribedBoard board = mFreetalk.getMessageManager().getSubscription(mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityID), boardName);

        synchronized(board) {

            final List<BoardMessageLink> messageRefList;
            if (minimumMessageIndex > 0) {
                messageRefList = board.getMessagesByMinimumIndex(minimumMessageIndex, sortByMessageIndexAscending, sortByMessageDateAscending);
            } else if (minimumMessageDate > 0) {
                messageRefList = board.getMessagesByMinimumDate(new Date(minimumMessageDate), sortByMessageIndexAscending, sortByMessageDateAscending);
            } else {
                messageRefList = board.getAllMessages(sortByMessageIndexAscending);
            }

            // send all messages
            for(final BoardMessageLink reference : messageRefList) {
            	try {
                    sendSingleMessage(replysender, reference.getMessage(), reference.getIndex(), includeMessageText);
                }
            	catch(MessageNotFetchedException e) {
            		// Ignore.
            	}
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
     *   OwnIdentityID=ID
     *   ThreadID=ID                     (optional, if not specified retrieves all Messages of Board)
     *   SortByMessageDateAscending=true|false   (Optional, default is false)
     *   IncludeMessageText=true|false   (optional, default is false)
     * Format of reply: see sendSingleMessage()
     */
    private void handleListThreadMessages(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException, InvalidParameterException, NoSuchBoardException, NoSuchMessageException,
    UnsupportedEncodingException, NoSuchIdentityException
    {
        final String boardName = getMandatoryParameter(params, "BoardName");
        final String ownIdentityID = getMandatoryParameter(params, "OwnIdentityID");
        
        final String threadID = params.get("ThreadID");
        final boolean sortByMessageDateAscending = Boolean.parseBoolean(params.get("SortByMessageDateAscending"));
        final boolean includeMessageText = Boolean.parseBoolean(params.get("IncludeMessageText"));

        //throws exception when not found
        final SubscribedBoard board = mFreetalk.getMessageManager().getSubscription(mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityID), boardName);

        synchronized(board) {

        	final BoardThreadLink threadLink = board.getThreadLink(threadID);
            final Iterable<BoardReplyLink> messageRefList;
            final Message thread = mFreetalk.getMessageManager().get(threadID); // throws exception when not found
            {
                // send thread root message
                sendSingleMessage(replysender, thread, threadLink.getIndex(), includeMessageText);
            }

            messageRefList = board.getAllThreadReplies(thread.getID(), sortByMessageDateAscending);

            // send all messages of thread
            for(final BoardMessageLink reference : messageRefList) {
            	try {
                final Message msg = reference.getMessage();
                sendSingleMessage(replysender, msg, reference.getIndex(), includeMessageText);
            	}
            	catch(MessageNotFetchedException e) {
            		// Ignore
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
     *   OwnIdentityID=ID
     *   MessageIndex=123                (message index in board)
     *   IncludeMessageText=true|false   (optional, default is false)
     * Format of reply: see sendSingleMessage()
     * Reply when messageID or boardName is not found:
     *   Message=Error
     *   OriginalMessage=GetMessage
     *   Description=Unknown message ID abc
     *   OR
     *   Description=Unknown board: abc
     *   OR
     *   Description=The message with ID ... was not fetched yet
     * @throws InvalidParameterException 
     * @throws NoSuchIdentityException 
     * @throws NoSuchBoardException 
     * @throws NoSuchMessageException 
     * @throws MessageNotFetchedException If the given MessageIndex is valid but the message belonging to that index was not downloaded yet. This usually happens
     * 			if a reply to a thread is downloaded before the thread message itself was fetched.
     * @throws PluginNotFoundException 
     * @throws UnsupportedEncodingException 
     */
    private void handleGetMessage(final PluginReplySender replysender, final SimpleFieldSet params)
    	throws InvalidParameterException, NoSuchBoardException, NoSuchIdentityException, NoSuchMessageException, MessageNotFetchedException,
    		UnsupportedEncodingException, PluginNotFoundException
    {
        final String boardName = getMandatoryParameter(params, "BoardName");
        final String ownIdentityID = getMandatoryParameter(params, "OwnIdentityID");

        final String messageIndexString = getMandatoryParameter(params, "MessageIndex");
        final int messageIndex;
        try {
            messageIndex = Integer.parseInt(messageIndexString);
        } catch(final NumberFormatException e) {
            throw new InvalidParameterException("MessageIndex ist not a number");
        }

        final boolean includeMessageText = Boolean.parseBoolean(params.get("IncludeMessageText"));

        //throws exception when not found
        final SubscribedBoard board = mFreetalk.getMessageManager().getSubscription(mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityID), boardName);
        
        final BoardMessageLink reference = board.getMessageByIndex(messageIndex); // throws exception when not found
        
        final Message message = reference.getMessage();  // throws MessageNotFetchedException

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
     *   ID=id
     *   Nickname=name
     *   FreetalkAddress=freetalkAddr
     */
    private void handleListKnownIdentities(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
    	final IdentityManager identityManager = mFreetalk.getIdentityManager();
    	
    	synchronized(identityManager) {
        for(final Identity id : identityManager.getAllIdentities()) {
            if (id instanceof OwnIdentity) {
                continue;
            }
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "KnownIdentity");
            sfs.putOverwrite("ID", id.getID());
            sfs.putOverwrite("Nickname", id.getNickname());
            sfs.putOverwrite("FreetalkAddress", id.getFreetalkAddress());
            replysender.send(sfs);
        }
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
     *   ID=id
     *   Nickname=name
     *   FreetalkAddress=freetalkAddr
     */
    // TODO: Require some kind of authentication.
    private void handleListOwnIdentities(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
    	final IdentityManager identityManager = mFreetalk.getIdentityManager();
    	
    	synchronized(identityManager) {
    	for(final OwnIdentity id : identityManager.ownIdentityIterator()) {
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "OwnIdentity");
            sfs.putOverwrite("ID", id.getID());
            sfs.putOverwrite("Nickname", id.getNickname());
            sfs.putOverwrite("FreetalkAddress", id.getFreetalkAddress());
            replysender.send(sfs);
        }
    	}

        final SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListOwnIdentities");
        replysender.send(sfs);
    }

    /**
    * Handle SubscribeToBoard command.
    * Format of request:
    *   BoardName=abc
    *   ID=id
     * @throws InvalidParameterException 
    */
    
    private void handleSubscribeToBoard(final PluginReplySender replysender, final SimpleFieldSet params) 
    throws InvalidParameterException, NoSuchIdentityException, NoSuchBoardException
    {
        final String boardName = getMandatoryParameter(params, "BoardName");
        final String ownIdentityID = getMandatoryParameter(params, "ID");
    	mFreetalk.getMessageManager().subscribeToBoard(mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityID), boardName);
    }


    /**
     * Handle UnsubscribeFromBoard command.
     * Format of request:
     *   BoardName=abc
     *   ID=id
     * @throws InvalidParameterException 
     */
     
     private void handleUnsubscribeFromBoard(final PluginReplySender replysender, final SimpleFieldSet params) 
     throws InvalidParameterException, NoSuchIdentityException, NoSuchBoardException
     {
         final String boardName = getMandatoryParameter(params, "BoardName");
         final String ownIdentityID = getMandatoryParameter(params, "ID");
     	 mFreetalk.getMessageManager().unsubscribeFromBoard(mFreetalk.getIdentityManager().getOwnIdentity(ownIdentityID), boardName);
     }

    
    /**
     * Handle CreateOwnIdentity command.
     * Format of request:
     *   Message=CreateOwnIdentity
     *   Nickname=name
     *   PublishTrustList=true|false            (optional, default is true)
     *   PublishIntroductionPuzzles=true|false  (optional, default is true)
     *   AutoSubscribe=true|false				(optional, default is false)
     *   DisplayImages=true|false				(optional, default is false)
     *   RequestURI=...                         (optional)
     *   InsertURI=...                          (optional)
     * Format of reply:
     *   Message=CreateOwnIdentityReply
     *   OwnIdentityCreated=true|false
     *   ID=id
     *   FreetalkAddress=addr
     *   InsertURI=...
     *   RequestURI=...
     *   ErrorDescription=abc             (set if OwnIdentityCreated=false)
     */
    private void handleCreateOwnIdentity(final PluginReplySender replysender, final SimpleFieldSet params)
    throws PluginNotFoundException
    {
        try {
            final String nickName = getMandatoryParameter(params, "Nickname");
            WoTIdentity.validateNickname(nickName); // throws Exception if invalid

            boolean publishTrustList = params.getBoolean("PublishTrustList", true);
            boolean publishIntroductionPuzzles = params.getBoolean("PublishIntroductionPuzzles", true);
            boolean autoSubscribe = params.getBoolean("AutoSubscribe", false);
            boolean displayImages = params.getBoolean("DisplayImages", false);

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
                        autoSubscribe,
                        displayImages,
                        requestUri,
                        insertUri);
            } else {
                id = (WoTOwnIdentity)mFreetalk.getIdentityManager().createOwnIdentity(
                        nickName,
                        publishTrustList,
                        publishIntroductionPuzzles,
                        autoSubscribe,
                        displayImages);
            }

            // id can't be null when we come here
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "CreateOwnIdentityReply");
            sfs.putOverwrite("OwnIdentityCreated", "true");
            sfs.putOverwrite("ID", id.getID());
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
            final String boardName = getMandatoryParameter(params, "BoardName");
            if (!Board.isNameValid(boardName)) {
                throw new InvalidParameterException("BoardName parameter is not valid");
            }

            Board board;
            synchronized(mFreetalk.getMessageManager()) {

                try {
                    mFreetalk.getMessageManager().getBoardByName(boardName);
                    throw new InvalidParameterException("Board with same name already exists");
                } catch (final NoSuchBoardException e) {
                }

                board = mFreetalk.getMessageManager().getOrCreateBoard(boardName);
            }

            // board can't be null when we come here
            final SimpleFieldSet sfs = new SimpleFieldSet(true);
            sfs.putOverwrite("Message", "CreateBoardReply");
            sfs.putOverwrite("BoardCreated", "true");
            sfs.putOverwrite("StoredBoardName", board.getName());
            sfs.putOverwrite("ID", board.getID());
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
     *   AuthorIdentityID=ID   (ID of an own identity)
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
        // TODO: Optimization: We don't need this lock probably, see Javadoc of postMessage
        synchronized(mFreetalk.getMessageManager()) {

            try {
            	// There are 4 possible combinations:
            	// Thread URI specified, parent URI specified: We are replying to the given message in the given thread.
            	// Thread URI specified, parent URI not specified: We are replying to the given thread directly, parent URI will be set to thread URI.
            	// Thread URI not specified, parent URI not specified: We are creating a new thread.
            	// Thread URI not specified, parent URI specified: Invalid, the message constructor will throw an exception.
            	// 
        		// The last case is invalid because the thread URI of a message is the primary information which decides in which thread it is displayed
            	// and you can link replies into multiple threads by replying to them with different thread URIs... so if there is only a parent URI and
            	// no thread URI we cannot decide to which thread the message belongs because the parent might belong to multiple threads.
            	
                // evaluate parentThread
                final String parentThreadID = params.get("ParentThreadID"); // may be null
                final Message parentThread;
                if(parentThreadID != null) {
                    try {
                        parentThread = mFreetalk.getMessageManager().get(parentThreadID);
                    } catch(final NoSuchMessageException e) {
                        throw new InvalidParameterException("Message specified by ParentThreadID was not found.");
                    }
                } else {
                	parentThread = null;
                }
                
                // evaluate parentMessage
                final String parentMsgId = params.get("ParentID"); // may be null
                final Message parentMessage;
                if (parentMsgId != null) {
                    try {
                        parentMessage = mFreetalk.getMessageManager().get(parentMsgId);
                    } catch(final NoSuchMessageException e) {
                        throw new InvalidParameterException("Message specified by ParentID was not found");
                    }
                } else {
                	parentMessage = null;
                }

                // evaluate targetBoards
                final String targetBoardsString = getMandatoryParameter(params, "TargetBoards");
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
                final String authorIdentityIDString = getMandatoryParameter(params, "AuthorIdentityID");
                final OwnIdentity authorIdentity;
                try {
                    authorIdentity = mFreetalk.getIdentityManager().getOwnIdentity(authorIdentityIDString);
                } catch(final NoSuchIdentityException e) {
                    throw new InvalidParameterException("No own identity found for AuthorIdentityID");
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
                    final String uriString = getMandatoryParameter(params, "FileAttachmentURI."+x);
                    final String mimeTypeString = params.get("FileAttachmentMIMEType."+x);
                    final String sizeString = params.get("FileAttachmentSize."+x);
                    long fileSize;
                    FreenetURI freenetUri;
                    MimeType mimeType;
                    try {
                        freenetUri = new FreenetURI(uriString);
                        mimeType = mimeTypeString != null ? new MimeType(mimeTypeString) : null;
                        fileSize = sizeString != null ? Long.parseLong(sizeString) : -1;
                    } catch(final Exception e) {
                        throw new InvalidParameterException("Invalid FileAttachment specified ("+x+")");
                    }
                    attachments.add(new Attachment(freenetUri, mimeType, fileSize));
                }

                // evaluate messageTitle
                final String messageTitle = getMandatoryParameter(params, "Title");
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
                mFreetalk.getMessageManager().postMessage(parentThread.getURI(),
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
     * TODO: provide numerical return codes for all possible error messages (Board not found,...)
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
