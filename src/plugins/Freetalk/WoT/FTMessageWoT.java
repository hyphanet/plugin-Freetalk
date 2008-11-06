/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.util.Date;
import java.util.List;
import java.util.Set;

import plugins.Freetalk.FTBoard;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTMessage;

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;

/**
 * @author xor
 *
 */
public class FTMessageWoT extends FTMessage {

	public FTMessageWoT(ObjectContainer myDB, FreenetURI newURI, FreenetURI newThreadURI, FreenetURI newParentURI, Set<FTBoard> newBoards, FTIdentity newAuthor,
			String newTitle, Date newDate, String newText, List<FreenetURI> newAttachments) {
		super(myDB, newURI, newThreadURI, newParentURI, newBoards, newAuthor, newTitle, newDate, newText, newAttachments);
		// TODO Auto-generated constructor stub
	}


}
