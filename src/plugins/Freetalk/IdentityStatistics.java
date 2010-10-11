/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import plugins.Freetalk.MessageList.MessageListID;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import freenet.support.Logger;

/**
 * This class stores statistics about a single identity.
 * It is used for {@link MessageList} and {@link Message} download decisions.
 * It also speeds up Freetalk by caching the results of complex database queries.
 * 
 * @author xor (xor@freenetproject.org)
 */
public class IdentityStatistics extends Persistent {
	
	private final Identity mIdentity;

	/**
	 * The index of the latest message list which is available for this identity
	 */
	private long mHighestFetchedMessageListIndex = -1;
	
	/**
	 * The lowest index of which the message list is fetched and all message lists after that index are fetched aswell.
	 */
	private long mLowestFetchedMessageListIndex = -1;
	
	
	protected IdentityStatistics(final Identity myIdentity) {
		mIdentity = myIdentity;
	}
	

	/**
	 * Get the highest message list index number which we have seen from this identity.
	 * It is guaranteed that no slot between the highest and lowest reported index is empty - in other words: IdentityStatistics will keep
	 * its numbers of highest and lowest fetched index in a way so that all message lists in between are fetched.
	 * If there is a slot missing, the statistics object tries to chose the highest/lowest numbers biased towards the higher available
	 * fragment instead of the lower one.
	 * 
	 * It is NOT guaranteed that all message lists above the returned number are not fetched, some might exist in the database already!
	 * - The numbers returned by this function are supposed to be used for message list fetching, its not bad if we fetch the same message list
	 * twice.
	 */
	public synchronized long getIndexOfLatestAvailableMessageList() throws NoSuchMessageListException {
		if(mHighestFetchedMessageListIndex == -1)
			throw new NoSuchMessageListException(null);
		
		return mHighestFetchedMessageListIndex;
	}
	
	protected synchronized void setIndexOfLatestAvailableMessageList(long index) {
		if(index < mLowestFetchedMessageListIndex)
			throw new RuntimeException("Illegal index: "+ index);
		
		mHighestFetchedMessageListIndex = index;
		assert(messageListIndicesAreValid());
		storeWithoutCommit();
	}
	
	/**
	 * Get the lowest message list index number which we have seen from this identity.
	 * It is guaranteed that no slot between the highest and lowest reported index is empty - in other words: IdentityStatistics will keep
	 * its numbers of highest and lowest fetched index in a way so that all message lists in between are fetched.
	 * If there is a slot missing, the statistics object tries to chose the highest/lowest numbers biased towards the higher available
	 * fragment instead of the lower one.
	 * 
	 * It is NOT guaranteed that all message lists below the returned number are not fetched, some might exist in the database already!
	 * - The numbers returned by this function are supposed to be used for message list fetching, its not bad if we fetch the same message list
	 * twice.
	 */
	public synchronized long getIndexOfOldestAvailableMessageList() throws NoSuchMessageListException {
		if(mLowestFetchedMessageListIndex == -1)
			throw new NoSuchMessageListException(null);
		
		return mLowestFetchedMessageListIndex;
	}
	
	protected synchronized void setIndexOfOldestAvailableMessageList(long index) {
		if(index > mHighestFetchedMessageListIndex)
			throw new RuntimeException("Illegal index: " + index);
		
		mLowestFetchedMessageListIndex = index;
		assert(messageListIndicesAreValid());
		storeWithoutCommit();
	}
	
	private void expandHighestAvailableMessageListIndex() {
		final MessageManager messageManager = mFreetalk.getMessageManager();
		
		// TODO: Optimization: We should observe the typical amount of lists which are skipped by this function. If it is high then it 
		// might be faster to query a sorted set of the message lists with higher index instead of skipping them one by one....
		
		boolean higherIndexWasValid;
		
		do {
			long higherIndex = mHighestFetchedMessageListIndex+1;
			
			try {
				messageManager.getMessageList(MessageListID.construct(mIdentity, higherIndex).toString());
				higherIndexWasValid = true;
				mHighestFetchedMessageListIndex = higherIndex;
			} catch(NoSuchMessageListException e) {
				higherIndexWasValid = false;
			}
		} while(higherIndexWasValid);
		
		assert(messageListIndicesAreValid());
	}
	
