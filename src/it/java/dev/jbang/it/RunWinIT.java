package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

	@Test
	public void shouldNotMixUpParallelRunsCmd() throws Exception {
		Path script = scratch().resolve("parallel.java");
		Files.write(script, ("class parallel { public static void main(String... args) { "
				+ "System.out.println(\"run-\" + args[0]); } }")
			.getBytes());
		// build it first so the parallel runs all finish at about the same time
		assertThat(shell("jbang", "build", script.toString())).succeeded();
		// cmd processes started in the same second all get the same %RANDOM%
		// values, so this makes them race for the same temporary file names
		int runs = 8;
		ExecutorService pool = Executors.newFixedThreadPool(runs);
		try {
			List<Future<CommandResult>> results = new ArrayList<>();
			for (int i = 0; i < runs; i++) {
				List<String> cmd = prefixShellArgs(Arrays.asList("jbang", script.toString(), String.valueOf(i)));
				results.add(pool.submit(() -> run(baseDir(), baseEnv, cmd)));
			}
			for (int i = 0; i < runs; i++) {
				assertThat(results.get(i).get()).succeeded().outContains("run-" + i);
			}
		} finally {
			pool.shutdownNow();
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
