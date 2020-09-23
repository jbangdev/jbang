package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

public class TestJdk {

	private static final int SUCCESS_EXIT = CommandLine.ExitCode.SOFTWARE;

	@AfterAll
	static void afterAll() {
		environmentVariables.clear("JBANG_CACHE_DIR");
	}

	@Rule
	public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	void testNoJdksInstalled() throws IOException {
		initJBangCacheDir();
		ExecutionResult result = checkecRun(this::runList);

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.message, equalTo("[jbang] No JDKs installed\n"));
	}

	@Test
	void testHasJdksInstalled() throws IOException {

		final File testCache = initJBangCacheDir();
		final File jdkPath = new File(testCache, "jdks");
		jdkPath.mkdirs();
		Arrays	.asList("11", "12", "13")
				.forEach(jdkId -> new File(jdkPath, jdkId).mkdirs());

		ExecutionResult result = checkecRun(this::runList);

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.message, equalTo("[jbang] Available installed JDKs:\n  11\n  12\n  13\n"));
	}

	@Test
	void testExistingJdkUninstall() throws IOException {

		final File testCache = initJBangCacheDir();
		final File jdkPath = new File(testCache, "jdks");
		jdkPath.mkdirs();
		int jdkVersion = 14;
		new File(jdkPath, String.valueOf(jdkVersion)).mkdirs();

		ExecutionResult result = checkecRun(jdk -> runUninstall(jdk, jdkVersion));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.message, equalTo("[jbang] Uninstalled JDK:\n  " + jdkVersion + "\n"));
	}

	@Test
	void testNonExistingJdkUninstall() throws IOException {

		initJBangCacheDir();
		int jdkVersion = 16;

		ExecutionResult result = checkecRun(jdk -> runUninstall(jdk, jdkVersion));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.message, equalTo("[jbang] JDK 16 is not installed\n"));
	}

	private File initJBangCacheDir() throws IOException {
		Path tempDirectory = Files.createTempDirectory("jbang-test-cache");
		environmentVariables.set("JBANG_CACHE_DIR", tempDirectory.toAbsolutePath().toString());
		return tempDirectory.toFile();
	}

	private ExecutionResult checkecRun(Function<Jdk, Pair<Integer, Exception>> commandRunner) {

		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("jdk", "list");
		Jdk jdk = (Jdk) pr.subcommand().commandSpec().userObject();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintWriter printWriter = new PrintWriter(baos);
		final PrintStream backup = System.err;
		PrintStream s = new PrintStream(baos);
		System.setErr(s);

		Pair<Integer, Exception> result = commandRunner.apply(jdk);

		printWriter.flush();
		System.setErr(backup);
		return new ExecutionResult(
				result.getKey(),
				baos.toString(Charset.defaultCharset()),
				result.getValue());
	}

	public Pair<Integer, Exception> runList(Jdk jdk) {
		try {
			return Pair.of(jdk.list(), null);
		} catch (IOException e) {
			return Pair.of(null, e);
		}
	}

	public Pair<Integer, Exception> runUninstall(Jdk jdk, int version) {
		try {
			return Pair.of(jdk.uninstall(version), null);
		} catch (IOException e) {
			return Pair.of(null, e);
		}
	}

	class ExecutionResult {
		final Integer exitCode;
		final String message;
		final Exception exception;

		ExecutionResult(Integer value, String console, Exception exception) {
			this.exitCode = value;
			this.message = console;
			this.exception = exception;
		}
	}
}
