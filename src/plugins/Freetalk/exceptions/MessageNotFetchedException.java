/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.exceptions;

/**
 * This exception is thrown when Freetalk has indication that a message with the given ID exists but was not able to fetch 
 * the message yet. This usually happens when a reply of a thread is downloaded before the thread message itself.
 */
public final class MessageNotFetchedException extends NoSuchMessageException {

	private static final long serialVersionUID = 1L;
	
	public MessageNotFetchedException(String id) {
		super("The message with ID " + id + " was not fetched yet");
	}

}
