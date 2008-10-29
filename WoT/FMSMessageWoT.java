/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.WoT;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;

import freenet.keys.FreenetURI;
import plugins.FMSPlugin.FMSBoard;
import plugins.FMSPlugin.FMSIdentity;
import plugins.FMSPlugin.FMSMessage;

/**
 * @author xor
 *
 */
public class FMSMessageWoT extends FMSMessage {


	public FMSMessageWoT(FreenetURI newURI, SortedSet<FMSBoard> newBoards, FMSIdentity newAuthor, String newTitle, Date newDate) {
		super(newURI, newBoards, newAuthor, newTitle, newDate);
		// TODO Auto-generated constructor stub
	}

	@Override
	public List<FreenetURI> getAttachments() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getText() {
		// TODO Auto-generated method stub
		return null;
	}


}
