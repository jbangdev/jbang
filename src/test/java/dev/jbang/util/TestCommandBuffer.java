package dev.jbang.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class TestCommandBuffer extends BaseTest {
	@Test
	void testRunWinBat() {
		if (Util.getOS() == Util.OS.windows) {
			String out = Util.runCommand(examplesTestFolder.resolve("echo.bat").toString(), "abc def", "abc;def");
			assertThat(out, containsString("ARG = abc def"));
			assertThat(out, containsString("ARG = abc;def"));
		}
	}
}
