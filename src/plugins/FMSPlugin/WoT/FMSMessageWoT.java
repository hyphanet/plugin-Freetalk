/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.WoT;

import java.util.Date;
import java.util.List;

import freenet.keys.FreenetURI;
import plugins.FMSPlugin.FMSIdentity;
import plugins.FMSPlugin.FMSMessage;

/**
 * @author xor
 *
 */
public class FMSMessageWoT extends FMSMessage {

	/**
	 * @param newURI
	 * @param newAuthor
	 * @param newTitle
	 * @param newDate
	 */
	public FMSMessageWoT(FreenetURI newURI, FMSIdentity newAuthor, String newTitle, Date newDate) {
		super(newURI, newAuthor, newTitle, newDate);
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see plugins.FMSPlugin.FMSMessage#getAttachments()
	 */
	@Override
	public List<FreenetURI> getAttachments() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see plugins.FMSPlugin.FMSMessage#getText()
	 */
	@Override
	public String getText() {
		// TODO Auto-generated method stub
		return null;
	}

}
