/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.exceptions;

public class NoSuchMessageListException extends NoSuchObjectException {

	private static final long serialVersionUID = 1L;
	
	public NoSuchMessageListException(String messageListID) {
		super(messageListID);
	}
	
}
