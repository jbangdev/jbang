package dev.jbang.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class TestCommandBuffer extends BaseTest {

	@Test
	void testRunWinBat() {
		if (Util.getOS() == Util.OS.windows) {
			String out = Util.runCommand(examplesTestFolder.resolve("echo.bat").toString(), "abc def", "abc;def");
			assertThat(out).contains("ARG = abc def");
			assertThat(out).contains("ARG = abc;def");
		}
	}

	@Test
	void testRunWinPS1() {
		if (Util.getOS() == Util.OS.windows) {
			String out = CommandBuffer.of("abc def", "abc;def").asCommandLine(Util.Shell.powershell);
			assertThat(out).isEqualTo("'abc def' 'abc;def'");
		}
	}
}
