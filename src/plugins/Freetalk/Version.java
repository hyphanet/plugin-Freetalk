/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

/**
 * @author xor (xor@freenetproject.org)
 * @author saces
 *
 */
public class Version {

	public static final String gitRevision = "@custom@";

	/** Version number of the plugin for getRealVersion(). Increment this on making
	 * a major change, a significant bugfix etc. These numbers are used in auto-update 
	 * etc, at a minimum any build inserted into auto-update should have a unique 
	 * version. */
	public static long version = 13;
	
	/** Published as an identity property if you own a seed identity. */
	public static final long mandatoryVersion = 1;
	
	/** Published as an identity property if you own a seed identity. */
	public static final long latestVersion = version;
	

	public static final String longVersionString = "0.1 "+gitRevision;

	public static long getRealVersion() {
		return version;
	}
}

