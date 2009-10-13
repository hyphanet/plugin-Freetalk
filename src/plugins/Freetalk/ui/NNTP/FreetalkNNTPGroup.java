/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import java.util.Iterator;
import java.util.NoSuchElementException;

import plugins.Freetalk.Message;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.exceptions.NoSuchMessageException;

/**
 * Object representing a newsgroup, as seen from the NNTP client's
 * point of view.
 *
 * @author Benjamin Moody
 */
public class FreetalkNNTPGroup {
    private final SubscribedBoard board;

    public FreetalkNNTPGroup(SubscribedBoard board) {
        this.board = board;
    }

    /**
     * Convert NNTP group name into a Freetalk board name.
     */
    public static String groupToBoardName(String name) {
        // FIXME: This does nothing at the moment.  In the future it
        // could be used to quote names in ASCII, for older
        // newsreaders that only allow ASCII group names
        return name;
    }

    /**
     * Convert a Freetalk board name into an NNTP group name.
     */
    public static String boardToGroupName(String name) {
        return name;
    }

    /**
     * Get the FTBoard object associated with this group.
     */
    public SubscribedBoard getBoard() {
        return board;
    }

    /**
     * Get the group name
     */
    public String getName() {
        return boardToGroupName(board.getName());
    }

    /**
     * Estimate number of messages that have been posted.
     */
    public long messageCount() {
        return board.getAllMessages(false).size();
    }

    /**
     * Get the first valid message number.
     */
    public int firstMessage() {
        return 1;
    }

    /**
     * Get the last valid message number.
     */
    public int lastMessage() {
        return board.getLastMessageIndex();
    }

    /**
     * Get an iterator for articles in the given range.
     */
    public Iterator<FreetalkNNTPArticle> getMessageIterator(int start, int end) throws NoSuchMessageException {
        synchronized (board) {
            if (start < firstMessage())
                start = firstMessage();

            if (end == -1 || end > lastMessage())
                end = lastMessage();

            Iterator<FreetalkNNTPArticle> iter;

            final int startIndex = start;
            final int endIndex = end;

            iter = new Iterator<FreetalkNNTPArticle>() {
                private int currentIndex = startIndex;
                private Message currentMessage = null;

                public boolean hasNext() {
                    if (currentMessage != null)
                        return true;

                    while (currentIndex <= endIndex) {
                        try {
                            currentMessage = board.getMessageByIndex(currentIndex);
                            return true;
                        }
                        catch (NoSuchMessageException e) {
                            // ignore
                        }
                        currentIndex++;
                    }
                    return false;
                }

                public FreetalkNNTPArticle next() {
                    if (!hasNext())
                        throw new NoSuchElementException();
                    else {
                        Message msg = currentMessage;
                        currentMessage = null;
                        return new FreetalkNNTPArticle(msg, currentIndex++);
                    }
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };

            if (!iter.hasNext())
                throw new NoSuchMessageException();

            return iter;
        }
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
