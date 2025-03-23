package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import dev.jbang.util.Util;

import io.qameta.allure.*;

public class RunIT {

	static Map<String, String> baseEnv;
	private static Path scratch;
	private static Path baseDir;

	public static List<String> prefixShellArgs(List<String> cmd) {
		List<String> list = new ArrayList<>(cmd);
		if (Util.isWindows()) {
			list.addAll(0, List.of("cmd", "/c"));
		} else {
			list.addAll(0, List.of("sh", "-c"));
		}
		return list;
	}

	static Map<String, String> baseEnv(Path scratch) {
		Map<String, String> env = new HashMap<>();

		// provide default scratch directory for temporary content
		// !('SCRATCH' in env) && (env.SCRATCH = sc)
		// set JBANG_REPO to not mess with users own ~/.m2
		String sep = java.io.File.separator;

		env.put("SCRATCH", scratch.toString());
		env.put("JBANG_REPO", scratch.toString() + sep + "karate-m2");
		env.put("JBANG_DIR", scratch.toString() + sep + "karate-jbang");
		env.put("JBANG_NO_VERSION_CHECK", "true");
		env.put("NO_COLOR", "1");

		// Add built jbang to PATH (not a gurantee that this will work if other jbang
		// instances are installed)
		env.put("PATH", Paths.get("build/install/jbang/bin").toAbsolutePath().toString() + File.pathSeparator
				+ System.getenv("PATH"));
		System.out.println("PATH: " + env.get("PATH"));
		return env;
	}

	@BeforeAll
	public static void setup(@TempDir Path tempscratch) throws URISyntaxException {
		scratch = tempscratch;
		baseEnv = baseEnv(scratch);

		URL examplesUrl = RunIT.class.getClassLoader().getResource("itests");
		if (examplesUrl == null) {
			baseDir = Paths.get("itests").toAbsolutePath();
		} else {
			baseDir = Paths.get(new File(examplesUrl.toURI()).getAbsolutePath());
		}
	}

	public static CommandResult run(Path baseDir, Map<String, String> env, List<String> command) {

		ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
		ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

		ProcessResult execute;
		String out;
		String err;
		try {
			execute = new ProcessExecutor()	.command(command)
											.directory(baseDir.toFile())
											.environment(env)
											.redirectOutput(stdoutStream)
											.redirectError(errorStream)
											.execute();

			out = new String(stdoutStream.toByteArray(), "UTF-8");
			err = new String(errorStream.toByteArray(), "UTF-8");
		} catch (InvalidExitValueException | IOException | InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Could not run " + command, e);
		}

		return new CommandResult(out, err, execute.getExitValue(), command);
	}

	public CommandResult shell(String... command) {
		final CommandResult[] resultHolder = new CommandResult[1];
		Allure.step(Arrays.toString(command),
				step -> {
					resultHolder[0] = run(baseDir, baseEnv, prefixShellArgs(Arrays.asList(command)));

					step.parameter("command", resultHolder[0].command().toString());
					step.parameter("out", resultHolder[0].out());
					step.parameter("err", resultHolder[0].err());
					step.parameter("exitCode", resultHolder[0].exitCode());
				});

		return resultHolder[0];
	}

	@Test
	@Description("Testing that jbang is running in a clean environment")
	public void testIsolation() {
		assertThat(shell("jbang version --verbose"))
													.errContains("Cache: " + scratch.toString())
													.errContains("Config: " + scratch.toString())
													.errContains(
															"Repository: " + scratch.resolve("karate-m2").toString())
													.succeeded();
	}

	// Scenario: should fail on missing file
	// * command('jbang notthere.java')
	// * match err contains 'Script or alias could not be found or read:
	// \'notthere.java\''
	// * match exit == 2
	@Test
	public void shouldFailOnMissingFile() {
		assertThat(shell("jbang notthere.java"))
												.errContains(
														"Script or alias could not be found or read: 'notthere.java'")
												.exitedWith(2);
	}

