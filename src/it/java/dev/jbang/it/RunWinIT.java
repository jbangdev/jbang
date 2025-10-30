package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
public class RunWinIT extends BaseIT {

	@Test
	public void shouldReturnCorrectErrorCodeWithPowershell() {
		System.setProperty("jbang.it.usePowershell", "true");
		try {
			assertThat(shell("jbang run -c \"System.exit(42)\"")).outIsExactly("");
		} finally {
			System.clearProperty("jbang.it.usePowershell");
		}
	}

	@Test
	public void shouldNotLeaveVariablesCmd() {
		assertThat(shell(Map.of("JAVA_HOME", "fake_home"),
				"jbang run -c \"System.exit(0)\"", "&", "echo",
				"%JAVA_HOME%",
				"%JBANG_RUNTIME_SHELL%",
				"%JBANG_STDIN_NOTTY%",
				"%JBANG_LAUNCH_CMD%"))
			.outMatches(Pattern.compile(".*fake_home.*%JBANG_RUNTIME_SHELL%.*%JBANG_STDIN_NOTTY%.*%JBANG_LAUNCH_CMD%.*",
					Pattern.DOTALL));
	}

	@Test
	@Disabled("Doesn't work in CI for some reason")
	public void shouldNotLeaveVariablesPowershell() {
		System.setProperty("jbang.it.usePowershell", "true");
		try {
			assertThat(shell(Map.of("JAVA_HOME", "fake_home"),
					"jbang run -c \"System.exit(0)\"", ";", "echo",
					"[${env:JAVA_HOME}]",
					"O${env:JBANG_RUNTIME_SHELL}K",
					"O${env:JBANG_STDIN_NOTTY}K",
					"O${env:JBANG_LAUNCH_CMD}K"))
				.outMatches(Pattern.compile(".*[fake_home].*OK.*OK.*OK.*", Pattern.DOTALL));
		} finally {
			System.clearProperty("jbang.it.usePowershell");
		}
	}

}
