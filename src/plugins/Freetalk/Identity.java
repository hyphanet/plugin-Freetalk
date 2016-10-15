/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;

public interface Identity {
	
	public String getID();
	
	/**
	 * @return The requestURI ({@link FreenetURI}) to fetch this Identity 
	 */
	public FreenetURI getRequestURI();
	
	public String getNickname();

	public String getShortestUniqueName();
	
	public String getFreetalkAddress();
	
	/**
	 * A class for representing and especially verifying identity IDs.
	 * We do not use it as a type for storing IDs in the database because that would make the queries significantly slower.
	 * We store the IDs as String.
	 */
	public static final class IdentityID {
		public static final int MAX_IDENTITY_ID_LENGTH = 64;
		
		private final String mID;
		
		private IdentityID(final String id) {
			mID = id;
		}
		
		public static final IdentityID construct(final String id) {
			if(id.length() > MAX_IDENTITY_ID_LENGTH)
				throw new IllegalArgumentException("ID is too long, length: " + id.length());
			
			try {
				final byte[] routingKey = Base64.decode(id);
				// TODO: Implement, its not important right now: FreenetURI.throwIfInvalidRoutingKey(routingKey)
			} catch (IllegalBase64Exception e) {
				throw new IllegalArgumentException("Invalid Base64 in ID: " + id);
			}

			return new IdentityID(id);
		}
		
		public static final IdentityID constructFromURI(final FreenetURI requestURI) {
			if(!requestURI.isUSK() && !requestURI.isSSK())
				throw new IllegalArgumentException("URI is no SSK/USK: " + requestURI);
			
			return new IdentityID(Base64.encode(requestURI.getRoutingKey()));
		}

		@Override public final String toString() {
			return mID;
		}

		@Override public final boolean equals(final Object o) {
			if(o instanceof IdentityID)
				return mID.equals(((IdentityID)o).mID);
			
			if(o instanceof String)
				return mID.equals((String)o);
			
			return false;
		}
	}
}
