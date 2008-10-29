/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import com.db4o.ObjectContainer;
import freenet.clients.http.PageMaker;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.io.TempBucketFactory;

/**
 * @author saces
 *
 */
public class FMS {
	public final PageMaker pm;
	public final PluginRespirator pr;
	public final ObjectContainer db_config;
	public final ObjectContainer db_cache;
	public final TempBucketFactory tempBF;
	
	FMS(TempBucketFactory tempBucketFactory, PageMaker pagemaker, PluginRespirator pluginrespirator, ObjectContainer dbConfig, ObjectContainer dbCache){
		pm = pagemaker;
		pr = pluginrespirator;
		db_config = dbConfig;
		db_cache = dbCache;
		tempBF = tempBucketFactory;
	}
	
	final public HTMLNode getPageNode() {
		return pm.getPageNode(FMSPlugin.SELF_TITLE, null);
	}

	/* ----- utils ----- */
	public boolean isWoTpresent() {
		FredPluginFCP plug = pr.getNode().pluginManager.getFCPPlugin(FMSPlugin.WOT_NAME);
		return (plug != null);
	}

	public long countIdentities() {
		return db_config.queryByExample(FMSIdentity.class).size() - countOwnIdentities();
	}

	public long countOwnIdentities() {
		return db_config.queryByExample(FMSOwnIdentity.class).size();
	}
}
