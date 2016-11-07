/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import plugins.Freetalk.Board;
import plugins.Freetalk.Configuration;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Identity;
import plugins.Freetalk.Message;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Quoting;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardMessageLink;
import plugins.Freetalk.SubscribedBoard.BoardReplyLink;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTMessageRating;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageRatingException;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import plugins.Freetalk.exceptions.NotTrustedException;
import freenet.keys.FreenetURI;
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
	
	private boolean mFirstUnread = true;

	private static final DateFormat mLocalDateFormat = DateFormat.getDateTimeInstance();

	public ThreadPage(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, BaseL10n _baseL10n)
	throws NoSuchMessageException, NoSuchBoardException, NoSuchElementException {
		super(myWebInterface, viewer, request, _baseL10n);

		String boardName = request.getParam("BoardName");
		if(boardName.length() == 0) // Also allow POST requests.
			boardName = request.getPartAsStringFailsafe("BoardName", Board.MAX_BOARDNAME_TEXT_LENGTH);

		String threadID = request.getParam("ThreadID");
		if(threadID.length() == 0)
			threadID = request.getPartAsStringFailsafe("ThreadID", 256); // TODO: Use a constant for max thread ID length

		mMarktThreadAsUnread = mRequest.isPartSet("MarkThreadAsUnread");

		mBoard = mFreetalk.getMessageManager().getSubscription(mOwnIdentity, boardName);
		mThreadID = threadID;
	}

	@Override public final void make() {
		try {
			synchronized (mLocalDateFormat) {

			// TODO: Optimization: We do NOT want to lock the identity manager here. We have to do it to prevent deadlocks because there is
			// no non-locking getIdentity() which could be used in addThreadNotDownloadedWarning/addReplyNotDownloadedWarning
			synchronized(mFreetalk.getIdentityManager()) {
        	
        	// Normally, we would have to lock the MessageManager because we call storeAndCommit() on BoardMessageLink objects:
        	// The board might be deleted between getSubscription() and the synchronized(mBoard) - the storeAndCommit() would result in orphan objects.
        	// BUT BoardMessageLink.storeAndCommit() does a db.isStored() check and throws if the BoardMessageLink is not stored anymore.
        	
        	synchronized(mBoard) {
            	mThread = mBoard.getThreadLink(mThreadID);
            	
            	makeBreadcrumbs();
            	
            	// Mark as unread first, then display
            	if(mMarktThreadAsUnread)
        			mThread.markThreadAndRepliesAsUnreadAndCommit();
           
            	try {
            		Message threadMessage = mThread.getMessage();
            		
            		if(threadMessage.isThread() == false)
            			addThreadIsNoThreadWarning(threadMessage);
            		else if(mBoard.contains(threadMessage) == false) // Do "else", one link to the original thread is enough.
            			addThreadBelongsToDifferentBoardWarning(threadMessage);

            		addMessageBox(threadMessage, mThread);
            	}
            	catch(MessageNotFetchedException e) {
            		addThreadNotDownloadedWarning(mThread);
            	}

                for(BoardReplyLink reference : mBoard.getAllThreadReplies(mThread.getThreadID(), true)) {
                	try {
                		addMessageBox(reference.getMessage(), reference);
                	} catch(MessageNotFetchedException e) {
                		addReplyNotDownloadedWarning(reference);
                		// TODO: Ensure that the warning is not displayed before a message whose parent is present:
                		// An attacker whose is trying to attack a message X might post a message A with an invalid parent ID and set the date 
                		// of A to the date of X. The SubscribedBoard code will then guess the date of A's parent as its date minus 1
                		// millisecond,  resulting in the construction of a ghost BoardReplyLink for A's parent with date == date of X - 1ms.
                		// Therefore, the "Reply not downloaded" warning will be displayed before X even though X's parent might be available
                		// This problem is inherent to flat (i.e. non-treeview) thread display and therefore not fixed in SubscribedBoard...
                		// Right now this is only a TO-DO and not FIX-ME because the l10n of the not-downloaded-warning has been chosen carefully
                		// so it does not state that the not-downloaded message is parent of the next message, it just says "a message is missing here"
                	}
                }
                
                // After most of the displaying-code has not failed we mark as read
                if(!mMarktThreadAsUnread)
            		mThread.markThreadAndRepliesAsReadAndCommit();
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
                    new String[] { "link", "boardname" }, 
                    new HTMLNode[] { HTMLNode.link(BoardPage.getURI(mBoard)), HTMLNode.text(mBoard.getName()) });
		}
	}

	private void addThreadNotDownloadedWarning(BoardThreadLink ref) {
		HTMLNode table = mContentNode.addChild("table", new String[] { "border", "width", "class", "id" },
				new String[] { "0", "100%", "message", ref.getMessageID()});

		HTMLNode row = table.addChild("tr", "class", "message");

		try {
			addAuthorNode(row, mFreetalk.getIdentityManager().getIdentity(ref.getAuthorID()));
		} catch(NoSuchIdentityException e) {
			HTMLNode authorNode = row.addChild("td", new String[] { "align", "valign", "rowspan", "width" },
					new String[] { "left", "top", "2", "15%" }, "");
			authorNode.addChild("b").addChild("i").addChild("#", l10n().getString("ThreadPage.ThreadNotDownloadedWarning.Author"));
		}

		HTMLNode title = row.addChild("td", new String[] { "align", "class" },
				new String[] { "left", "title " + ((ref == null || ref.wasRead()) ? "read" : "unread") });
		title.addChild("div", "class", "date", mLocalDateFormat.format(ref.getMessageDate()));

		addMarkThreadAsUnreadButton(title, ref);

		title.addChild("div", "class", "text", l10n().getString("ThreadPage.ThreadNotDownloadedWarning.Title"));

		// Body of the message
		row = table.addChild("tr");
		HTMLNode text = row.addChild("td", "align", "left", "");
		text.addChild("div", "class", "infobox-error", l10n().getString("ThreadPage.ThreadNotDownloadedWarning.Content"));
	}

	private void addReplyNotDownloadedWarning(BoardReplyLink ref) {
		HTMLNode table = mContentNode.addChild("table", new String[] { "border", "width", "class", "id"},
				new String[] { "0", "100%", "message", ref.getMessageID() });

		HTMLNode row = table.addChild("tr", "class", "message");

		try {
			addAuthorNode(row, mFreetalk.getIdentityManager().getIdentity(ref.getAuthorID()));
		} catch(NoSuchIdentityException e) {
			HTMLNode authorNode = row.addChild("td", new String[] { "align", "valign", "rowspan", "width" },
					new String[] { "left", "top", "2", "15%" }, "");
			authorNode.addChild("b").addChild("i").addChild("#", l10n().getString("ThreadPage.ReplyNotDownloadedWarning.Author"));
		}

		HTMLNode title = row.addChild("td", new String[] { "align", "class" },
				new String[] { "left", "title " + ((ref == null || ref.wasRead()) ? "read" : "unread") });
		title.addChild("div", "class", "date", mLocalDateFormat.format(ref.getMessageDate()));


		title.addChild("div", "class", "text", l10n().getString("ThreadPage.ReplyNotDownloadedWarning.Title"));

		// Body of the message
		row = table.addChild("tr");
		HTMLNode text = row.addChild("td", "align", "left", "");
		text.addChild("div", "class", "infobox-error", l10n().getString("ThreadPage.ReplyNotDownloadedWarning.Content"));
	}

	private void addThreadIsNoThreadWarning(Message threadWhichIsNoThread) {
		HTMLNode div = addAlertBox(l10n().getString("ThreadPage.ThreadIsNoThreadWarning.Header")).addChild("div");

		String realThreadID = threadWhichIsNoThread.getThreadIDSafe();

		Board realThreadBoard;

		// mBoard is a SubscribedBoard, getBoards() only returns a list of Board objects, so we must call getParentBoard()
		// TODO: This should be encapsulated in a function Message.containedInBoard(Board board)
		if(Arrays.binarySearch(threadWhichIsNoThread.getBoards(), mBoard.getParentBoard()) >= 0)
			realThreadBoard = mBoard;
		else {
			try {
				realThreadBoard = threadWhichIsNoThread.getReplyToBoard();
			} catch(NoSuchBoardException e) {
				// TODO: List all boards to which the original thread was sent, not only the first one
				realThreadBoard = threadWhichIsNoThread.getBoards()[0];
			}
		}

        l10n().addL10nSubstitution(
                div, 
                "ThreadPage.ThreadIsNoThreadWarning.Text",
                new String[] { "link" }, 
                new HTMLNode[] { HTMLNode.link(getURI(realThreadBoard.getName(), realThreadID)) });
	}

	private void addThreadBelongsToDifferentBoardWarning(Message thread) {
		HTMLNode div = addAlertBox(l10n().getString("ThreadPage.ThreadBelongsToDifferentBoardWarning.Header")).addChild("div");

		Board realThreadBoard;

		try {
			realThreadBoard = thread.getReplyToBoard();
		} catch(NoSuchBoardException e) {
			// TODO: List all boards to which the original thread was sent, not only the first one
			realThreadBoard = thread.getBoards()[0];
		}

        l10n().addL10nSubstitution(
                div, 
                "ThreadPage.ThreadBelongsToDifferentBoardWarning.Text",
                new String[] { "link" }, 
                new HTMLNode[] { HTMLNode.link(getURI(realThreadBoard.getName(), thread.isThread() ? thread.getID() : thread.getThreadIDSafe())) });
	}

	private void addAuthorNode(HTMLNode parent, WoTIdentity author) {
		HTMLNode authorNode = parent.addChild("td", new String[] { "align", "valign", "rowspan", "width", "class" }, new String[] { "left", "top", "2", "15%", "author" }, "");

		authorNode.addChild("a", new String[] { "class", "href", "title" },
				new String[] { "identity-link", Freetalk.WOT_PLUGIN_URI + "/ShowIdentity?id=" + author.getID(), "Web of Trust Page" })
				.addChild("abbr", new String[] { "title" }, new String[] { author.getID() })
				.addChild("span", "class", "name", author.getShortestUniqueName());
		
        authorNode.addChild("br");
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.Posts") + ": " + mFreetalk.getMessageManager().getMessagesBy(author).size());
        authorNode.addChild("br");
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.TrustersCount") + ": ");
        try {
        	addTrustersInfo(authorNode, author);
        }
        catch(Exception e) {
        	Logger.error(this, "addTrustersInfo() failed", e);
        	authorNode.addChild("#", l10n().getString("ThreadPage.Author.TrustersCountUnknown"));
        }
        
        authorNode.addChild("br");
        authorNode.addChild("#", l10n().getString("ThreadPage.Author.TrusteesCount") + ": ");
        try {
        	addTrusteesInfo(authorNode, author);
        }
        catch(Exception e) {
        	Logger.error(this, "addTrusteesInfo() failed", e);
        	authorNode.addChild("#", l10n().getString("ThreadPage.Author.TrusteesCountUnknown"));
        }
        
        Integer intTrust = null;
        
        if(author == mOwnIdentity) {
        	authorNode.addChild("br");
        	authorNode.addChild("br");
        	authorNode.addChild("#", l10n().getString("ThreadPage.Author.Yourself"));
        	authorNode.addChild("br");
        } else {
	        // Your trust value
	        authorNode.addChild("br");
	        
	        String trust;
	        try {
	            intTrust = ((WoTOwnIdentity)mOwnIdentity).getTrustIn(author);
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
	        	final int score = ((WoTIdentityManager)mFreetalk.getIdentityManager()).getScore((WoTOwnIdentity)mOwnIdentity, author);
	        	txtScore = Integer.toString(score);
	        } catch(NotInTrustTreeException e) {
	        	txtScore = l10n().getString("Common.WebOfTrust.ScoreNull");
	        } catch(Exception e) {
	        	Logger.error(this, "getScore() failed", e);
	        	txtScore = l10n().getString("Common.WebOfTrust.ScoreNull");
	        }
	        
	        authorNode.addChild("#", l10n().getString("Common.WebOfTrust.Score") + ": "+ txtScore);
        }
        
        authorNode.addChild("br");
        authorNode.addChild("div", "class", "identicon").addChild("img", new String[] { "src", "width", "height" }, new String[] { Freetalk.WOT_PLUGIN_URI + "/GetIdenticon?identity=" + author.getID(), "128", "128"});
    }

    /**
     * Shows the given message.
     * 
     * You have to synchronize on mLocalDateFormat when using this function
     * 
     * @param message The message which shall be shown. Must not be null.
     * @param ref A reference to the message which is to be displayed. Can be null, then the "message was read?" information will be unavailable. 
     */
    private void addMessageBox(Message message, BoardMessageLink ref) {
    	
    	final WoTIdentity author = (WoTIdentity)message.getAuthor();

		HTMLNode table = mContentNode.addChild("table", new String[] { "border", "width", "class", "id" },
				new String[] { "0", "100%", "message", ref.getMessageID()});

		HTMLNode row = table.addChild("tr", "class", "message");
		
		if(mFirstUnread && !ref.wasRead()) {
			row.addAttribute("id", "FirstUnreadMessage");
			mFirstUnread = false;
		}

		addAuthorNode(row, author);

		// Title of the message
		HTMLNode title = row.addChild("td", new String[] { "align", "class" }, new String[] { "left", "title " + ((ref == null || ref.wasRead()) ? "read" : "unread") });
		title.addChild("div", "class", "date", mLocalDateFormat.format(message.getDate()));


		if(ref != null && ref instanceof BoardThreadLink)
			addMarkThreadAsUnreadButton(title, (BoardThreadLink)ref);

		if(author != mOwnIdentity) {
			HTMLNode modButtons = title.addChild("div", "class", "button-row");

			try {
				WoTMessageRating rating = mFreetalk.getMessageManager().getMessageRating(mOwnIdentity, message);
				addRemoveRatingButton(modButtons, message, rating);
			} catch(NoSuchMessageRatingException e) {
				Integer intTrust;

				try {
					intTrust = ((WoTOwnIdentity)mOwnIdentity).getTrustIn(author);
				} catch(Exception e2) {
					intTrust = null;
				}

				if(intTrust == null || (intTrust+10) <= 100) { // TODO: Use constants
					addRateButton(modButtons, message, 10, (intTrust!=null ? intTrust : 0) + 10);
				}
				if(intTrust == null || (intTrust-10) >= -100) { // TODO: Use constants
					addRateButton(modButtons, message, -10, (intTrust!=null ? intTrust : 0) - 10);
				}
			}
		}
		title.addChild("div", "class", "text", message.getTitle());


		// Body of the message
		row = table.addChild("tr", "class", "body");
		HTMLNode text = row.addChild("td", "align", "left", "");
		Quoting.TextElement element = Quoting.parseText(message);
		elementsToHTML(text, element.mChildren, mOwnIdentity, mFreetalk.getIdentityManager());
		addReplyButton(text, message.getID());
	}

	public static void elementsToHTML(HTMLNode parent, List<Quoting.TextElement> elements, OwnIdentity viewer, WoTIdentityManager identityManager) {
		for (final Quoting.TextElement t : elements) {
			switch(t.mType) {
			case PlainText: {
				addTextToNode(parent, t.mContent);
				break;
			}

			case Bold: {
				HTMLNode child = parent.addChild("b", "");
			   	elementsToHTML(child, t.mChildren, viewer, identityManager);
			   	break;
			}
			
			case Image:
				if(viewer.wantsImageDisplay()) {
					String uriText = t.getContentText().replaceAll("\n","").trim();
					try {
						FreenetURI uri = new FreenetURI(uriText);
						parent.addChild(new HTMLNode("img", new String[] { "src", "alt" }, new String[] { "/" + uri.toString(), uri.toString() }));
					} catch (MalformedURLException e) {
						parent.addChild("span", "class", "error", uriText);
					}
					
					break;
				}
				// Fall through and display as link.

			case Link: {
				String uriText = t.getContentText().replaceAll("\n","").trim();
				try {
					FreenetURI uri = new FreenetURI(uriText);
					HTMLNode linkNode = new HTMLNode("a", "href", "/" + uri.toString());
					HTMLNode linkText = new HTMLNode("abbr", "title", uri.toString(), uri.toShortString());
					linkNode.addChild(linkText);
					parent.addChild(linkNode);
				} catch (MalformedURLException e) {
					if (uriText.toLowerCase().startsWith("http")) {
						try {
							final URI uri = new URI(uriText);
							if(!uri.getScheme().equalsIgnoreCase("http"))
								throw new URISyntaxException(uriText, "Does not have http protocol");
							
							HTMLNode linkNode = new HTMLNode("a", "href", "/?_CHECKED_HTTP_="+ uri.toString(), uriText);
							parent.addChild(linkNode);
						} catch(URISyntaxException syntaxError) {
							parent.addChild("span", "class", "error", uriText);
						}
					}
					else
						parent.addChild("#", uriText);
				}
				
				break;
			}

			case Italic: {
				HTMLNode child = parent.addChild("i", "");
				elementsToHTML(child, t.mChildren, viewer, identityManager);
				break;
			}

			case Code: {
				HTMLNode child = parent.addChild("div", "class", "code");
				elementsToHTML(child, t.mChildren, viewer, identityManager);
				break;
			}

			case Quote: {
				HTMLNode child = parent.addChild("div", "class", "quote");
				HTMLNode authorNode = child.addChild("div", "class", "author");

				final String specifiedAuthor = t.mAttributes.get("author");
				// TODO: Put a link to the message
				// final String messageID = t.mAttributes.get("message"); 

				if(specifiedAuthor == null) {
					authorNode.addChild("b", "Quote");
				} else {
					authorNode.addChild("b", "Unvalidated quote of "); // TODO: l10n
					
					// TODO: Move to proper class
					// mNickname + "@" + mID + "." + Freetalk.WOT_CONTEXT.toLowerCase();
					final Pattern pattern = Pattern.compile(".*?@(.*)."+ Freetalk.WOT_CONTEXT.toLowerCase());
					final Matcher matcher = pattern.matcher(t.mContent);
					
					if (!matcher.matches()) {
						authorNode.addChild("b", specifiedAuthor);
					} else {
						try {
							final WoTIdentity author = (WoTIdentity)identityManager.getIdentity(matcher.group(1));
							authorNode.addChild("a", new String[] { "class", "href", "title" },
									new String[] { "identity-link", Freetalk.WOT_PLUGIN_URI + "/ShowIdentity?id=" + author.getID(), "Web of Trust Page" })
									.addChild("abbr", new String[] { "title" }, new String[] { author.getID() })
									.addChild("span", "class", "name", author.getShortestUniqueName());
						} catch (NoSuchIdentityException e) {
							authorNode.addChild("b", specifiedAuthor + " (Unknown identity)"); // TODO: l10n
						}
					}
				}

				authorNode.addChild("#", ":");

				elementsToHTML(child, t.mChildren, viewer, identityManager);
				break;
			}

			case Error: {
				addTextToNode(parent.addChild("span", "class", "error"), t.mContent);
				break;
			}
			}
		}
	}

	private static void addTextToNode(HTMLNode parent, String text) {
		String[] lines = text.split("\r?\n", -1);

		parent.addChild("#", lines[0]);
		for(int i = 1; i < lines.length; i++) {
			parent.addChild("br");
			parent.addChild("#", lines[i]);
		}
	}

	private void addTrustersInfo(HTMLNode parent, Identity author) throws Exception {
		WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();

		int trustedBy = identityManager.getReceivedTrustsCount(author, 1);
		int distrustedBy = identityManager.getReceivedTrustsCount(author, -1);

		parent.addChild("abbr", new String[]{"title", "class"}, new String[]{ l10n().getString("Common.WebOfTrust.TrustedByCount.Description"), "trust-count"},
				String.valueOf(trustedBy));

		parent.addChild("#", " / ");

		parent.addChild("abbr", new String[]{"title", "class"}, new String[]{ l10n().getString("Common.WebOfTrust.DistrustedByCount.Description"), "distrust-count"},
				String.valueOf(distrustedBy));
	}

	private void addTrusteesInfo(HTMLNode parent, Identity author) throws Exception {
		WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();

		int trustsCount = identityManager.getGivenTrustsCount(author, 1);
		int distrustsCount = identityManager.getGivenTrustsCount(author, -1);

		parent.addChild("abbr", new String[]{"title", "class"},
				new String[]{ l10n().getString("Common.WebOfTrust.PositiveGivenTrustsCount.Description"), "trust-count"},
				String.valueOf(trustsCount));

		parent.addChild("#", " / ");

		parent.addChild("abbr", new String[]{"title", "class"},
				new String[]{ l10n().getString("Common.WebOfTrust.NegativeGivenTrustsCount.Description"), "distrust-count"},
				String.valueOf(distrustsCount));
	}

	private void addReplyButton(HTMLNode parent, String parentMessageID) {
		parent = parent.addChild("div", "align", "right");
		HTMLNode newReplyForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/NewReply", "NewReplyPage");
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ParentThreadID", mThread.getThreadID()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ParentMessageID", parentMessageID});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit", l10n().getString("ThreadPage.ReplyButton") });
	}

	private void addRateButton(HTMLNode parent, Message message, int change, int newTrust) {
		parent = parent.addChild("div", "class", "button-row-button");
		HTMLNode newReplyForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/ChangeTrust", "ChangeTrustPage");
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ThreadID", mThread.getThreadID()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "MessageID", message.getID()});
		newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "TrustChange", String.valueOf(change)});
		
		if(change >= 0) {
			newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit",
					l10n().getString("ThreadPage.Rating.Rate.Good.Text", "points", "+" + change)});
		} else { // distrust button
			if(newTrust >= 0) {
				newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit",
						l10n().getString("ThreadPage.Rating.Rate.Bad.Text", "points", Integer.toString(change))});
			} else {
				newReplyForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit",
						l10n().getString("ThreadPage.Rating.Rate.Ignore.Text", "points", Integer.toString(change))});
			}
		}
	}

	private void addRemoveRatingButton(HTMLNode parent, Message message, WoTMessageRating rating) {
		parent = parent.addChild("div", "class", "button-row-button");
		HTMLNode removeRatingForm = addFormChild(parent, Freetalk.PLUGIN_URI + "/ChangeTrust", "ChangeTrustPage");
		removeRatingForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "OwnIdentityID", mOwnIdentity.getID()});
		removeRatingForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "BoardName", mBoard.getName()});
		removeRatingForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "ThreadID", mThread.getThreadID()});
		removeRatingForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "MessageID", message.getID()});
		removeRatingForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"hidden", "RemoveRating", "true"});
		removeRatingForm.addChild("input", new String[] {"type", "name", "value"}, new String[] {"submit", "submit",
				l10n().getString("ThreadPage.Rating.Remove.Text", "points", (rating.getValue() >=0  ? "+" : "") + rating.getValue()) });
	}

	private void addMarkThreadAsUnreadButton(final HTMLNode title, final BoardThreadLink ref) {
		HTMLNode span = title.addChild("div", "class", "mark-unread-button");

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
	
	public static String getFirstUnreadURI(final SubscribedBoard board, final BoardThreadLink thread) {
		return getURI(board, thread) + "#FirstUnreadMessage";
	}

	public static String getURI(final SubscribedBoard board, final BoardThreadLink thread) {
		return getURI(board.getName(), thread.getThreadID());
	}

	public static String getURI(final String boardName, final String threadID) {
		return Freetalk.PLUGIN_URI + "/showThread?BoardName=" + boardName + "&ThreadID=" + threadID;
	}

	public static URI getURI(final String boardName, final String threadID, final String messageID) {
		try {
			return new URI(Freetalk.PLUGIN_URI + "/showThread?BoardName=" + boardName + "&ThreadID=" + threadID + "#" + messageID);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
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
			synchronized(board) {
			for(BoardReplyLink ref : board.getAllThreadReplies(myThread.getThreadID(), true)) {
				try  {
					firstMessage = ref.getMessage();
					break;
				} catch(MessageNotFetchedException e1) { }
			}
			}
		}

		if(firstMessage == null)
			throw new RuntimeException("Thread neither has a thread message nor any replies: " + myThread);

		trail.addBreadcrumbInfo(maxLength(firstMessage.getTitle(), 30), getURI(board, myThread));
	}
}
