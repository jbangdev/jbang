package dev.jbang.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class GlobTest extends BaseTest {

	@Test
	void testMainMatching() {
		assertThat("Demo1").matches(Glob.toRegex("*"));
		assertThat("Demo1").matches(Glob.toRegex("Demo*"));
		assertThat("a.b.c.Demo$myapp").matches(Glob.toRegex("a.b.c.Demo$myapp"));

		assertThat("a.b.c.Demo$myapp").doesNotMatch(Glob.toRegex("Demo$myapp"));
		assertThat("a.b.c.Demo$myapp").matches(Glob.toRegex("*Demo$myapp"));
	}
}