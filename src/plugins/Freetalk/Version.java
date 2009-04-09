/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

/**
 * @author saces
 *
 */
public class Version {
	/** SVN revision number. Only set if the plugin is compiled properly e.g. by emu. */
	static final String svnRevision = "@custom@";
	/** Version number of the plugin for getRealVersion(). Increment this on making
	 * a major change, a significant bugfix etc. These numbers are used in auto-update 
	 * etc, at a minimum any build inserted into auto-update should have a unique 
	 * version. */
	public static long version = 1;
}

