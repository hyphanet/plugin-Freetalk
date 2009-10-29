/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;

import com.db4o.ext.ExtObjectContainer;

/**
 * When a message (list) fetch fails we need to mark the message (list) as fetched to prevent the failed message (list) from getting into the
 * fetch queue over and over again. An attacker could insert many message list which have unparseable XML to fill up everyone's fetch queue
 * otherwise, this would be a denial of service.
 * 
 * When marking a message (list) as fetched even though the fetch failed, we store a Message(List)FetchFailedMarker so that we can try to 
 * fetch the message (list) again in the future. For example when the user installs a new version of the plugin we can fetch all messages(list)
 * again with failed XML parsing if the new version has fixed a bug in the XML parser.
 */
public class FetchFailedMarker {
	
	public static enum Reason {
		Unknown,
		DataNotFound,
		ParsingFailed
	}

	private Reason mReason;
	
	private Date mDate;
	
	private int mNumberOfRetries;
	
	private Date mDateOfNextRetry;
	
	public static String[] getIndexedFields() {
		return new String[] { "mDateOfNextRetry" };
	}
	
	public FetchFailedMarker(Reason myReason, Date myDate, Date myDateOfNextRetry) {
		if(myReason == null) throw new NullPointerException();
		if(myDate == null) throw new NullPointerException();
		if(myDateOfNextRetry == null) throw new NullPointerException();
		
		mReason = myReason;
		mDate = myDate;
		mNumberOfRetries = 0;
		mDateOfNextRetry = myDateOfNextRetry;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void setReason(Reason newReason) {
		if(newReason == null) throw new NullPointerException();
		
		mReason = newReason;
	}

	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public Date getDate() {
		return mDate;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public int getNumberOfRetries() {
		return mNumberOfRetries;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void incrementNumberOfRetries() {
		++mNumberOfRetries;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public Date getDateOfNextRetry() {
		return mDateOfNextRetry;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void setDateOfNextRetry(Date newDate) {
		mDateOfNextRetry = newDate;
	}
	
	protected transient ExtObjectContainer mDB;
	
	protected transient MessageManager mMessageManager;

	public void initializeTransient(ExtObjectContainer myDB, MessageManager myMessageManager) {
		mDB = myDB;
		mMessageManager = myMessageManager;
	}

	public void storeWithoutCommit() {
		try {
			DBUtil.checkedActivate(mDB, this, 3); // TODO: Figure out a suitable depth.
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			mDB.store(this);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(mDB, this, e);
		}
	}
	
	public void deleteWithoutCommit() {
		try {
			DBUtil.checkedActivate(mDB, this, 3); // TODO: Figure out a suitable depth.
			
			DBUtil.checkedDelete(mDB, this);
		}
		catch(RuntimeException e) {
			DBUtil.rollbackAndThrow(mDB, this, e);
		}
	}

	public Reason getReason() {
		return mReason;
	}
}
