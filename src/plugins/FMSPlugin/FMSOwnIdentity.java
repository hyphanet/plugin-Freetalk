/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;

/**
 * @author saces
 *
 */
public class FMSOwnIdentity extends FMSIdentity {
	
	private final String insertUri;
	private final boolean publishTrustList;

	public FMSOwnIdentity(String nickname, String requesturi, String inserturi, boolean publishtrustlist) {
		super(nickname, requesturi);
		insertUri = inserturi;
		publishTrustList = publishtrustlist;
	}

	public String getLastChange() {
		return "LastChange";
	}

	public Date getLastInsert() {
		return new Date(0);
	}

	public String getInsertURI() {
		return insertUri;
	}
	
	public boolean doesPublishTrustList() {
		return publishTrustList;
	}
	
	public final void exportXML(OutputStream out) throws IOException {
		Writer w = new BufferedWriter(new OutputStreamWriter(out));
		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		w.write("<Identity\n");
		w.write("\t<Name><![CDATA[");
		XMLUtils.writeEsc(w, getNickName());
		w.write("]]></Name>\n");
		
		w.write("\t<SingleUse>false</SingleUse>\n");
		w.write("\t<PublishTrustList>false</PublishTrustList>\n");
		w.write("\t<PublishBoardList>false</PublishBoardList>\n");

		w.write("<Identity\n");
		w.flush();
		w.close();
	}
}
