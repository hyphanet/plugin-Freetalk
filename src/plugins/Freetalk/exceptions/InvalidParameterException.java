/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.exceptions;

/* 
 * TODO: Isn't there any standard java exception for that purpose?
 * IllegalArgumentException is unfortunately a RuntimeException, same
 * for the java.security.InvalidParameterException.
 */


/**
 * Thrown the user supplied an invalid parameter.
 * 
 * @author Julien Cornuwel (batosai@freenetproject.org)
 *
 */
public final class InvalidParameterException extends Exception {
	
	private static final long serialVersionUID = -1;

	public InvalidParameterException(String message) {
		super(message);
	}
}
