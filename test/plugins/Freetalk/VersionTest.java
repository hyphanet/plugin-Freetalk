package plugins.Freetalk;

import static org.junit.Assert.*;

import org.junit.Test;

/** Tests class {@link Version}. */
public final class VersionTest {

	@Test public void testGetGitRevision() {
		String gitRevision = Version.getGitRevision();
		
		assertTrue(gitRevision.trim().length() > 0);
		
		// The legacy Ant builder ought to replace "@custom@" in "Version.java" with the revision.
		// Test for accidental replacement beyond "Version.java":
		assertEquals("@" + "custom" + "@", "@custom@");
		// Test if the placeholder is actually replaced in the variable behind getGitRevision():
		assertNotEquals("@custom@", gitRevision);
		
		// The new Gradle builder instead must put the revision into the file "Version.properties".
		// If getGitRevision() can't load the file it'll return "ERROR-while-loading-git-revision".
		assertFalse(gitRevision.toUpperCase().contains("ERROR"));
	}

	@Test public void testGetRealVersion() {
		long realVersion = Version.getRealVersion();
		assertTrue(realVersion >   0);
		assertTrue(realVersion >= 13); // Latest release as of 2021-02-06.
	}

	@Test public void testGetMarketingVersion() {
		String marketingVersion = Version.getMarketingVersion();
		String expectedSuffix = " " + Version.getGitRevision();
		assertTrue(marketingVersion.endsWith(expectedSuffix));
		assertTrue(marketingVersion.length() > expectedSuffix.length());
	}

}