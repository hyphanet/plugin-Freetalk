/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Date;

import freenet.support.CurrentTimeUTC;

/**
 * When a message (list) fetch fails we need to mark the message (list) as fetched to prevent the failed message (list) from getting into the
 * fetch queue over and over again. An attacker could insert many message list which have unparseable XML to fill up everyone's fetch queue
 * otherwise, this would be a denial of service.
 * 
 * When marking a message (list) as fetched even though the fetch failed, we store a Message(List)FetchFailedMarker so that we can try to 
 * fetch the message (list) again in the future. For example when the user installs a new version of the plugin we can fetch all messages(list)
 * again with failed XML parsing if the new version has fixed a bug in the XML parser.
 */
// @IndexedClass // I can't think of any query which would need to get all FetchFailedMarker objects.
public class FetchFailedMarker extends Persistent {
	
	public static enum Reason {
		Unknown,
		DataNotFound,
		ParsingFailed
	}

	private Reason mReason;
	
	private Date mDate;
	
	private int mNumberOfRetries;
	
	@IndexedField
	private Date mDateOfNextRetry;
	
	@IndexedField
	private boolean mRetryAllowedNow;


	public FetchFailedMarker(Reason myReason, Date myDate, Date myDateOfNextRetry) {
		if(myReason == null) throw new NullPointerException();
		if(myDate == null) throw new NullPointerException();
		if(myDateOfNextRetry == null) throw new NullPointerException();
		
		mReason = myReason;
		mDate = myDate;
		mNumberOfRetries = 0;
		mDateOfNextRetry = myDateOfNextRetry;
		mRetryAllowedNow = !mDateOfNextRetry.after(mDate);
	}

	@Override
	public void databaseIntegrityTest() throws Exception {
		checkedActivate(2);
		
		if(mReason == null)
			throw new NullPointerException("mReason==null");
		
		if(mDate == null)
			throw new NullPointerException("mDate==null");
		
		if(mDate.after(CurrentTimeUTC.get()))
			throw new IllegalStateException("mDate is in the future: " + mDate);
		
		if(mNumberOfRetries < 0)
			throw new IllegalStateException("mNumberOfRetries==" + mNumberOfRetries);
		
		if(mDateOfNextRetry == null)
			throw new NullPointerException("mDateOfNextRetry==null");
		
		if(mDateOfNextRetry.before(mDate))
			throw new IllegalStateException("mDateOfNextRetry is before mDate: mDateOfNextRetry==" + mDateOfNextRetry + "; mDate==" + mDate);
		
		if(mRetryAllowedNow && mDateOfNextRetry.after(CurrentTimeUTC.get()))
			throw new IllegalStateException("mRetryAllowedNow==true but date of next retry is in the future: " + mDateOfNextRetry);
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
		// activate(1);	// 1 is the default activation depth => no need to activate.
		return mDate;
	}

	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void setDate(Date newDate) {
		mDate = newDate;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public int getNumberOfRetries() {
		// activate(1);	// 1 is the default activation depth => no need to activate.
		return mNumberOfRetries;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void incrementNumberOfRetries() {
		// activate(1);	// 1 is the default activation depth => no need to activate.
		++mNumberOfRetries;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public Date getDateOfNextRetry() {
		// activate(1);	// 1 is the default activation depth => no need to activate.
		return mDateOfNextRetry;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void setDateOfNextRetry(Date newDate) {
		mDateOfNextRetry = newDate;
		setAllowRetryNow(!mDateOfNextRetry.after(mDate));
	}
	
	public boolean isRetryAllowedNow() {
		return mRetryAllowedNow;
	}
	
	public void setAllowRetryNow(boolean allowRetryNow) {
		mRetryAllowedNow = allowRetryNow;
	}

	public void storeWithoutCommit() {
		super.storeWithoutCommit(2); // TODO: Figure out a suitable depth.
	}
	
	public void deleteWithoutCommit() {
		super.deleteWithoutCommit(2); // TODO: Figure out a suitable depth.
		// FIXME XXX: Delete the mReason, I've that I delete enum objects in other places...
	}

	public Reason getReason() {
		checkedActivate(2); // TODO: Check whether this is enough for an enum, or even too much.
		return mReason;
	}

}
