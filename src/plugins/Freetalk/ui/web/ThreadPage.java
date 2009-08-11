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
import plugins.Freetalk.Board.BoardReplyLink;
import plugins.Freetalk.Board.BoardThreadLink;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTTrust;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.WoTDisconnectedException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * 
 * @author xor
 */
public final class ThreadPage extends WebPageImpl {

    private final Board mBoard;
    private final BoardThreadLink mThread;

    private static final DateFormat mLocalDateFormat = DateFormat.getDateTimeInstance();

    public ThreadPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchMessageException, NoSuchBoardException {
        super(myWebInterface, viewer, request);
        mBoard = mFreetalk.getMessageManager().getBoardByName(request.getParam("board"));
        mThread = mBoard.getThreadReference(request.getParam("id"));
    }

    public final void make() {
        makeBreadcrumbs();
        synchronized (mLocalDateFormat) {
            synchronized(mBoard) {
            	if(mThread.getMessage() != null) {
            		if(mThread.getMessage().isThread() == false)
            			addThreadIsNoThreadWarning();

            		addMessageBox(mThread.getMessage());
            	}
            	else
            		addThreadNotDownloadedWarning();

                for(MessageReference reference : mBoard.getAllThreadReplies(mThread.getThreadID(), true))
                    addMessageBox(reference.getMessage());
            }
        }
    }
    
    private void addThreadNotDownloadedWarning() {
    	addAlertBox("Thread not downloaded").addChild("div", "The parent thread of these messages was not downloaded yet.");
    }
    
    private void addThreadIsNoThreadWarning() {
    	HTMLNode div = addAlertBox("This is a forked thread").addChild("div");
    
    	div.addChild("#", "The author of the second message below replied to the first message stating that it should become a new thread, the first message was"
    			+ " not intended to be a thread by it's author, it was a reply to ");
    	try {
			div.addChild("a", "href",  Freetalk.PLUGIN_URI + "/showThread?board=" + mBoard.getName() + "&id=" + mThread.getMessage().getThreadID())
			.addChild("#", "this");
		} catch (NoSuchMessageException e) {
			throw new IllegalArgumentException("SHOULD NOT HAPPEN");
		}
		
		div.addChild("#", " thread.");
    }

    /* You have to synchronize on mLocalDateFormat when using this function */
    private void addMessageBox(Message message) {
        HTMLNode table = mContentNode.addChild("table", new String[] {"border", "width" }, new String[] { "0", "100%" });
        HTMLNode row = table.addChild("tr");
        HTMLNode authorNode = row.addChild("td", new String[] { "align", "valign", "rowspan", "width" }, new String[] { "left", "top", "2", "15%" }, "");
        authorNode.addChild("b").addChild("i").addChild("#", message.getAuthor().getShortestUniqueName(50));
        authorNode.addChild("br");
        authorNode.addChild("#", "Posts: " + mFreetalk.getMessageManager().getMessagesBy(message.getAuthor()).size());
        authorNode.addChild("br");
        authorNode.addChild("#", "Reputation: ");
        addTrustersInfo(authorNode, message.getAuthor());
        authorNode.addChild("br");
        authorNode.addChild("#", "Esteem: "+makeStars((int)(Math.log(((WoTIdentityManager)mFreetalk.getIdentityManager()).getScore(mOwnIdentity, message.getAuthor()))/Math.log(10))));
        authorNode.addChild("br");
        int trust;
        try {
            trust = ((WoTOwnIdentity)mOwnIdentity).getTrustIn(message.getAuthor());
        } catch (NumberFormatException e) {
            trust = 0;
        }
        authorNode.addChild("#", "Trust: "+trust);

        HTMLNode title = row.addChild("td", "align", "left", "");
        title.addChild("span", "style", "float:right", mLocalDateFormat.format(message.getDate()));
        if(((WoTOwnIdentity)mOwnIdentity).getAssessed(message) == false) {
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
        for(int i=0;i<number;i++)
            result += "*";
        return result;
    }

    private void addTrustersInfo(HTMLNode parent, FTIdentity author) {
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
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "Reply" });
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
        BoardsPage.addBreadcrumb(trail);
        BoardPage.addBreadcrumb(trail, mBoard);
        ThreadPage.addBreadcrumb(trail, mBoard, mThread);
        mContentNode.addChild(trail.getHTMLNode());
    }

    /**
     * 
     * @param trail
     * @param board
     * @param firstMessageInThread The thread itself if it was downloaded already, if not, the first reply
     * @param threadID
     */
    public static void addBreadcrumb(BreadcrumbTrail trail, Board board, BoardThreadLink myThread) {
        Message firstMessage = myThread.getMessage();
        
        if(firstMessage == null) { // The thread was not downloaded yet, we use it's first reply for obtaining the information in the breadcrumb
        	for(BoardReplyLink ref : board.getAllThreadReplies(myThread.getThreadID(), true)) {
        		firstMessage = ref.getMessage();
        		break;
        	}
        }
        
        trail.addBreadcrumbInfo(maxLength(firstMessage.getTitle(),30), Freetalk.PLUGIN_URI + "/showThread?board=" + board.getName() + "&id=" + myThread.getThreadID());
    }
}
