/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import static java.lang.System.out;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.support.Logger;
import freenet.support.io.Closer;

/** Provides this Freetalk binary's version numbers in three different levels of rawness:
 *  - {@link #getGitRevision()}
 *  - {@link #getRealVersion()}
 *  - {@link #getMarketingVersion()}
 *  
 *  Is used by class {@link Freetalk} to implement fred's interfaces for plugins to specify their
 *  version, for purposes of the auto-update mechanism and "Plugins" page on the web interface:
 *  - {@link FredPluginRealVersioned}
 *  - {@link FredPluginVersioned}
 *  
 *  NOTICE: The member variable {@link #version} must be manually incremented before every release
 *  to the auto-update mechanism of fred!  
 *  The member variable {@link #marketingVersion} may be, but need not be, manually incremented. */
public final class Version {

	/** This is replaced by the Ant build script during compilation.  
	 *  It thus must be private and only accessible through a getter function to ensure
	 *  its pre-replacement default value does not get inlined into the code of other classes!
	 *  
	 *  @see #getGitRevision()
	 *  @deprecated Use the file "plugins/Freetalk/Version.properties" instead. This ensures build
	 *      scripts such as especially the Gradle one require less complexity to specify the
	 *      revision. See {@link #getGitRevision()} for what to put into the file.  
	 *      TODO: Code quality: Remove once we remove the Ant builder. */
	@Deprecated
	private static final String gitRevision = "@custom@";

	/** The {@link FredPluginRealVersioned#getRealVersion()} aka build number.  
	 *  NOTICE: This is used by fred's auto-update code to distinguish different versions, so it
	 *  MUST be incremented on **every** release.
	 *  
	 *  @see #getRealVersion() */
	private static final long version = 13;

	/** The version which we tell the user for advertising purposes, e.g. by incrementing the major
	 *  version on important new features.  
	 *  In opposite to {@link #version} this does not have to be changed on every release.
	 *  
	 *  @see FredPluginVersioned
	 *  @see #getMarketingVersion() */
	private static final String marketingVersion = "0.1";


	/** Returns the most raw way of describing the Freetalk version: The output of
	 *      git describe --always --abbrev=4 --dirty
	 *  as observed by the Ant or Gradle build script which was used to compile Freetalk.
	 *  
	 *  If no commits have been added since the last git tag, this will be equal to the name of
	 *  the last git tag. Thus, by tagging releases with "buildXXXX" where XXXX is equal to a
	 *  zero-padded {@link #getRealVersion()}, each release will have this return a clean string
	 *  "buildXXXX" instead of including raw commit info.
	 *  
	 *  The Gradle build script must add the file "plugins/Freetalk/Version.properties" to the JAR
	 *  to power this function. In that file it must set the key "git.revision" to the output of the
	 *  above git command line.  
	 *  The file must be encoded in ISO 8859-1. */
	public static String getGitRevision() {
		// If the legacy gitRevision field was populated by Ant then use it because there won't
		// be a "Version.properties" file to load it from, Ant doesn't generate it.
		// ("@custom@" must be written in a non-literal fashion here to prevent Ant from replacing
		// it in the equals() parameter!)
		if(!gitRevision.equals("@" + "custom" + "@"))
			return gitRevision;
		
		InputStream s = null;
		String loadedRevision = "ERROR-while-loading-git-revision";
		// NOTICE: This try{} block must catch very thoroughly so we don't break Freenet's "Plugins"
		// page on the web interface and thus prevent users from unloading broken Freetalk versions!
		try {
			s = Version.class.getResourceAsStream("/plugins/Freetalk/Version.properties");
			if(s == null) 
				throw new IOException("Version.properties not found or not accessible!");
			
			Properties p = new Properties();
			p.load(s);
			
			String value = p.getProperty("git.revision");
			if(value == null)
				throw new RuntimeException("git.revision missing in Version.properties!");
			
			loadedRevision = value;
		} catch(IllegalArgumentException e) {
			Logger.error(Version.class, "getGitRevision(): Properties.load() failed: "
				+ "InputStream contains malformed character encoding. Should be in ISO 8859-1!", e);
		} catch(IOException | RuntimeException | Error e) {
			Logger.error(Version.class, "getGitRevision() failed!", e);
		} finally {
			Closer.close(s);
		}
		
		return loadedRevision;
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
	 *  increased (or kept as is) to represent development progress.  
	 *  It is concatenated with {@link #getGitRevision()}.
	 *  
	 *  Because {@link #getGitRevision()} should have the clean format of "buildXXXX" for releases,
	 *  the resulting string should be easy to read for users, such as "1.2.3 build0456".  
	 *  It notably includes **all** version info of this class:
	 *  - the marketing version
	 *  - the git revision
	 *  - {@link #getRealVersion()} via the "buildXXXX" included in the git revision. */
	public static String getMarketingVersion() {
		return marketingVersion + " " + getGitRevision();
	}

	/** Can be used to obtain the version if Freetalk is broken and won't load via Freenet's plugins
	 *  page.  
	 *  On Linux run it via:
	 *      cd /path/of/Freenet
	 *      for JAR in plugins/Freetalk.jar* ; do
	 *          echo "Versions of $JAR:"
	 *          java -classpath "${JAR}:freenet.jar" plugins.Freetalk.Version
	 *      done */
	public static void main(String[] args) {
		out.println("Marketing version: " + getMarketingVersion());
		out.println("Real version: " + getRealVersion());
		out.println("Git revision: " + getGitRevision());
	}

}