	private void expandLowestAvailableMessageListIndex() {
		final MessageManager messageManager = mFreetalk.getMessageManager();
		
		// TODO: Optimization: We should observe the typical amount of lists which are skipped by this function. If it is high then it 
		// might be faster to query a sorted set of the message lists with higher index instead of skipping them one by one....
		
		boolean lowerIndexWasValid;
		
		do {
			long lowerIndex = mLowestFetchedMessageListIndex-1;
			
			try {
				messageManager.getMessageList(MessageListID.construct(mIdentity, lowerIndex).toString());
				lowerIndexWasValid = true;
				mLowestFetchedMessageListIndex = lowerIndex;
			} catch(NoSuchMessageListException e) {
				lowerIndexWasValid = false;
			}
		} while(lowerIndexWasValid && mLowestFetchedMessageListIndex > 0);
		
		assert(messageListIndicesAreValid());
	}
	
	protected void onMessageListFetched(final MessageList messageList) {
		if(messageList instanceof OwnMessageList)
			throw new RuntimeException("OwnMessageList are not allowed: " + messageList);
		
		final long newIndex = messageList.getIndex();
		
		if(newIndex >= mLowestFetchedMessageListIndex && newIndex <= mHighestFetchedMessageListIndex) {
			Logger.warning(this, "MessageList was fetched already: " + messageList);
			return;
		}
		
		if(newIndex > mHighestFetchedMessageListIndex+1) {
			// When the list of fetched indices is fragmented, there are two possible biases which this function can have:
			// Set its boundaries to the lowest available fragment or to the highest available fragment. 
			// - We set the boundaries to the highest available fragment so the message list fetcher fetches new message lists fast.
			setIndexOfLatestAvailableMessageList(newIndex);
			setIndexOfOldestAvailableMessageList(newIndex);
			expandHighestAvailableMessageListIndex();
			expandLowestAvailableMessageListIndex();
			return;
		}
		
		if(newIndex == mHighestFetchedMessageListIndex+1) {
			setIndexOfLatestAvailableMessageList(newIndex);
			expandHighestAvailableMessageListIndex();
			return;
		}
		
		if(newIndex == mLowestFetchedMessageListIndex-1) {
			setIndexOfOldestAvailableMessageList(newIndex);
			expandLowestAvailableMessageListIndex();
			return;
		}
		
		if(newIndex < mLowestFetchedMessageListIndex-1) {
			// The mLowestFetchedMessageListIndex-1 index will definitely not be available, no need to check
			// expandLowestAvailableMessageListIndex();
			return;
		}
		
		// This should not happen:
		throw new RuntimeException("Unhandled case: lowest index=" + mLowestFetchedMessageListIndex + "; "
				+ "highest index=" + mHighestFetchedMessageListIndex + "; "
				+ "new index=" + newIndex);
	}
	
	protected void onMessageListDeleted(final MessageList messageList) {
		if(messageList instanceof OwnMessageList)
			throw new RuntimeException("OwnMessageList are not allowed: " + messageList);
		
		final long deletedIndex = messageList.getIndex();
		
		if(deletedIndex < mLowestFetchedMessageListIndex || deletedIndex > mHighestFetchedMessageListIndex)
			return;
		
		if(mLowestFetchedMessageListIndex == mHighestFetchedMessageListIndex) {
			// TODO: Optimization: If this happens often and in a specific order during identity deletion it might cause O(n*n)...
			// Check whether we need to do something against that.
			setIndexOfOldestAvailableMessageList(-1);
			setIndexOfLatestAvailableMessageList(-1);
			expandHighestAvailableMessageListIndex();
			return;
		}
	
		
		setIndexOfLatestAvailableMessageList(deletedIndex-1);	
		setIndexOfOldestAvailableMessageList(deletedIndex+1);
		
		// This should not happen:
		throw new RuntimeException("Unhandled case: lowest index=" + mLowestFetchedMessageListIndex + "; "
				+ "highest index=" + mHighestFetchedMessageListIndex + "; "
				+ "deleted index=" + deletedIndex);
	}
	
	private boolean messageListIndicesAreValid() {
		final MessageManager messageManager = mFreetalk.getMessageManager();
		
		for(long i = mLowestFetchedMessageListIndex; i <= mHighestFetchedMessageListIndex; ++i) {
			try  {
				messageManager.getMessageList(MessageListID.construct(mIdentity, i).toString());
			} catch(NoSuchMessageListException e) {
				Logger.error(this, "Wrong index numbers found: lowest==" + mLowestFetchedMessageListIndex + 
						"; highest==" + mHighestFetchedMessageListIndex);
				return false;
			}
		}
		
		return true;
	}

}
