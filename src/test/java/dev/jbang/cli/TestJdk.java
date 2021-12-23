package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

import picocli.CommandLine;

public class TestJdk extends BaseTest {

	private static final int SUCCESS_EXIT = CommandLine.ExitCode.OK;

	@Test
	void testNoJdksInstalled() throws IOException {
		initJBangCacheDir();
		ExecutionResult result = checkedRun(Jdk::list);

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("No JDKs installed\n"));
	}

	@Test
	void testHasJdksInstalled() throws IOException {

		final File testCache = initJBangCacheDir();
		final File jdkPath = new File(testCache, "jdks");
		jdkPath.mkdirs();
		Arrays	.asList("11", "12", "13")
				.forEach(jdkId -> new File(jdkPath, jdkId).mkdirs());

		ExecutionResult result = checkedRun(Jdk::list);

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Available installed JDKs:\n  11\n  12\n  13\n"));
	}

	@Test
	void testExistingJdkUninstall() throws IOException {

		final File testCache = initJBangCacheDir();
		final File jdkPath = new File(testCache, "jdks");
		jdkPath.mkdirs();
		int jdkVersion = 14;
		new File(jdkPath, String.valueOf(jdkVersion)).mkdirs();

		ExecutionResult result = checkedRun(jdk -> jdk.uninstall(jdkVersion));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] Uninstalled JDK:\n  " + jdkVersion + "\n"));
	}

	@Test
	void testNonExistingJdkUninstall() throws IOException {

		initJBangCacheDir();
		int jdkVersion = 16;

		ExecutionResult result = checkedRun(jdk -> jdk.uninstall(jdkVersion));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] JDK 16 is not installed\n"));
	}

	private File initJBangCacheDir() throws IOException {
		Path tempDirectory = Files.createTempDirectory("jbang-test-cache");
		environmentVariables.set("JBANG_CACHE_DIR", tempDirectory.toAbsolutePath().toString());
		return tempDirectory.toFile();
	}

	private ExecutionResult checkedRun(Function<Jdk, Integer> commandRunner) throws IOException {
		return checkedRun(commandRunner, "jdk");
	}
}
