/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.exceptions;

public final class DuplicateMessageException extends DuplicateElementException {

	private static final long serialVersionUID = 1L;

	public DuplicateMessageException(String threadID) {
		super("Duplicate message: " + threadID);
	}
}
