package plugins.Freetalk;

import static org.junit.Assert.*;

import org.junit.Test;

/** Tests class {@link Version}. */
public final class VersionTest {

	@Test public void testGetGitRevision() {
		assertNotEquals("@" + "custom" + "@", Version.getGitRevision());
		assertNotEquals("ERROR-while-loading-git-revision", Version.getGitRevision());
	}

}