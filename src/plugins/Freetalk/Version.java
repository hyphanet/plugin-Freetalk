/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import static java.lang.System.out;

import freenet.pluginmanager.FredPluginRealVersioned;

public final class Version {

	/** This is replaced by the Ant/Gradle build scripts during compilation.
	 *  It thus must be private and only accessible through a getter function to ensure
	 *  its pre-replacement default value does not get inlined into the code of other classes!
	 *  
	 *  @see #getGitRevision() */
	private static final String gitRevision = "@custom@";

	/** The {@link FredPluginRealVersioned#getRealVersion()} aka build number.
	 *  NOTICE: This is used by fred's auto-update code to distinguish different versions, so it
	 *  MUST be incremented on **every** release.
	 *  
	 *  @see #getRealVersion() */
	private static final long version = 13;

	private static final String marketingVersion = "0.1";


	/** Returns the most raw way of describing the Freetalk version: The output of
	 *      git describe --always --abbrev=4 --dirty
	 *  as observed by the Ant or Gradle build script which was used to compile Freetalk.
	 *  
	 *  If no commits have been added since the last git tag, this will be equal to the name of
	 *  the last git tag. Thus, by tagging releases with "buildXXXX" where XXXX is equal to a
	 *  zero-padded {@link #getRealVersion()}, each release will have this return a clean string
	 *  "buildXXXX" instead of including raw commit info. */
	public static String getGitRevision() {
		return gitRevision;
	}

	/** Returns a less raw way of describing the Freetalk version:
	 *  The sequential "build number", i.e. the number of the last Freetalk testing or stable
	 *  release which this codebase is equal to or greater than (= includes additional commits).
	 *  
	 *  Freenet uses this for the auto-update mechanism.
	 *  
	 *  Thus maintainers MUST increase this number by exactly 1 whenever they do a release so
	 *  it can be used to determine if one release is more recent than another. */
	public static long getRealVersion() {
		return version;
	}

	/** Returns the least raw way of describing the Freetalk version:
	 *  A hand-picked "marketing" version name such as "1.2.3", which is freely chosen and
	 *  increased to represent development progress.  
	 *  It is concatenated with {@link #getGitRevision()}.
	 *  
	 *  Because {@link #getGitRevision()} should have the clean format of "buildXXXX" for releases,
	 *  the resulting string should be easy to read for users, such as "1.2.3 build0456". */
	public static String getMarketingVersion() {
		return marketingVersion + " " + getGitRevision();
	}

	/** Can be used to obtain the version if Freetalk is broken and won't load via Freenet's plugins
	 *  page.
	 *  On Linux run it via:
	 *      cd /path/of/Freenet
	 *      for JAR in plugins/Freetalk.jar* ; do
	 *          echo "Versions of $JAR:"
	 *          java -classpath "$JAR" plugins.Freetalk.Version
	 *      done */
	public static void main(String[] args) {
		out.println("Marketing version: " + getMarketingVersion());
		out.println("Real version: " + getRealVersion());
		out.println("Git revision: " + getGitRevision());
	}

}

