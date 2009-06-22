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
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
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
    private final Message mThread;

    private static final DateFormat mLocalDateFormat = DateFormat.getDateTimeInstance();

    public ThreadPage(WebInterface myWebInterface, FTOwnIdentity viewer, HTTPRequest request) throws NoSuchMessageException, NoSuchBoardException {
        super(myWebInterface, viewer, request);
        mBoard = mFreetalk.getMessageManager().getBoardByName(request.getParam("board"));
        mThread = mFreetalk.getMessageManager().get(request.getParam("id"));
    }

    public final void make() {
        makeBreadcrumbs();
        synchronized (mLocalDateFormat) {
            synchronized(mBoard) {  /* FIXME: Is this enough synchronization or should we lock the message manager? */
                addMessageBox(mThread);

                for(MessageReference reference : mBoard.getAllThreadReplies(mThread, true))
                    addMessageBox(reference.getMessage());
            }
        }
    }

    /* You have to synchronize on mLocalDateFormat when using this function */
    private void addMessageBox(Message message) {
        HTMLNode messageBox = addContentBox("Subject: " + maxLength(message.getTitle(),50));

        HTMLNode table = messageBox.addChild("table", new String[] {"border", "width" }, new String[] { "0", "100%" });

        HTMLNode row = table.addChild("tr");
        row.addChild("th", new String[] { "align" }, new String[] { "left" }, "Author:");
        HTMLNode authorInfo = new HTMLNode("td", new String[] { "align" }, new String[] { "left" }, message.getAuthor().getShortestUniqueName(50) + " ");
        addTrustersInfo(authorInfo, message.getAuthor());
        row.addChild(authorInfo);
        row.addChild("th", new String[] { "align" }, new String[] { "left" }, "Trust:");
        HTMLNode trustItem = row.addChild("td", new String[] { "align" }, new String[] { "left" }, "");
        // TODO: Get rid of the ugly cast, maybe this should be called WoTThreadPage :|
        trustItem.addChild("span", "style", "float:left", Integer.toString(((WoTOwnIdentity)mOwnIdentity).getScoreFor(message.getAuthor())));
        addModButton(trustItem, message.getAuthor(), 10, "+");
        addModButton(trustItem, message.getAuthor(), -10, "-");
        row.addChild("th", new String[] { "align" }, new String[] { "left" }, "Date:");
        row.addChild("td", new String[] { "align" }, new String[] { "left" }, mLocalDateFormat.format(message.getDate()));

        row = table.addChild("tr");
        row.addChild("th", new String[] { "align" }, new String[] { "left" }, "Debug:");
        addDebugInfo(row.addChild("td", new String[] { "align", "colspan"}, new String[] { "left", "5" }), message);

        row = table.addChild("tr");
        HTMLNode cell = row.addChild("td", new String[] { "align", "colspan" }, new String[] { "left", "6" });

        String[] lines = message.getText().split("\r\n|\n");
        for(String line : lines) {
            cell.addChild("#", line);
            cell.addChild("br");
        }

        addReplyButton(cell, message);
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
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ParentMessageID", parentMessage.getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "Reply" });
    }

    private void addModButton(HTMLNode parent, FTIdentity identity, int change, String label) {
        parent = parent.addChild("span", "style", "float:left");
        HTMLNode newReplyForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/ChangeTrust", "ChangeTrustPage");
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OtherIdentityID", identity.getUID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ThreadID", mThread.getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "TrustChange", String.valueOf(change)});
        newReplyForm.addChild("input", new String[] {"type", "name", "value", "style"}, new String[] {"submit", "submit", label, "width:3em" });
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

    public static void addBreadcrumb(BreadcrumbTrail trail, Board board, Message thread) {
        trail.addBreadcrumbInfo(maxLength(thread.getTitle(),30), Freetalk.PLUGIN_URI + "/showThread?board=" + board.getName() + "&id=" + thread.getID());
    }
}
