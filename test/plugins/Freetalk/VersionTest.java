package plugins.Freetalk;

import static org.junit.Assert.*;

import org.junit.Test;

/** Tests class {@link Version}. */
public final class VersionTest {

	@Test public void testGetGitRevision() {
		// The legacy Ant builder ought to replace "@custom@" in "Version.java" with the revision.
		// Test for accidental replacement beyond "Version.java":
		assertEquals("@" + "custom" + "@", "@custom@");
		// Test if the placeholder is actually replaced in the variable behind getGitRevision():
		assertNotEquals("@custom@", Version.getGitRevision());
		
		// The new Gradle builder instead must put the revision into the file "Version.properties".
		// If getGitRevision() can't load the file it'll return "ERROR-while-loading-git-revision".
		assertFalse(Version.getGitRevision().toUpperCase().contains("ERROR"));
	}

}