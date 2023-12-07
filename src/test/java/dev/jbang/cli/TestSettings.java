package dev.jbang.cli;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;

public class TestSettings extends BaseTest {

	@Test
	void testRepo() {
		environmentVariables.clear("JBANG_REPO");
		assertThat(Settings.getJBangLocalMavenRepoOverride(), nullValue());
		environmentVariables.set("JBANG_REPO", "envrepo");
		assertThat(Settings.getJBangLocalMavenRepoOverride().toString(), endsWith("envrepo"));
		environmentVariables.clear("JBANG_REPO");
	}
}
