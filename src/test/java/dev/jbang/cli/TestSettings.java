package dev.jbang.cli;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;

public class TestSettings extends BaseTest {

	@Test
	void testRepo() {
		assertEquals(Settings.getLocalMavenRepo().toString(), mavenTempDir.toString());

		System.setProperty("maven.repo.local", "nowhere");
		try {
			assertThat(Settings.getLocalMavenRepo().toString(), endsWith("nowhere"));
		} finally {
			System.clearProperty("maven.repo.local");
		}

		environmentVariables.set("JBANG_REPO", "envrepo");
		assertThat(Settings.getLocalMavenRepo().toString(), endsWith("envrepo"));
		environmentVariables.clear("JBANG_REPO");

	}
}
