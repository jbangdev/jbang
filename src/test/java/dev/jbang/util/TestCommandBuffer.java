package dev.jbang.util;

import static dev.jbang.util.Util.JBANG_RUNTIME_SHELL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class TestCommandBuffer extends BaseTest {

	@Test
	void testRunWinBat() {
		if (Util.getOS() == Util.OS.windows) {
			String out = Util.runCommand(examplesTestFolder.resolve("echo.bat").toString(), "abc def", "abc;def",
					"abc=def", "abc,def");
			assertThat(out, containsString("ARG = abc def"));
			assertThat(out, containsString("ARG = abc;def"));
			assertThat(out, containsString("ARG = abc=def"));
			assertThat(out, containsString("ARG = abc,def"));
		}
	}

	@Test
	void testRunWinBatFromBash() {
		if (Util.getOS() == Util.OS.windows) {
			environmentVariables.set(JBANG_RUNTIME_SHELL, "bash");
			String out = Util.runCommand(examplesTestFolder.resolve("echo.bat").toString(), "abc def", "abc;def",
					"abc=def", "abc,def");
			assertThat(out, containsString("ARG = abc def"));
			assertThat(out, containsString("ARG = abc;def"));
			assertThat(out, containsString("ARG = abc=def"));
			assertThat(out, containsString("ARG = abc,def"));
		}
	}

	@Test
	void testRunWinPS1() {
		if (Util.getOS() == Util.OS.windows) {
			String out = CommandBuffer.of("abc def", "abc;def").asCommandLine(Util.Shell.powershell);
			assertThat(out, equalTo("'abc def' 'abc;def'"));
		}
	}
}
