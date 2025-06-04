package dev.jbang.util;

import static dev.jbang.util.Util.JBANG_RUNTIME_SHELL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import dev.jbang.BaseTest;

public class TestCommandBuffer extends BaseTest {

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testRunWinBat() {
		String out = Util.runCommand(examplesTestFolder.resolve("echo.bat").toString(), "abc def", "abc;def",
				"abc=def", "abc,def");
		assertThat(out, containsString("ARG = abc def"));
		assertThat(out, containsString("ARG = abc;def"));
		assertThat(out, containsString("ARG = abc=def"));
		assertThat(out, containsString("ARG = abc,def"));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testRunWinBatFromBash() {
		environmentVariables.set(JBANG_RUNTIME_SHELL, "bash");
		String out = Util.runCommand(examplesTestFolder.resolve("echo.bat").toString(), "abc def", "abc;def",
				"abc=def", "abc,def");
		assertThat(out, containsString("ARG = abc def"));
		assertThat(out, containsString("ARG = abc;def"));
		assertThat(out, containsString("ARG = abc=def"));
		assertThat(out, containsString("ARG = abc,def"));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testRunWinPS1() {
		String out = CommandBuffer.of("abc def", "abc;def").shell(Util.Shell.powershell).asCommandLine();
		assertThat(out, equalTo("'abc def' 'abc;def'"));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testApplyWindowsMaxLengthLimitExe() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.exe"))
			.shell(Util.Shell.cmd)
			.applyWindowsMaxLengthLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), equalTo(2));
		assertThat(pb.command().get(1), anyOf(startsWith("@"), startsWith("\"@")));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testApplyWindowsMaxLengthLimitBat() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.bat"))
			.shell(Util.Shell.cmd)
			.applyWindowsMaxLengthLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), equalTo(2));
		assertThat(pb.command().get(1), anyOf(startsWith("@"), startsWith("\"@")));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testApplyWindowsMaxLengthLimitCmd() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.cmd"))
			.shell(Util.Shell.cmd)
			.applyWindowsMaxLengthLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), equalTo(2));
		assertThat(pb.command().get(1), anyOf(startsWith("@"), startsWith("\"@")));
	}

	@Test
	void testUsingArgsFileWith1Arg() throws IOException {
		ProcessBuilder pb = CommandBuffer.of("abc").usingArgsFile().asProcessBuilder();
		assertThat(pb.command().size(), equalTo(1));
	}

	@Test
	void testUsingArgsFileWith3Args() throws IOException {
		ProcessBuilder pb = CommandBuffer.of("abc", "def", "ghi").usingArgsFile().asProcessBuilder();
		assertThat(pb.command().size(), equalTo(2));
		assertThat(pb.command().get(1), anyOf(startsWith("@"), startsWith("\"@")));
	}

	@Test
	void testUsingArgsFileNoDup() throws IOException {
		CommandBuffer cmd = CommandBuffer.of("abc", "def", "ghi").usingArgsFile();
		CommandBuffer cmd2 = cmd.usingArgsFile();
		assertThat(cmd, is(cmd2));
	}

	private String[] argsTooLong(String cmd) {
		String[] args = new String[1000];
		args[0] = cmd;
		for (int i = 1; i < args.length; i++) {
			args[i] = "argument " + i;
		}
		return args;
	}
}
