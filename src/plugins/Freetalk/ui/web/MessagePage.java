/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import plugins.Freetalk.Board;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * A WebPage for displaying detailed and internal information about a Freetalk {@link Message}, such as its URI and other stuff.
 * 
 * The MessagePage is supposed to be linked from the {@link ThreadPage}, therefore it accepts a BoardName and ThreadID as HTTP parameters even though only the 
 * message ID is required for identifying the message: BoardName and ThreadID are being used for displaying breadcrumbs.
 *  
 * @author xor (xor@freenetproject.org)
 */
public class MessagePage extends WebPageImpl {
	
	private final String mBoardName;
	private final String mThreadID;
	private final String mMessageID;
	
	private SubscribedBoard mBoard;
	private BoardThreadLink mThread;
	private Message mMessage;

	public MessagePage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _basel10n) {
		super(myWebInterface, viewer, request, _basel10n);
		
        String boardName = request.getParam("BoardName");
        if(boardName.length() == 0) // Also allow POST requests.
        	boardName = request.getPartAsString("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH);
        
        String threadID = request.getParam("ThreadID");
        if(threadID.length() == 0) // Also allow POST requests.
        	threadID = request.getPartAsString("ThreadID", 256); // TODO: Use a constant for max thread ID length
		
        String messageID = request.getParam("MessageID");
        if(messageID.length() == 0) // Also allow POST requests.
        	messageID = request.getPartAsString("MessageID", 256); // TODO: Use a constant for max thread ID length
        
        mBoardName = boardName;
        mThreadID = threadID;
        mMessageID = messageID;
	}

	@Override public final void make() {
		try {
			MessageManager messageManager = mFreetalk.getMessageManager();
			
			synchronized(messageManager) {
				mMessage = messageManager.get(mMessageID);
				
				try {
					mBoard = messageManager.getSubscription(mOwnIdentity, mBoardName);
					mThread = mBoard.getThreadLink(mThreadID);
				}
				catch(NoSuchBoardException e) { }
				catch(NoSuchMessageException e) { }
				
				makeBreadcrumbs();
				
				// ThreadPage.addMessageBox(mContentNode, mMessage);
			}
			
			
		} catch(NoSuchMessageException e) {
        	makeBreadcrumbs();
        	HTMLNode alertBox = addAlertBox(l10n().getString("MessagePage.MessageDeleted.Header"));
        	alertBox.addChild("p", l10n().getString("MessagePage.MessageDeleted.Text0"));
        	HTMLNode p = alertBox.addChild("p");
        	
        	l10n().addL10nSubstitution(p, "MessagePage.MessageDeleted.Text1", new String[] { "link", "boardname" },
       			 new HTMLNode[] { HTMLNode.link(BoardPage.getURI(mBoardName)), HTMLNode.text(mBoardName) });
        }
	}
	
    private void makeBreadcrumbs() {
        BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
        Welcome.addBreadcrumb(trail);
        BoardsPage.addBreadcrumb(trail);
        if(mBoard != null)
        	BoardPage.addBreadcrumb(trail, mBoard);
        if(mThread != null)
        	ThreadPage.addBreadcrumb(trail, mBoard, mThread);
        MessagePage.addBreadcrumb(trail, mBoard, mThread, mMessage, mMessageID);
        mContentNode.addChild(trail.getHTMLNode());
    }
    
    /**
     * Add the breadcrumb of a MessagePage to the existing trail. Can also be used if the board, thread or message does not exist anymore,
     * then it's ID will be displayed.
     * 
     * @param trail The existing breadcrumb trail. Must not be null
     * @param board The board where the message was shown. Can be null.
     * @param BoardThreadLink The thread where the message was shown. Can be null.
     * @param message The given message. Can be null, then the ID will be displayed.
     * @param messageID The ID of the message.
     */
	public static void addBreadcrumb(BreadcrumbTrail trail, SubscribedBoard board, BoardThreadLink thread, Message message, String myMessageID) {
		final String breadcrumbURI = getURI(board != null ? board.getName() : null, thread != null ? thread.getThreadID() : null, myMessageID);
		String breadcrumbText;
		
		if(message != null) {
			breadcrumbText = trail.getL10n().getString("MessagePage.Breadcrumb.Text", new String[] { "MessageID", "MessageTitle" },
																				new String[] { myMessageID, message.getTitle() });
		} else {
			breadcrumbText = trail.getL10n().getString("MessagePage.Breadcrumb.MessageDeletedText", "MessageID", myMessageID);
		}
		
		trail.addBreadcrumbInfo(breadcrumbText, breadcrumbURI);
	}
	
	/**
	 * Get the FProxy URI of a MessagePage which shows information about the message with the given ID.
	 * 
	 * @param myBoardName The board of the thread where the message is referenced. This can be null.
	 * @param myThreadID The ID of the thread in which this message is referenced. This can be null.
	 * @param myMessageID The ID of the message. Must not be null.
	 */
	public static String getURI(String myBoardName, String myThreadID, String myMessageID) {
		String uri = Freetalk.PLUGIN_URI + "/showMessage?MessageID= " + myMessageID;
		
		if(myBoardName != null)
			uri += "&BoardName=" + myBoardName;
		
		if(myThreadID != null)
			uri += "&ThreadID=" + myThreadID;
		
		return uri;
	}

}
