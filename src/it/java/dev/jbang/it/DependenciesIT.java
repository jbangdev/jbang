package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;

public class DependenciesIT extends BaseIT {

	// Feature: dependency fetching

	// Scenario: fetch dependencies
	// * command('jbang --verbose version')
	// When command('jbang classpath_log.java', { JBANG_REPO: scratch + "/newrepo"})
	// Then match err == '[jbang] Resolving dependencies...\n[jbang]
	// log4j:log4j:1.2.17\n[jbang] Dependencies resolved\n[jbang] Building jar for
	// classpath_log.java...\n'
	// And fileexist(scratch + "/newrepo")
	// And match exit == 0
	@Test
	public void testFetchDependencies() {
		shell("jbang --verbose version");

		Path newRepo = scratch().resolve("newrepo");
		assertThat(
				shell(Collections.singletonMap("JBANG_REPO", newRepo.toAbsolutePath().toString()),
						"jbang classpath_log.java"))
			.succeeded()
			.errEquals(
					"[jbang] Resolving dependencies...\n[jbang]    log4j:log4j:1.2.17\n[jbang] Dependencies resolved\n[jbang] Building jar for classpath_log.java...\n"
						.replace(
								"\n", System.lineSeparator()));

		assertThat(Files.exists(newRepo)).isTrue();
	}
}