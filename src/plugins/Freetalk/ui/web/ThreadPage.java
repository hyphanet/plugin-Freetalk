/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;
import java.util.List;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardReplyLink;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.SubscribedBoard.MessageReference;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTTrust;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import plugins.Freetalk.exceptions.NotTrustedException;
import plugins.Freetalk.exceptions.WoTDisconnectedException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor
 */
public final class ThreadPage extends WebPageImpl {

    private final SubscribedBoard mBoard;
    private final String mThreadID;
    private BoardThreadLink mThread;
    private final boolean mMarktThreadAsUnread;

    private static final DateFormat mLocalDateFormat = DateFormat.getDateTimeInstance();

    public ThreadPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n)
    throws NoSuchMessageException, NoSuchBoardException {
        super(myWebInterface, viewer, request, _baseL10n);
        
        String boardName = request.getParam("BoardName");
        if(boardName.length() == 0) // Also allow POST requests.
        	boardName = request.getPartAsString("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH);
        
        String threadID = request.getParam("ThreadID");
        if(threadID.length() == 0)
        	threadID = request.getPartAsString("ThreadID", 256); // TODO: Use a constant for max thread ID length
        
        mMarktThreadAsUnread = mRequest.isPartSet("MarkThreadAsUnread");
        
        mBoard = mFreetalk.getMessageManager().getSubscription(mOwnIdentity, boardName);
        mThreadID = threadID;
    }

    public final void make() {
        try {
        synchronized (mLocalDateFormat) {
        	
        	// Normally, we would have to lock the MessageManager because we call storeAndCommit() on MessageReference objects:
        	// The board might be deleted between getSubscription() and the synchronized(mBoard) - the storeAndCommit() would result in orphan objects.
        	// BUT MessageReference.storeAndCommit() does a db.isStored() check and throws if the MessageReference is not stored anymore.
        	
        	synchronized(mBoard) {
            	mThread = mBoard.getThreadLink(mThreadID);
            	
            	makeBreadcrumbs();
           
            	if(mMarktThreadAsUnread && mThread.wasThreadRead()) { // Mark thread as unread if requested.
        			mThread.markThreadAsUnread();
        			mThread.storeAndCommit();
        		}
            	
            	if(mThread.getMessage() != null) {
            		if(mThread.getMessage().isThread() == false)
            			addThreadIsNoThreadWarning();

            		addMessageBox(mThread);
            	}
            	else
            		addThreadNotDownloadedWarning(mThread);
            	
        		if(!mMarktThreadAsUnread && !mThread.wasThreadRead()) { // After displaying it to the user, mark it as read 
	        		mThread.markAsRead();
	        		mThread.markThreadAsRead();
	            	mThread.storeAndCommit();
        		}

                for(MessageReference reference : mBoard.getAllThreadReplies(mThread.getThreadID(), true)) {
                	if(mMarktThreadAsUnread && reference.wasRead()) { // If requested, mark the messages of the thread as unread
                    	reference.markAsUnread();
                     	reference.storeAndCommit();
                    }
                	
                	addMessageBox(reference);

                    if(!mMarktThreadAsUnread && !reference.wasRead()) { // After displaying the messages to the user, mark them as read
	                    reference.markAsRead();
	                   	reference.storeAndCommit();
                    }
                }
            }
        }
        } catch(NoSuchMessageException e) {
        	makeBreadcrumbs();
        	HTMLNode alertBox = addAlertBox(l10n().getString("ThreadPage.ThreadDeleted.Header"));
        	alertBox.addChild("p", l10n().getString("ThreadPage.ThreadDeleted.Text1"));
        	HTMLNode p = alertBox.addChild("p");
        	
            l10n().addL10nSubstitution(
                    p, 
                    "ThreadPage.ThreadDeleted.Text2",
                    new String[] { "link", "boardname", "/link" }, 
                    new String[] {
                            "<a href=\""+BoardPage.getURI(mBoard)+"\">",
                            mBoard.getName(),
                            "</a>" });
        }
    }
    
    private void addThreadNotDownloadedWarning(BoardThreadLink ref) {
        HTMLNode table = mContentNode.addChild("table", new String[] {"border", "width" }, new String[] { "0", "100%" });
        HTMLNode row = table.addChild("tr");
        HTMLNode authorNode = row.addChild("td", new String[] { "align", "valign", "rowspan", "width" }, new String[] { "left", "top", "2", "15%" }, "");
    	// FIXME: The author can be reconstructed from the thread id because it contains the id of the author. We just need to figure out
    	// what the proper place for a function "getIdentityIDFromThreadID" is and whether I have already written one which can do that, and if
    	// yes, where it is.
        authorNode.addChild("b").addChild("i").addChild("#", l10n().getString("ThreadPage.ThreadNotDownloadedWarning.Author"));

        HTMLNode title = row.addChild(ref.wasRead() ? "td" : "th", "align", "left", "");

        addMarkThreadAsUnreadButton(title, (BoardThreadLink)ref);
        
        title.addChild("b", l10n().getString("ThreadPage.ThreadNotDownloadedWarning.Title"));
        
        row = table.addChild("tr");
        HTMLNode text = row.addChild("td", "align", "left", "");
        text.addChild("div", "class", "infobox-error", l10n().getString("ThreadPage.ThreadNotDownloadedWarning.Content"));
    }
    
    private void addThreadIsNoThreadWarning() {
    	HTMLNode div = addAlertBox(l10n().getString("ThreadPage.ThreadIsNoThreadWarning.Header")).addChild("div");

    	String uri;
    	try {
    	    uri = getURI(mBoard.getName(), mThread.getMessage().getThreadID());
    	} catch (NoSuchMessageException e) {
    	    throw new IllegalArgumentException("SHOULD NOT HAPPEN");
    	}
    	
        l10n().addL10nSubstitution(
                div, 
                "ThreadPage.ThreadIsNoThreadWarning.Text",
                new String[] { "link", "/link" }, 
                new String[] {
                        "<a href=\""+uri+"\">",
                        "</a>" });
    }

    /* You have to synchronize on mLocalDateFormat when using this function */
    private void addMessageBox(MessageReference ref) {
    	Message message = ref.getMessage();
    	
        HTMLNode table = mContentNode.addChild("table", new String[] {"border", "width" }, new String[] { "0", "100%" });
        HTMLNode row = table.addChild("tr");
        HTMLNode authorNode = row.addChild("td", new String[] { "align", "valign", "rowspan", "width" }, new String[] { "left", "top", "2", "15%" }, "");
        authorNode.addChild("b").addChild("i").addChild("#", message.getAuthor().getShortestUniqueName(50));
        authorNode.addChild("br");
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.Posts") + ": " + mFreetalk.getMessageManager().getMessagesBy(message.getAuthor()).size());
        authorNode.addChild("br");
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.Reputation") + ": ");
        try {
        	addTrustersInfo(authorNode, message.getAuthor());
        }
        catch(Exception e) {
        	Logger.error(this, "addTrustersInfo() failed", e);
        	authorNode.addChild("#", l10n().getString("ThreadPage.UnknownReputation"));
        }
        
        authorNode.addChild("br");
        
        final String txtEsteem = l10n().getString("ThreadPage.Author.Esteem");
        try {
        	int score = ((WoTIdentityManager)mFreetalk.getIdentityManager()).getScore((WoTOwnIdentity)mOwnIdentity, (WoTIdentity)message.getAuthor());
        		
        	authorNode.addChild("#", txtEsteem + ": "+ makeStars((int)(Math.log(score)/Math.log(10))));
        } catch(NotInTrustTreeException e) {
        	authorNode.addChild("#", txtEsteem + ": " + l10n().getString("ThreadPage.Author.EsteemNone"));
        } catch(Exception e) {
        	Logger.error(this, "getScore() failed", e);
        	authorNode.addChild("#", txtEsteem + ": " + l10n().getString("ThreadPage.Author.EsteemNone"));
        }
        
        authorNode.addChild("br");
        
        String trust;
        try {
            int intTrust = ((WoTOwnIdentity)mOwnIdentity).getTrustIn((WoTIdentity)message.getAuthor());
            trust = Integer.toString(intTrust); 
        } catch (NotTrustedException e) {
            trust = l10n().getString("ThreadPage.Author.YourTrustNone");
        } catch (Exception e) {
        	Logger.error(this, "getTrust() failed", e);
        	trust = l10n().getString("ThreadPage.Author.YourTrustUnknown");
        }
        
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.YourTrust") + ": "+trust);

        HTMLNode title = row.addChild(ref.wasRead() ? "td" : "th", "align", "left", "");
        
        title.addChild("span", "style", "float:right; margin-left:10px", mLocalDateFormat.format(message.getDate()));
        
        if(ref instanceof BoardThreadLink)
        	addMarkThreadAsUnreadButton(title, (BoardThreadLink)ref);
        
        if(message.getAuthor() != mOwnIdentity && ((WoTOwnIdentity)mOwnIdentity).getAssessed(message) == false) {
            addModButton(title, message, 10, "+");
            addModButton(title, message, -10, "-");
            title.addChild("%", "&nbsp;");
        }
        title.addChild("b", maxLength(message.getTitle(),50));
        
        

        row = table.addChild("tr");
        HTMLNode text = row.addChild("td", "align", "left", "");
        String[] lines = message.getText().split("\r\n|\n");
        for(String line : lines) {
            text.addChild("#", line);
            text.addChild("br");
        }
        addReplyButton(text, message);
    }

    private String makeStars(int number) {
        String result = "";
        for(int i=0;i<number;i++) {
            result += "*";
        }
        return result;
    }

    private void addTrustersInfo(HTMLNode parent, FTIdentity author) throws Exception {
        int trustedBy = 0;
        int distrustedBy = 0;
        String trusted = "";
        String distrusted = "";
        List<WoTTrust> receivedTrust;
        try {
            receivedTrust = ((WoTIdentityManager)mFreetalk.getIdentityManager()).getReceivedTrusts(author);
            for(WoTTrust t: receivedTrust) {
                if(t.getValue() > 0) {
                    trustedBy++;
                    if(!trusted.equals("")) trusted += ", ";
                    trusted += t.getTruster().getShortestUniqueName(20);
                }
                if(t.getValue() < 0) {
                    distrustedBy++;
                    if(!distrusted.equals("")) distrusted += ", ";
                    distrusted += t.getTruster().getShortestUniqueName(20);
                }
            }
        } catch (WoTDisconnectedException e) {
            parent.addChild("#", "?");
            return;
        }

        if(trustedBy > 0) {
            parent.addChild("abbr", new String[]{"title", "style"}, new String[]{trusted, "color:green"}, String.valueOf(trustedBy));
        } else {
            parent.addChild("#", String.valueOf(trustedBy));
        }
        parent.addChild("#", "/");
        if(distrustedBy > 0) {
            parent.addChild("abbr", new String[]{"title", "style"}, new String[]{distrusted, "color:red"}, String.valueOf(distrustedBy));
        } else {
            parent.addChild("#", String.valueOf(distrustedBy));
        }
    }

    private void addReplyButton(HTMLNode parent, Message parentMessage) {
        parent = parent.addChild("div", "align", "right");
        HTMLNode newReplyForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/NewReply", "NewReplyPage");
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ParentThreadID", mThread.getThreadID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ParentMessageID", parentMessage.getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("ThreadPage.ReplyButton") });
    }

    private void addModButton(HTMLNode parent, Message message, int change, String label) {
        parent = parent.addChild("span", "style", "float:left");
        HTMLNode newReplyForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/ChangeTrust", "ChangeTrustPage");
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OtherIdentityID", message.getAuthor().getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ThreadID", mThread.getThreadID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "MessageID", message.getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "TrustChange", String.valueOf(change)});
        newReplyForm.addChild("input", new String[] {"type", "name", "value", "style"}, new String[] {"submit", "submit", label, "width:2em" });
    }
    
    private void addMarkThreadAsUnreadButton(final HTMLNode title, final BoardThreadLink ref) {
    	HTMLNode span = title.addChild("span", "style", "float:right");
    	
        HTMLNode markAsUnreadButton = addFormChild(span, Freetalk.PLUGIN_URI + "/showThread", "ThreadPage");
        markAsUnreadButton.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
        markAsUnreadButton.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
        markAsUnreadButton.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ThreadID", mThread.getThreadID()});
        markAsUnreadButton.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "MarkThreadAsUnread", "true"});
        markAsUnreadButton.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("ThreadPage.MarkAsUnreadButton") });
    }

    private void addDebugInfo(HTMLNode messageBox, Message message) {
        messageBox = messageBox.addChild("font", new String[] { "size" }, new String[] { "-2" });

        messageBox.addChild("#", "uri: " + message.getURI());
        messageBox.addChild("br", "ID: " + message.getID());
        try {
            messageBox.addChild("br", "threadID: " + message.getThreadID());
        }
        catch (NoSuchMessageException e) {
            messageBox.addChild("br", "threadID: null");
        }

        try {
            messageBox.addChild("br", "parentID: " + message.getParentID());
        }
        catch(NoSuchMessageException e) {
            messageBox.addChild("br", "parentID: null");
        }
    }

    private void makeBreadcrumbs() {
        BreadcrumbTrail trail = new BreadcrumbTrail();
        Welcome.addBreadcrumb(trail);
        BoardsPage.addBreadcrumb(trail, l10n());
        BoardPage.addBreadcrumb(trail, mBoard);
        if(mThread != null)
        	ThreadPage.addBreadcrumb(trail, mBoard, mThread);
        mContentNode.addChild(trail.getHTMLNode());
    }
    
    public static String getURI(final SubscribedBoard board, final BoardThreadLink thread) {
    	return getURI(board.getName(), thread.getThreadID());
    }
    
    public static String getURI(final String boardName, final String threadID) {
    	return Freetalk.PLUGIN_URI + "/showThread?BoardName=" + boardName + "&ThreadID=" + threadID;
    }

    /**
     * 
     * @param trail
     * @param board
     * @param firstMessageInThread The thread itself if it was downloaded already, if not, the first reply
     * @param threadID
     */
    public static void addBreadcrumb(BreadcrumbTrail trail, SubscribedBoard board, BoardThreadLink myThread) {
        Message firstMessage = myThread.getMessage();
        
        if(firstMessage == null) { // The thread was not downloaded yet, we use it's first reply for obtaining the information in the breadcrumb
        	for(BoardReplyLink ref : board.getAllThreadReplies(myThread.getThreadID(), true)) { // FIXME: Synchronization is lacking.
        		firstMessage = ref.getMessage();
        		break;
        	}
        }
        
        trail.addBreadcrumbInfo(maxLength(firstMessage.getTitle(),30), getURI(board, myThread));
    }
}
