package dev.jbang.util;

import static dev.jbang.util.Util.JBANG_RUNTIME_SHELL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
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
	void testApplyWindowsMaxProcessLimitExe1() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.exe", 1000))
			.shell(Util.Shell.cmd)
			.applyWindowsMaxProcessLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), greaterThan(2));
		assertThat(pb.command().get(1), not(anyOf(startsWith("@"), startsWith("\"@"))));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testApplyWindowsMaxProcessLimitExe2() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.exe", 4000))
			.shell(Util.Shell.cmd)
			.applyWindowsMaxProcessLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), equalTo(2));
		assertThat(pb.command().get(1), anyOf(startsWith("@"), startsWith("\"@")));
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void testApplyOthersMaxProcessLimit() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo", 4000))
			.shell(Util.Shell.bash)
			.applyWindowsMaxProcessLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), greaterThan(2));
		assertThat(pb.command().get(1), not(anyOf(startsWith("@"), startsWith("\"@"))));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testApplyWindowsMaxProcessLimitBat() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.bat", 1000))
			.shell(Util.Shell.cmd)
			.applyWindowsMaxProcessLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), equalTo(2));
		assertThat(pb.command().get(1), anyOf(startsWith("@"), startsWith("\"@")));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testApplyWindowsMaxProcessLimitBatFromBash() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.bat", 1000))
			.shell(Util.Shell.bash)
			.applyWindowsMaxProcessLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), equalTo(2));
		assertThat(pb.command().get(1), anyOf(startsWith("@"), startsWith("\"@")));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testApplyWindowsMaxProcessLimitCmd() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.cmd", 1000))
			.shell(Util.Shell.cmd)
			.applyWindowsMaxProcessLimit()
			.asProcessBuilder();
		assertThat(pb.command().size(), equalTo(2));
		assertThat(pb.command().get(1), anyOf(startsWith("@"), startsWith("\"@")));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testApplyWindowsMaxProcessLimitCmdFromBash() throws IOException {
		ProcessBuilder pb = CommandBuffer.of(argsTooLong("foo.cmd", 1000))
			.shell(Util.Shell.bash)
			.applyWindowsMaxProcessLimit()
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

	private String[] argsTooLong(String cmd, int cnt) {
		String[] args = new String[cnt];
		args[0] = cmd;
		for (int i = 1; i < args.length; i++) {
			args[i] = "argument " + i;
		}
		return args;
	}
}
