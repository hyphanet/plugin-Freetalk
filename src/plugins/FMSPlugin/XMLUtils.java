/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.io.IOException;
import java.io.Writer;

/**
 * @author saces
 *
 */
public class XMLUtils {
	
	public final static void writeEsc(Writer w, String s) throws IOException {
		writeEsc(w, s, false);
	}
	
	private static void writeEsc(Writer w, String s, boolean isAttVal) throws IOException {
		for (int i = 0; i < s.length(); i++) {
			switch (s.charAt(i)) {
			case '&':
				w.write("&amp;");
				break;
			case '<':
				w.write("&lt;");
				break;
			case '>':
				w.write("&gt;");
				break;
			case '\"':
				if (isAttVal) {
					w.write("&quot;");
				} else {
					w.write('\"');
				}
				break;
			default:
				w.write(s.charAt(i));
			}
		}
	}
}

