package plugins.Freetalk.tasks;
/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
import plugins.Freetalk.Board;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.ui.web.WebInterface;
import plugins.Freetalk.ui.web.WebPage;
import plugins.Freetalk.util.CurrentTimeUTC;
import freenet.support.codeshortification.IfNull;

/**
 * Subscribes the owner of this task to all existing boards.
 * Used when a new {@link OwnIdentity} is created.
 * 
 * TODO: Wire in to the SelectBoardsPage with a "Subscribe to all boards" button
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class SubscribeToAllBoardsTask extends PersistentTask {

	public SubscribeToAllBoardsTask(OwnIdentity myOwner) {
		super(myOwner);
		IfNull.thenThrow(myOwner, "Owner cannot be null");
		
		mNextProcessingTime = 0;
		mNextDisplayTime = Long.MAX_VALUE;
		mDeleteTime = Long.MAX_VALUE;
	}
	
	@Override
	public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		checkedActivate(1);
		IfNull.thenThrow(mOwner);
	}

	@Override
	protected void process() {
		checkedActivate(1);
		
		mNextProcessingTime = Long.MAX_VALUE;
		mDeleteTime = 0;
		
		final MessageManager messageManager = mFreetalk.getMessageManager();
		
		// TODO: Optimization: Use unsorted iterator, there is none right now.
		for(Board board : messageManager.boardIteratorSortedByName()) {
			try {
				messageManager.subscribeToBoard(getOwner(), board.getName()); // Does not throw if subscription exists.
			} catch(Exception e) {
				mNextProcessingTime = CurrentTimeUTC.getInMillis() + 10 * 60 * 1000;
				mDeleteTime = Long.MAX_VALUE;
			}
		}
		
		storeAndCommit();
	}

	@Override
	public WebPage display(WebInterface myWebInterface) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onHideForSomeTime() {
		throw new UnsupportedOperationException();
	}

}
