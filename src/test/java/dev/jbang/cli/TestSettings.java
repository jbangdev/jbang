package dev.jbang.cli;

import static org.junit.Assert.assertEquals;

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

		System.setProperty("maven.repo.local", "/nowhere");
		try {
			assertEquals(Settings.getLocalMavenRepo().toString(), "/nowhere");
		} finally {
			System.clearProperty("maven.repo.local");
		}

		environmentVariables.set("JBANG_REPO", "/envrepo");
		assertEquals(Settings.getLocalMavenRepo().toString(), "/envrepo");
		environmentVariables.clear("JBANG_REPO");

	}
}
