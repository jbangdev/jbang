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
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

public class TestJdk {

	private static final int SUCCESS_EXIT = CommandLine.ExitCode.OK;

	@AfterAll
	static void afterAll() {
		environmentVariables.clear("JBANG_CACHE_DIR");
	}

	@Rule
	public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	void testNoJdksInstalled() throws IOException {
		initJBangCacheDir();
		ExecutionResult result = checkedRun(jdk -> jdk.list());

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(osIndependentOutput(result.message),
				equalTo("[jbang] No JDKs installed\n"));
	}

	@Test
	void testHasJdksInstalled() throws IOException {

		final File testCache = initJBangCacheDir();
		final File jdkPath = new File(testCache, "jdks");
		jdkPath.mkdirs();
		Arrays	.asList("11", "12", "13")
				.forEach(jdkId -> new File(jdkPath, jdkId).mkdirs());

		ExecutionResult result = checkedRun(jdk -> jdk.list());

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(osIndependentOutput(result.message),
				equalTo("[jbang] Available installed JDKs:\n  11\n  12\n  13\n"));
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
		assertThat(osIndependentOutput(result.message),
				equalTo("[jbang] Uninstalled JDK:\n  " + jdkVersion + "\n"));
	}

	@Test
	void testNonExistingJdkUninstall() throws IOException {

		initJBangCacheDir();
		int jdkVersion = 16;

		ExecutionResult result = checkedRun(jdk -> jdk.uninstall(jdkVersion));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(osIndependentOutput(result.message),
				equalTo("[jbang] JDK 16 is not installed\n"));
	}

	private File initJBangCacheDir() throws IOException {
		Path tempDirectory = Files.createTempDirectory("jbang-test-cache");
		environmentVariables.set("JBANG_CACHE_DIR", tempDirectory.toAbsolutePath().toString());
		return tempDirectory.toFile();
	}

	private ExecutionResult checkedRun(Function<Jdk, Integer> commandRunner) {

		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("jdk", "list");
		Jdk jdk = (Jdk) pr.subcommand().commandSpec().userObject();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintWriter printWriter = new PrintWriter(baos);
		final PrintStream originalErr = System.err;
		PrintStream ps = new PrintStream(baos);
		System.setErr(ps);

		final Integer result = commandRunner.apply(jdk);

		printWriter.flush();
		System.setErr(originalErr);
		return new ExecutionResult(result, baos.toString(Charset.defaultCharset()));
	}

	class ExecutionResult {
		final Integer exitCode;
		final String message;

		ExecutionResult(Integer value, String console) {
			this.exitCode = value;
			this.message = console;
		}
	}

	private String osIndependentOutput(String value) {
		return value.replaceAll("\\r\\n", "\n");
	}

}
