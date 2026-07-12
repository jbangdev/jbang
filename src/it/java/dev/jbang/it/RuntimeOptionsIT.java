package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import io.qameta.allure.Description;

/**
 * Integration tests for -R (runtime-option) and -C (compile-option) flags.
 * Covers regression from aesh migration where attached short-option values
 * (e.g. -R-Xmx4G without space) stopped working.
 *
 * See: https://github.com/jbangdev/jbang/issues/2555 See:
 * https://github.com/aeshell/aesh/issues/541
 */
public class RuntimeOptionsIT extends BaseIT {

	// -------------------------------------------------------------------
	// -R (runtime-option) with different syntaxes
	// -------------------------------------------------------------------

	@Test
	@Description("-R with space separator should pass runtime option to JVM")
	public void runtimeOptionWithSpace() {
		assertThat(shell("jbang run -R -Xmx128m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m");
	}

	@Test
	@Description("-R with equals separator should pass runtime option to JVM")
	public void runtimeOptionWithEquals() {
		assertThat(shell("jbang run -R=-Xmx128m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m");
	}

	@Test
	@Description("-R with attached value (no separator) should pass runtime option to JVM (issue #2555, aesh #541)")
	public void runtimeOptionAttached() {
		assertThat(shellWithTimeout("jbang run -R-Xmx128m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m");
	}

	@Test
	@Description("--runtime-option with equals should pass runtime option to JVM")
	public void runtimeOptionLongWithEquals() {
		assertThat(shell("jbang run --runtime-option=-Xmx128m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m");
	}

	@Test
	@Description("--runtime-option with space should pass runtime option to JVM")
	public void runtimeOptionLongWithSpace() {
		assertThat(shell("jbang run --runtime-option -Xmx128m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m");
	}

	// -------------------------------------------------------------------
	// Multiple -R options
	// -------------------------------------------------------------------

	@Test
	@Description("Multiple -R with space separator should all be passed to JVM")
	public void multipleRuntimeOptionsWithSpace() {
		assertThat(shell("jbang run -R -Xmx128m -R -Xms64m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m")
			.outContains("JVMARG:-Xms64m");
	}

	@Test
	@Description("Multiple -R with attached values should all be passed to JVM (issue #2555, aesh #541)")
	public void multipleRuntimeOptionsAttached() {
		assertThat(shellWithTimeout("jbang run -R-Xmx128m -R-Xms64m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m")
			.outContains("JVMARG:-Xms64m");
	}

	@Test
	@Description("Multiple -R with equals should all be passed to JVM")
	public void multipleRuntimeOptionsWithEquals() {
		assertThat(shell("jbang run -R=-Xmx128m -R=-Xms64m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m")
			.outContains("JVMARG:-Xms64m");
	}

	// -------------------------------------------------------------------
	// -R with typical JVM flags (the original issue scenario)
	// -------------------------------------------------------------------

	@Test
	@Description("-R-Xmx and -R-Xms attached should set JVM memory options (issue #2555, aesh #541)")
	public void runtimeOptionXmxXmsAttached() {
		assertThat(shellWithTimeout("jbang run -R-Xmx128m -R-Xms64m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m")
			.outContains("JVMARG:-Xms64m");
	}

	@Test
	@Description("-R -Xmx and -R -Xms with space should set JVM memory options")
	public void runtimeOptionXmxXmsWithSpace() {
		assertThat(shell("jbang run -R -Xmx128m -R -Xms64m printjvmargs.java"))
			.succeeded()
			.outContains("JVMARG:-Xmx128m")
			.outContains("JVMARG:-Xms64m");
	}

	// -------------------------------------------------------------------
	// -C (compile-option) with different syntaxes
	// -------------------------------------------------------------------

	@Test
	@Description("-C with space separator should pass compile option (visible via --verbose)")
	public void compileOptionWithSpace() {
		// -g is a standard javac option that enables debug info
		assertThat(shell("jbang build --fresh --verbose -C -g printjvmargs.java"))
			.succeeded()
			.errContains("-g");
	}

	@Test
	@Description("-C with equals separator should pass compile option")
	public void compileOptionWithEquals() {
		assertThat(shell("jbang build --fresh --verbose -C=-g printjvmargs.java"))
			.succeeded()
			.errContains("-g");
	}

	@Test
	@Description("-C with attached value (no separator) should pass compile option (issue #2555, aesh #541)")
	public void compileOptionAttached() {
		assertThat(shellWithTimeout("jbang build --fresh --verbose -C-g printjvmargs.java"))
			.succeeded()
			.errContains("-g");
	}

	@Test
	@Description("--compile-option with equals should pass compile option")
	public void compileOptionLongWithEquals() {
		assertThat(shell("jbang build --fresh --verbose --compile-option=-g printjvmargs.java"))
			.succeeded()
			.errContains("-g");
	}

	@Test
	@Description("--compile-option with space should pass compile option")
	public void compileOptionLongWithSpace() {
		assertThat(shell("jbang build --fresh --verbose --compile-option -g printjvmargs.java"))
			.succeeded()
			.errContains("-g");
	}

	/**
	 * Run a shell command with a 10-second timeout. Used for tests that may hang
	 * due to aesh infinite loop bug (#541).
	 */
	private CommandResult shellWithTimeout(String... command) {
		ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
		ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
		java.util.List<String> cmd = prefixShellArgs(Arrays.asList(command));
		try {
			ProcessResult execute = new ProcessExecutor().command(cmd)
				.directory(baseDir().toFile())
				.environment(baseEnv)
				.redirectOutput(stdoutStream)
				.redirectError(errorStream)
				.timeout(10, TimeUnit.SECONDS)
				.destroyOnExit()
				.execute();
			return new CommandResult(
					new String(stdoutStream.toByteArray(), "UTF-8"),
					new String(errorStream.toByteArray(), "UTF-8"),
					execute.getExitValue(), cmd);
		} catch (TimeoutException e) {
			return new CommandResult("",
					"Process timed out (likely infinite loop in option parsing)",
					124, cmd);
		} catch (Exception e) {
			throw new IllegalStateException("Could not run " + cmd, e);
		}
	}
}
