/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
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
        row.addChild("td", new String[] { "align" }, new String[] { "left" }, message.getAuthor().getShortestUniqueName(50));
        row.addChild("th", new String[] { "align" }, new String[] { "left" }, "Date:");
        row.addChild("td", new String[] { "align" }, new String[] { "left" }, mLocalDateFormat.format(message.getDate()));

        row = table.addChild("tr");
        row.addChild("th", new String[] { "align" }, new String[] { "left" }, "Debug:");
        addDebugInfo(row.addChild("td", new String[] { "align", "colspan"}, new String[] { "left", "3" }), message);

        row = table.addChild("tr");
        HTMLNode cell = row.addChild("td", new String[] { "align", "colspan" }, new String[] { "left", "4" });

        String[] lines = message.getText().split("\r\n|\n");
        for(String line : lines) {
            cell.addChild("#", line);
            cell.addChild("br");
        }

        addReplyButton(cell, message);
    }

    private void addReplyButton(HTMLNode parent, Message parentMessage) {
        parent = parent.addChild("div", "align", "right");
        HTMLNode newReplyForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/NewReply", "NewReplyPage");
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getUID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ParentMessageID", parentMessage.getID()});
        newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", "Reply" });
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
