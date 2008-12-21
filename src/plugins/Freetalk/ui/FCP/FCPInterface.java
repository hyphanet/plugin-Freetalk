/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.FCP;

import plugins.Freetalk.Freetalk;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public final class FCPInterface implements FredPluginFCP {

	private final Freetalk mFreetalk;
	
	public FCPInterface(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
	}

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Hello", "Nice try ;)");
		sfs.putOverwrite("Sorry", "Not implemeted yet :(");
	}

}
