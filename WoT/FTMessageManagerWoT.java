/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.NoSuchElementException;

import plugins.Freetalk.FTBoard;
import plugins.Freetalk.FTMessage;
import plugins.Freetalk.FTMessageManager;
import plugins.Freetalk.exceptions.InvalidParameterException;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;
import freenet.support.Executor;
import freenet.support.Logger;

public class FTMessageManagerWoT extends FTMessageManager {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 5 * 60 * 1000;
	
	private FTIdentityManagerWoT mIdentityManager;
	
	private volatile boolean isRunning = true;
	private Thread mThread;

	public FTMessageManagerWoT(ObjectContainer myDB, Executor myExecutor, FTIdentityManagerWoT myIdentityManager) {
		super(myDB, myExecutor, myIdentityManager);
		mIdentityManager = myIdentityManager;
		Logger.debug(this, "Message manager started.");
	}

	protected synchronized void deleteMessage(FreenetURI uri) throws NoSuchElementException {
		/* FIXME: implement */
	}

	private synchronized void onMessageReceived(String newMessageData) throws InvalidParameterException { 
		FTMessage newMessage = new FTMessage(null, null, null, null, null, null, null, null, null);
		newMessage.initializeTransient(db, this);
		String boardName = "";
		/* FIXME: Store the description in FTOwnIdentity. We cannot store in FTBoard because we want to allow per-identity customization */

		String[] boardNames = new String[0];
		FTBoard[] boards = new FTBoard[boardNames.length];
		                                    
		for(int idx = 0; idx < boards.length; ++idx) {
			FTBoard board = getBoardByName(boardNames[idx]);
			
			if(board == null) {
				board = new FTBoard(this, boardName);
				board.initializeTransient(db, this);
			}
			
			boards[idx] = board;
		}
		
		for(FTBoard b : boards) {
			b.addMessage(newMessage);
		}
	}
	
	public void run() {
		Logger.debug(this, "Message manager running.");
		mThread = Thread.currentThread();
		
		try {
			Logger.debug(this, "Waiting for the node to start up...");
			Thread.sleep((long) (3*60*1000 * (0.5f + Math.random()))); /* Let the node start up */
		}
		catch (InterruptedException e)
		{
			mThread.interrupt();
		}
		
		while(isRunning) {
			Logger.debug(this, "Message manager loop running...");

			Logger.debug(this, "Message manager loop finished.");

			try {
				Thread.sleep((long) (THREAD_PERIOD * (0.5f + Math.random())));
			}
			catch (InterruptedException e)
			{
				mThread.interrupt();
				Logger.debug(this, "Message manager loop interrupted!");
			}
		}
		Logger.debug(this, "Message manager thread exiting.");
	}
	
	public void terminate() {
		Logger.debug(this, "Stopping the message manager..."); 
		isRunning = false;
		mThread.interrupt();
		try {
			mThread.join();
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		Logger.debug(this, "Stopped the message manager.");
	}
}
