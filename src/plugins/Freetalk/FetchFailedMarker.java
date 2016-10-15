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
		checkedActivate(1);
		
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
		checkedActivate(1);
		if(newReason == null) throw new NullPointerException();
		
		mReason = newReason;
	}

	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public Date getDate() {
		checkedActivate(1); // Date is a db4o primitive type so 1 is enough
		return mDate;
	}

	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void setDate(Date newDate) {
		checkedActivate(1); // Date is a db4o primitive type so 1 is enough
		mDate = newDate;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public int getNumberOfRetries() {
		checkedActivate(1); // int is a db4o primitive type so 1 is enough
		return mNumberOfRetries;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void incrementNumberOfRetries() {
		checkedActivate(1); // int is a db4o primitive type so 1 is enough
		++mNumberOfRetries;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public Date getDateOfNextRetry() {
		checkedActivate(1); // Date is a db4o primitive type so 1 is enough
		return mDateOfNextRetry;
	}
	
	/**
	 * NOT synchronized! Lock the MessageManager when working on FetchFailedMarker objects.
	 */
	public void setDateOfNextRetry(Date newDate) {
		checkedActivate(1); // Date is a db4o primitive type so 1 is enough
		mDateOfNextRetry = newDate;
		setAllowRetryNow(!mDateOfNextRetry.after(mDate));
	}
	
	public boolean isRetryAllowedNow() {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
		return mRetryAllowedNow;
	}
	
	public void setAllowRetryNow(boolean allowRetryNow) {
		checkedActivate(1); // boolean is a db4o primitive type so 1 is enough
		mRetryAllowedNow = allowRetryNow;
	}

	@Override public void storeWithoutCommit() {
		super.storeWithoutCommit(1);
	}

	@Override public void deleteWithoutCommit() {
		super.deleteWithoutCommit(1);
		// All members are db4o native types which we don't have to delete - 
		// even mReason: Toad said that we do not have to delete enums on our own, db4o does the job.
	}

	public Reason getReason() {
		checkedActivate(1);
		return mReason;
	}

}
