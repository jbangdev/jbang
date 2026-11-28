package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
public class RunWinIT extends BaseIT {

	@Test
	public void shouldReturnCorrectErrorCodeWithPowershell() {
		System.setProperty("jbang.it.usePowershell", "true");
		try {
			// single quotes, powershell -Command drops double quotes
			assertThat(shell("jbang run -c 'System.exit(42)'")).exitedWith(42).outIsExactly("");
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
	public void shouldNotLeaveVariablesPowershell() {
		System.setProperty("jbang.it.usePowershell", "true");
		try {
			assertThat(shell(Map.of("JAVA_HOME", "fake_home"),
					"jbang run -c 'System.exit(3)'", ";", "echo",
					"[${env:JAVA_HOME}]",
					"O${env:JBANG_RUNTIME_SHELL}K",
					"O${env:JBANG_STDIN_NOTTY}K",
					"O${env:JBANG_LAUNCH_CMD}K"))
				// only jbang can produce exit code 3, so this proves it ran
				.exitedWith(3)
				.outFind(Pattern.compile("(?m)^\\[fake_home\\]\\r?\\n^OK\\r?\\n^OK\\r?\\n^OK\\r?$"));
		} finally {
			System.clearProperty("jbang.it.usePowershell");
		}
	}

}
