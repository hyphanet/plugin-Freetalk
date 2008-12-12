/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.NoSuchBoardException;

import com.db4o.ObjectContainer;

import freenet.support.Executor;
import freenet.support.Logger;

public class WoTMessageManager extends MessageManager {
	
	/* FIXME: This really has to be tweaked before release. I set it quite short for debugging */
	private static final int THREAD_PERIOD = 5 * 60 * 1000;

	private volatile boolean isRunning = true;
	private Thread mThread;
	
	private static final Calendar mCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	public WoTMessageManager(ObjectContainer myDB, Executor myExecutor, WoTIdentityManager myIdentityManager) {
		super(myDB, myExecutor, myIdentityManager);
		mIdentityManager = myIdentityManager;
		Logger.debug(this, "Message manager started.");
	}
	
	public WoTMessageManager(ObjectContainer myDB) {
		super(myDB);
	}

	public OwnMessage postMessage(Message myParentMessage, Set<Board> myBoards, Board myReplyToBoard, FTOwnIdentity myAuthor,
			String myTitle, String myText, List<Attachment> myAttachments) {
		OwnMessage m;
		
		synchronized(OwnMessage.class) {
			Date date = mCalendar.getTime();
			Message parentThread = myParentMessage!= null ? myParentMessage.getThread() : null;
			int index = getFreeMessageIndex(myAuthor, date);
			
			m = new OwnMessage(parentThread, myParentMessage, myBoards, myReplyToBoard, myAuthor, myTitle, date, index,
				myText, myAttachments);
			
			m.initializeTransient(db, this);
			
			m.store();

			for (Iterator<Board> i = myBoards.iterator(); i.hasNext(); ) {
				Board board = i.next();
				board.addMessage(m);
			}
		}
		
		return m;
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
