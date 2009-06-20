/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Iterator;
import java.util.HashSet;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.Board.MessageReference;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

public class Wiki {

	protected FTOwnIdentity mOwnIdentity;
	protected Freetalk mFreetalk;
	protected String mPage;
	protected Board mBoard;
	protected Message mThread;
	protected Message mLastMessage;
	protected String mText;
	protected boolean mExists;

	public Wiki(FTOwnIdentity viewer, Freetalk freetalk, String page) {
		mOwnIdentity = viewer;
		mFreetalk = freetalk;
		mPage = page;
		try {
			mBoard = mFreetalk.getMessageManager().getOrCreateBoard("en.test.wiki");
		} catch (InvalidParameterException e){
			// wtf?
		}
		try {
			mThread = getWikiThread(mBoard, mPage);
			mLastMessage = getMostRecentMessage(mThread);
			mText = mLastMessage.getText();
			mExists = true;
		} catch (NoSuchMessageException e) {
			mThread = null;
			mLastMessage = null;
			mText = "";
			mExists = false;
		}
	}

	private Message getWikiThread(Board board, String subject) throws NoSuchMessageException {
		synchronized(board) {
			for(MessageReference threadReference : board.getThreads(mOwnIdentity)) {
				Message thread = threadReference.getMessage();
				// we are looking for a thread with subject mSubject
				if(thread.getTitle().equals(subject)) {
					// found!
					return thread;
				}
			}
		}
		throw new NoSuchMessageException();
	}

	private Message getMostRecentMessage(Message thread) throws NoSuchMessageException {
		Message last = null;

		for(MessageReference reference : mBoard.getAllThreadReplies(thread, true)) {
			last = reference.getMessage();
		}
		if(last != null) {
			return last;
		}
		return thread;
	}

	public String getText() {
		return mText;
	}

	public boolean doesExist() {
		return mExists;
	}

	public void uploadNewVersion(String text) {
		HashSet<Board> boards = new HashSet<Board>();
		boards.add(mBoard);
		try {
			mFreetalk.getMessageManager().postMessage(mLastMessage, boards, mBoard, mOwnIdentity, mPage, null, text, null);
		} catch (InvalidParameterException e){
			// wtf?
		} catch (Exception e){
			// wtf?
		}
	}
}
