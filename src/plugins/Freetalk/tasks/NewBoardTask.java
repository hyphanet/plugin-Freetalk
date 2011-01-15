/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.tasks;

import plugins.Freetalk.Board;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.ui.web.WebInterface;
import plugins.Freetalk.ui.web.WebPage;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;
import freenet.support.codeshortification.IfNull;

/**
 * A task which is stored each time a new {@link Board} is created.
 * It subscribes all OwnIdentities to the board who have auto subscription enabled.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class NewBoardTask extends PersistentTask {
	
	final String mBoardName;

	public NewBoardTask(Board board) {
		super(null);
		mBoardName = board.getName();
		mNextProcessingTime = 0;
		mNextDisplayTime = Long.MAX_VALUE;
		mDeleteTime = Long.MAX_VALUE;
	}
	
	@Override
	public void databaseIntegrityTest() throws Exception {
		IfNull.thenThrow(mBoardName, "mBoardName");
		
		if(!Board.isNameValid(mBoardName))
			throw new IllegalStateException("Invalid board name: " + mBoardName);
	}

	@Override
	protected void process() {
		mNextProcessingTime = Long.MAX_VALUE;
		mDeleteTime = 0;
		
		MessageManager messageManager = mFreetalk.getMessageManager();
		
		try {
			messageManager.getBoardByName(mBoardName);
		} catch(NoSuchBoardException e) {
			Logger.normal(this, "Not processing NewBoardTask: Board does not exist anymore: " + mBoardName);
			storeAndCommit();
			return;
		}
		
		Logger.normal(this, "Auto-Subscribing all own identities to the new board " + mBoardName  + " ...");
		
		for(OwnIdentity identity : mFreetalk.getIdentityManager().ownIdentityIterator()) {
			if(!identity.wantsAutoSubscribeToNewBoards())
				continue;
			
			try {
				messageManager.subscribeToBoard(identity, mBoardName); // Does not throw if subscription exists.
			} catch (Exception e) {
				Logger.error(this, "Auto-subscribing to board failed", e);
				mNextProcessingTime = CurrentTimeUTC.getInMillis() + 10 * 60 * 1000;
				mDeleteTime = Long.MAX_VALUE;
			}
		}
		
		Logger.normal(this, "Finished auto subcribing.");
		
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
