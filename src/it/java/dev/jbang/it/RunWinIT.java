package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
				"%JBANG_LAUNCH_CMD%",
				"%JBANG_EXECUTE_FILE%"))
			.outMatches(Pattern.compile(
					".*fake_home.*%JBANG_RUNTIME_SHELL%.*%JBANG_STDIN_NOTTY%.*%JBANG_LAUNCH_CMD%.*%JBANG_EXECUTE_FILE%.*",
					Pattern.DOTALL));
	}

	@Test
	public void shouldNotPassExecuteFileToAppCmd() throws IOException {
		assertThat(shell("jbang", executeFileScript().toString())).succeeded().outContains("[null]");
	}

	@Test
	public void shouldNotPassExecuteFileToAppPowershell() throws IOException {
		Path script = executeFileScript();
		System.setProperty("jbang.it.usePowershell", "true");
		try {
			assertThat(shell("jbang", script.toString())).succeeded().outContains("[null]");
		} finally {
			System.clearProperty("jbang.it.usePowershell");
		}
	}

	private Path executeFileScript() throws IOException {
		Path script = scratch().resolve("executefile.java");
		Files.write(script, ("class executefile { public static void main(String... args) { "
				+ "System.out.println(\"[\" + System.getenv(\"JBANG_EXECUTE_FILE\") + \"]\"); } }")
			.getBytes());
		return script;
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
