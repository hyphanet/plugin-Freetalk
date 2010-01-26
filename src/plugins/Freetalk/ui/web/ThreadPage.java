/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.text.DateFormat;
import java.util.Arrays;

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
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import plugins.Freetalk.exceptions.NotTrustedException;
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
            	
            	try {
            		Message threadMessage = mThread.getMessage();
            		
            		if(threadMessage.isThread() == false)
            			addThreadIsNoThreadWarning(threadMessage);

            		addMessageBox(threadMessage, mThread);
            	}
            	catch(MessageNotFetchedException e) {
            		addThreadNotDownloadedWarning(mThread);
            	}
            	
        		if(!mMarktThreadAsUnread && !mThread.wasThreadRead()) { // After displaying it to the user, mark it as read 
	        		mThread.markAsRead();
	        		mThread.markThreadAsRead();
	            	mThread.storeAndCommit();
        		}

                for(BoardReplyLink reference : mBoard.getAllThreadReplies(mThread.getThreadID(), true)) {
                	if(mMarktThreadAsUnread && reference.wasRead()) { // If requested, mark the messages of the thread as unread
                    	reference.markAsUnread();
                     	reference.storeAndCommit();
                    }
                	
                	try {
                		addMessageBox(reference.getMessage(), reference);
                	} catch(NoSuchMessageException e) {
                		throw new RuntimeException(e); // getMessage() should never fail for BoardReplyLink.
                	}

                    if(!mMarktThreadAsUnread && !reference.wasRead()) { // After displaying the messages to the user, mark them as read
	                    reference.markAsRead();
	                   	reference.storeAndCommit();
                    }
                }
        	}
			}
        } catch(NoSuchMessageException e) {
        	mThread = null;
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
    
    private void addThreadIsNoThreadWarning(Message threadWhichIsNoThread) {
    	HTMLNode div = addAlertBox(l10n().getString("ThreadPage.ThreadIsNoThreadWarning.Header")).addChild("div");
    	
    	String realThreadID;
    		
    	try {
    		realThreadID = threadWhichIsNoThread.getThreadID();
    	}
    	catch (NoSuchMessageException e) {
    	    throw new IllegalArgumentException("SHOULD NOT HAPPEN: addThreadIsNoThreadWarning called for a thread: " + mThread.getThreadID());
    	}
    	
    	Board realThreadBoard;
    	
    	// mBoard is a SubscribedBoard, getBoards() only returns a list of Board objects, so we must call getParentBoard()
    	if(Arrays.binarySearch(threadWhichIsNoThread.getBoards(), mBoard.getParentBoard()) >= 0)
    		realThreadBoard = mBoard;
    	else
    		realThreadBoard = threadWhichIsNoThread.getReplyToBoard();
    	
    	String uri = getURI(realThreadBoard.getName(), realThreadID);
    	
        l10n().addL10nSubstitution(
                div, 
                "ThreadPage.ThreadIsNoThreadWarning.Text",
                new String[] { "link", "/link" }, 
                new String[] {
                        "<a href=\""+uri+"\">",
                        "</a>" });
    }

    /**
     * Shows the given message.
     * 
     * You have to synchronize on mLocalDateFormat when using this function
     * 
     * @param message The message which shall be shown. Must not be null.
     * @param ref A reference to the message which is to be displayed. Can be null, then the "message was read?" information will be unavailable. 
     */
    private void addMessageBox(Message message, MessageReference ref) {

        HTMLNode table = mContentNode.addChild("table", new String[] {"border", "width", "class" }, new String[] { "0", "100%", "message" });
        HTMLNode row = table.addChild("tr");
        HTMLNode authorNode = row.addChild("td", new String[] { "align", "valign", "rowspan", "width" }, new String[] { "left", "top", "2", "15%" }, "");
        authorNode.addChild("b").addChild("i").addChild("abbr", new String[] { "title" }, new String[] { message.getAuthor().getID() }).addChild("#", message.getAuthor().getShortestUniqueName(50));
        authorNode.addChild("#", " [");
        authorNode.addChild("a", new String[] { "class", "href", "title" }, new String[] { "identity-link", "/WoT/ShowIdentity?id=" + message.getAuthor().getID(), "Web of Trust Page" }).addChild("#", "WoT");
        authorNode.addChild("#", "]");
        authorNode.addChild("br");
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.Posts") + ": " + mFreetalk.getMessageManager().getMessagesBy(message.getAuthor()).size());
        authorNode.addChild("br");
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.TrusterCount") + ": ");
        try {
        	addTrustersInfo(authorNode, message.getAuthor());
        }
        catch(Exception e) {
        	Logger.error(this, "addTrustersInfo() failed", e);
        	authorNode.addChild("#", l10n().getString("ThreadPage.Author.TrusterCountUnknown"));
        }
        
        // Your trust value
        authorNode.addChild("br");
        
        String trust;
        try {
            final int intTrust = ((WoTOwnIdentity)mOwnIdentity).getTrustIn((WoTIdentity)message.getAuthor());
            trust = Integer.toString(intTrust); 
        } catch (NotTrustedException e) {
            trust = l10n().getString("ThreadPage.Author.YourTrustNone");
        } catch (Exception e) {
        	Logger.error(this, "getTrust() failed", e);
        	trust = l10n().getString("ThreadPage.Author.YourTrustUnknown");
        }
        
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.YourTrust") + ": "+trust);
        
        // Effective score of the identity
        authorNode.addChild("br");
        
        String txtScore;
        try {
        	final int score = ((WoTIdentityManager)mFreetalk.getIdentityManager()).getScore((WoTOwnIdentity)mOwnIdentity, (WoTIdentity)message.getAuthor());
        	txtScore = Integer.toString(score);
        } catch(NotInTrustTreeException e) {
        	txtScore = l10n().getString("Common.WebOfTrust.ScoreNull");
        } catch(Exception e) {
        	Logger.error(this, "getScore() failed", e);
        	txtScore = l10n().getString("Common.WebOfTrust.ScoreNull");
        }
        
        authorNode.addChild("#", l10n().getString("Common.WebOfTrust.Score") + ": "+ txtScore);

        // Title of the message
        HTMLNode title = row.addChild((ref == null || ref.wasRead()) ? "td" : "th", "align", "left", "");
        
        title.addChild("span", "style", "float:right; margin-left:10px", mLocalDateFormat.format(message.getDate()));
        
        if(ref != null && ref instanceof BoardThreadLink)
        	addMarkThreadAsUnreadButton(title, (BoardThreadLink)ref);
        
        if(message.getAuthor() != mOwnIdentity && ((WoTOwnIdentity)mOwnIdentity).getAssessed(message) == false) {
            addModButton(title, message, 10, "+");
            addModButton(title, message, -10, "-");
            title.addChild("%", "&nbsp;");
        }
        title.addChild("b", maxLength(message.getTitle(),50));
        
        
        // Body of the message
        row = table.addChild("tr");
        HTMLNode text = row.addChild("td", "align", "left", "");
        String messageBody = message.getText();
        text.addChild(convertMessageBody(messageBody, "message-line", null));
        addReplyButton(text, message);
    }

    private void addTrustersInfo(HTMLNode parent, FTIdentity author) throws Exception {
    	WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();

        int trustedBy = identityManager.getReceivedTrustsCount(author, 1);
        int distrustedBy = identityManager.getReceivedTrustsCount(author, -1);

        parent.addChild("abbr", new String[]{"title", "style"}, new String[]{ l10n().getString("Common.WebOfTrust.TrustedByCount.Description"), "color:green"}, 
        		String.valueOf(trustedBy));
        
        parent.addChild("#", " / ");

        parent.addChild("abbr", new String[]{"title", "style"}, new String[]{ l10n().getString("Common.WebOfTrust.DistrustedByCount.Description"), "color:red"},
        		String.valueOf(distrustedBy));
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
        BreadcrumbTrail trail = new BreadcrumbTrail(l10n());
        Welcome.addBreadcrumb(trail);
        BoardsPage.addBreadcrumb(trail);
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
	 * This method converts the message body into a HTML node. Line breaks
	 * “CRLF” and “LF” are recognized and converted into {@code div} tags.
	 * Freenet URIs are recognized and converted into links, even when they have
	 * line breaks embedded in them.
	 *
	 * @param messageBody
	 *            The message body to convert
	 * @return The HTML node displaying the message
	 */
	public static HTMLNode convertMessageBody(String messageBody) {
		return convertMessageBody(messageBody, null, null);
	}

	/**
	 * This method converts the message body into a HTML node. Line breaks
	 * “CRLF” and “LF” are recognized and converted into {@code div} tags.
	 * Freenet URIs are recognized and converted into links, even when they have
	 * line breaks embedded in them.
	 *
	 * @param messageBody
	 *            The message body to convert
	 * @param lineClass
	 *            The CSS class(es) for the single lines
	 * @param linkClass
	 *            The CSS class(es) for the links
	 * @return The HTML node displaying the message
	 */
	public static HTMLNode convertMessageBody(String messageBody, String lineClass, String linkClass) {
		HTMLNode messageNode = new HTMLNode("#");
		HTMLNode currentParagraph = (lineClass != null) ? new HTMLNode("div", "class", lineClass) : new HTMLNode("div");
		String currentLine = messageBody;
		int chkLink = currentLine.indexOf("CHK@");
		int sskLink = currentLine.indexOf("SSK@");
		int uskLink = currentLine.indexOf("USK@");
		int kskLink = currentLine.indexOf("KSK@");
		int lineBreakCRLF = currentLine.indexOf("\r\n");
		int lineBreakLF = currentLine.indexOf("\n");
		while ((chkLink != -1) || (sskLink != -1) || (uskLink != -1) || (kskLink != -1) || (lineBreakCRLF != -1) || (lineBreakLF != -1)) {
			int nextLink = Integer.MAX_VALUE;
			int nextLineBreak = Integer.MAX_VALUE;
			if ((chkLink != -1) && (chkLink < nextLink)) {
				nextLink = chkLink;
			}
			if ((sskLink != -1) && (sskLink < nextLink)) {
				nextLink = sskLink;
			}
			if ((uskLink != -1) && (uskLink < nextLink)) {
				nextLink = uskLink;
			}
			if ((kskLink != -1) && (kskLink < nextLink)) {
				nextLink = kskLink;
			}
			if ((lineBreakCRLF != -1) && (lineBreakCRLF < nextLineBreak)) {
				nextLineBreak = lineBreakCRLF;
			}
			if ((lineBreakLF != -1) && (lineBreakLF < nextLineBreak)) {
				nextLineBreak = lineBreakLF;
			}
			/* TODO: other separators? */
			if (nextLineBreak < nextLink) {
				currentParagraph.addChild("#", currentLine.substring(0, nextLineBreak));
				messageNode.addChild(currentParagraph);
				currentLine = currentLine.substring(nextLineBreak);
				if (currentLine.startsWith("\r\n")) {
					currentLine = currentLine.substring(2);
				} else if (currentLine.startsWith("\n")) {
					currentLine = currentLine.substring(1);
				}
				currentParagraph = (lineClass != null) ? new HTMLNode("div", "class", lineClass) : new HTMLNode("div");
			} else if (nextLink < nextLineBreak) {
				currentParagraph.addChild("#", currentLine.substring(0, nextLink));
				int firstSlash = currentLine.indexOf('/', nextLink);
				String uriKey = currentLine.substring(nextLink, firstSlash).replaceAll("[\r\n\t ]+", "");
				int nextSpace = currentLine.indexOf(' ', firstSlash);
				if ((nextSpace > nextLineBreak) && (firstSlash < nextLineBreak)) {
					nextSpace = nextLineBreak;
				}
				if (nextSpace == -1) {
					nextSpace = currentLine.length();
				}
				uriKey += currentLine.substring(firstSlash, nextSpace);
				currentLine = currentLine.substring(nextSpace);
				HTMLNode linkNode = (linkClass != null) ? new HTMLNode("a", new String[] { "href", "class" }, new String[] { "/" + uriKey, linkClass }, uriKey) : new HTMLNode("a", "href", "/" + uriKey, uriKey);
				currentParagraph.addChild(linkNode);
			}
			chkLink = currentLine.indexOf("CHK@");
			sskLink = currentLine.indexOf("SSK@");
			uskLink = currentLine.indexOf("USK@");
			kskLink = currentLine.indexOf("USK@");
			lineBreakCRLF = currentLine.indexOf("\r\n");
			lineBreakLF = currentLine.indexOf("\n");
		}
		currentParagraph.addChild("#", currentLine);
		messageNode.addChild(currentParagraph);
		return messageNode;
	}

    /**
     * 
     * @param trail
     * @param board
     * @param firstMessageInThread The thread itself if it was downloaded already, if not, the first reply
     * @param threadID
     */
    public static void addBreadcrumb(BreadcrumbTrail trail, SubscribedBoard board, BoardThreadLink myThread) {
        Message firstMessage = null;
        
        try {
        	firstMessage = myThread.getMessage();
        }
        catch (MessageNotFetchedException e) { // The thread was not downloaded yet, we use it's first reply for obtaining the information in the breadcrumb
        	for(BoardReplyLink ref : board.getAllThreadReplies(myThread.getThreadID(), true)) { // FIXME: Synchronization is lacking.
        		try  {
        			firstMessage = ref.getMessage();
        		} catch(MessageNotFetchedException e1) {
        			throw new RuntimeException(e1); // Should not happen: BoardReplyLink objects are only created if a message was fetched already.
        		}
        		break;
        	}
        }
        
    	if(firstMessage == null)
    		throw new RuntimeException("Thread neither has a thread message nor any replies: " + myThread);
        
        trail.addBreadcrumbInfo(maxLength(firstMessage.getTitle(), 30), getURI(board, myThread));
    }
}
