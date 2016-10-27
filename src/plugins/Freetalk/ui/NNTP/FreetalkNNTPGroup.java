/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import java.util.Iterator;
import java.util.NoSuchElementException;

import plugins.Freetalk.Message;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.support.Logger;

/**
 * Object representing a newsgroup, as seen from the NNTP client's
 * point of view.
 *
 * @author Benjamin Moody
 */
public final class FreetalkNNTPGroup {
    private final SubscribedBoard mBoard;

    public FreetalkNNTPGroup(final SubscribedBoard board) {
        mBoard = board;
    }

    /**
     * Convert NNTP group name into a Freetalk board name.
     */
    public static String groupToBoardName(final String name) {
        return name;
    }

    /**
     * Convert a Freetalk board name into an NNTP group name.
     */
    public static String boardToGroupName(final String name) {
        // TODO: This does nothing at the moment.  In the future it
        // could be used to quote names in ASCII, for older
        // newsreaders that only allow ASCII group names
        return name;
    }

    /**
     * Get the FTBoard object associated with this group.
     */
    public SubscribedBoard getBoard() {
        return mBoard;
    }

    /**
     * Get the group name
     */
    public String getGroupName() {
        return boardToGroupName(mBoard.getName());
    }

    /**
     * Estimate number of messages that have been posted.
     */
    public long messageCount() {
        return mBoard.getAllMessages(false).size();
    }

    /**
     * Get the first valid message number.
     */
    public int firstMessage() {
    	try {
    		return mBoard.getFirstMessageIndex();
    	}
    	catch(NoSuchMessageException e) {
    		return 0; // TODO: Does NNTP expect this if there are no messages??
    	}
    }

    /**
     * Get the last valid message number.
     */
    public int lastMessage() {
    	try {
    		return mBoard.getLastMessageIndex();
    	}
    	catch(NoSuchMessageException e) {
    		return 0; // TODO: Does NNTP expect this if there are no messages??
    	}
    }

    /**
     * Get an iterator for articles in the given range.
     * You have to embed the call to this function and processing of the returned Iterator in a synchronized(thisGroup.getBoard())!
     */
    public Iterator<FreetalkNNTPArticle> getMessageIterator(int start, int end) throws NoSuchMessageException {
            if (start < firstMessage())
                start = firstMessage();

            if (end == -1 || end > lastMessage())
                end = lastMessage();

            final int startIndex = start;
            final int endIndex = end;

            final Iterator<FreetalkNNTPArticle> iter = new Iterator<FreetalkNNTPArticle>() {
                private int currentIndex = startIndex;
                private Message currentMessage = null;

                @Override public boolean hasNext() {
                    if (currentMessage != null)
                        return true;

                    while (currentIndex <= endIndex) {
                        try {
                            currentMessage = mBoard.getMessageByIndex(currentIndex).getMessage();
                            return true;
                        }
                        catch (MessageNotFetchedException e) {
                        	// Skip this one
                        }
                        catch (NoSuchMessageException e) { // Should not happen because endIndex == board.getLastMessageIndex();
                            Logger.error(this, "NoSuchMessageException for currentIndex (" + currentIndex + ") <= endIndex (" + endIndex + ")", e);
                        }
                        currentIndex++;
                    }
                    return false;
                }

                @Override public FreetalkNNTPArticle next() {
                    if (!hasNext())
                        throw new NoSuchElementException();
                    else {
                        Message msg = currentMessage;
                        currentMessage = null;
                        return new FreetalkNNTPArticle(msg, currentIndex++);
                    }
                }

                @Override public void remove() {
                    throw new UnsupportedOperationException();
                }
            };

            if (!iter.hasNext())
                throw new NoSuchMessageException();

            return iter;
    }

    /**
     * Get the board posting status.  This is normally either "y"
     * (posting is allowed), "n" (posting is not allowed), or "m"
     * (group is moderated.)  It is a hint to the reader and doesn't
     * necessarily indicate whether the client will be allowed to
     * post, or whether any given message will be accepted.
     */
    public String postingStatus() {
        return "y";
    }
}
