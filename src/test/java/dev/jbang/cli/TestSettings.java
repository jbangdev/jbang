package dev.jbang.cli;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.Test;

import dev.jbang.Settings;

public class TestSettings {

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	void testRepo() {

		assertEquals(Settings.getLocalMavenRepo().toString(), System.getProperty("user.home") + "/.m2/repository");

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
