package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DeprecatedIT extends BaseIT {

	// Feature: deprecated and removed features

	// Scenario: --init should error and tell user about alternative
	// When command('jbang --init ' + scratch + '/test.java')
	// * match exit == 2
	// * match err contains "deprecated and now removed"
	@Test
	public void testInitDeprecated(@TempDir Path scratch) {
		Path testFile = scratch.resolve("test.java");
		assertThat(shell("jbang --init " + testFile))
			.failed()
			.errContains("deprecated and now removed");
	}

	// Scenario: --trust should error and tell user about alternative
	// When command('jbang --trust test.java')
	// * match exit == 2
	// * match err contains "deprecated and now removed"
	@Test
	public void testTrustDeprecated() {
		assertThat(shell("jbang --trust test.java"))
			.failed()
			.errContains("deprecated and now removed");
	}

	// Scenario: --trust should error and tell user about alternative
	// When command('jbang --edit-live=idea test.java')
	// * match exit == 2
	// * match err contains "deprecated and now removed"
	@Test
	public void testEditLiveDeprecated() {
		assertThat(shell("jbang --edit-live=idea test.java"))
			.failed()
			.errContains("deprecated and now removed");
	}
}