	// Scenario: parameter passing
	// * command('jbang helloworld.java jbangtest')
	// * match err == "[jbang] Building jar for helloworld.java...\n"
	// * match out == "Hello jbangtest\n"
	@Test
	public void shouldPassParameters() {
		assertThat(shell("jbang helloworld.java jbangtest"))
															.errEquals("[jbang] Building jar for helloworld.java...\n")
															.outEquals("Hello jbangtest\n");
	}

	@Test
	public void path() {
		assertThat(shell("echo $PATH"))
										.outContains(Paths.get("buildk/install/jbang/bin").toAbsolutePath().toString());
	}

	// Scenario: std in
	// * command('cat helloworld.java | jbang - jbangtest')
	// * match err == "[jbang] Building jar for helloworld.java...\n"
	// * match out == "Hello jbangtest\n"
	@Test
	public void shouldPassStdIn() {
		assertThat(shell("cat helloworld.java | jbang - jbangtest"))
																	.errEquals(
																			"[jbang] Building jar for helloworld.java...\n")
																	.outEquals("Hello jbangtest\n");
	}

	@Test
	public void shouldLaunchHelloWorldWithJFR() {
		assertThat(shell("jbang --jfr helloworld.java"))
														.outContains(
																"Started recording 1. No limit specified, using maxsize=250MB as default.");
	}

	// Scenario: java run multiple sources
	// * command('jbang --verbose one.java')
	// * match out contains "Two"
	@Test
	public void shouldRunMultipleSources() {
		assertThat(shell("jbang --verbose one.java"))
														.outContains("Two");
	}

	// Scenario: java run multiple matching sources
	// * command('jbang RootOne.java')
	// * match out contains "NestedOne"
	// * match out contains "NestedTwo"
	@Test
	public void shouldRunMultipleMatchingSources() {
		assertThat(shell("jbang RootOne.java"))
												.outContains("NestedOne")
												.outContains("NestedTwo");
	}

	// Scenario: java run multiple sources via cli
	// * command('jbang -s bar/Bar.java foo.java')
	// * match out contains "Bar"
	@Test
	public void shouldRunMultipleSourcesViaCli() {
		assertThat(shell("jbang -s bar/Bar.java foo.java"))
															.outContains("Bar");
	}

	// Scenario: java run multiple files
	// * command('jbang res/resource.java')
	// * match out contains "hello properties"
	@Test
	public void shouldRunMultipleFiles() {
		assertThat(shell("jbang res/resource.java"))
													.outContains("hello properties");
	}

	// Scenario: java run multiple files using globbing
	// * command('jbang resources.java')
	// * match out contains "hello properties"
	@Test
	public void shouldRunMultipleFilesUsingGlobbing() {
		assertThat(shell("jbang resources.java"))
													.outContains("hello properties");
	}

	// Scenario: java run multiple files using globbing and a mounting folder
	// * command('jbang resourcesmnt.java')
	// * match out contains "hello properties"
	@Test
	public void shouldRunMultipleFilesUsingGlobbingAndMountingFolder() {
		assertThat(shell("jbang resourcesmnt.java"))
													.outContains("hello properties");
	}

	// Scenario: java run multiple files using alias
	// * command('jbang resource')
	// * match out contains "hello properties"
	@Test
	public void shouldRunMultipleFilesUsingAlias() {
		assertThat(shell("jbang resource"))
											.outContains("hello properties");
	}

	// Scenario: java run multiple files using alias

//Scenario: java run multiple files using remote alias
//Then command('jbang trust add https://raw.githubusercontent.com')
//Then command('jbang resource@test')
//Then match out contains "hello properties"
	@Test
	public void shouldRunMultipleFilesUsingRemoteAlias() {
		assertThat(shell("jbang trust add https://raw.githubusercontent.com")).succeeded();
		assertThat(shell("jbang resource@test"))
												.outContains("hello properties");
	}

}
