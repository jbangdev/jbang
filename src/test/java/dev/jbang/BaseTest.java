package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.JBang;
import dev.jbang.dependencies.DependencyCache;
import dev.jbang.util.Util;

import picocli.CommandLine;

public abstract class BaseTest {

	@BeforeEach
	void initEnv(@TempDir Path tempPath) throws IOException {
		jbangTempDir = Files.createDirectory(tempPath.resolve("jbang"));
		cwdDir = Files.createDirectory(tempPath.resolve("cwd"));
		Util.setCwd(cwdDir);
		System.setProperty("user.home", tempPath.toString());
		System.setProperty("maven.repo.local", mavenTempDir.toString());
		// Each test gets a fresh JBang config folder
		environmentVariables.set(Settings.JBANG_DIR, jbangTempDir.toString());
		// Each test gets a fresh cache folder
		environmentVariables.set(Settings.JBANG_CACHE_DIR, jbangTempDir.resolve("cache").toString());
		// Except we make all tests use the same JDK installation folder to prevent
		// excessive downloads
		environmentVariables.set(Settings.JBANG_CACHE_DIR + "_JDKS", jdksTempDir.toString());
		// Make sure we don't go looking outside our temp dir
		environmentVariables.set(Settings.JBANG_LOCAL_ROOT, tempPath.toString());
		// Don't check fo rnew versions while running tests
		environmentVariables.set(Settings.ENV_NO_VERSION_CHECK, "true");
		if (Util.isWindows()) {
			// On Windows assume we're running from within a CMD shell
			environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "cmd");
		}
		Configuration.instance(null);
		DependencyCache.clear();
	}

	public static final String EXAMPLES_FOLDER = "itests";
	public static Path examplesTestFolder;

	@BeforeAll
	static void init() throws URISyntaxException, IOException {
		try {
			// The default ConsoleHandler for logging doesn't like us changing
			// System.err out from under it, so we remove it and add our own
			LogManager lm = LogManager.getLogManager();
			lm.readConfiguration(BaseTest.class.getResourceAsStream("/logging.properties"));
			Logger rl = lm.getLogger("");
			Arrays.stream(rl.getHandlers())
				.filter(h -> h instanceof ConsoleHandler)
				.forEach(rl::removeHandler);
			rl.addHandler(new JBangHandler());
		} catch (IOException e) {
			// Ignore
		}
		mavenTempDir = Files.createTempDirectory("jbang_tests_maven");
		jdksTempDir = Files.createTempDirectory("jbang_tests_jdks");
		URL examplesUrl = BaseTest.class.getClassLoader().getResource(EXAMPLES_FOLDER);
		if (examplesUrl == null) {
			examplesTestFolder = Paths.get(EXAMPLES_FOLDER).toAbsolutePath();
		} else {
			examplesTestFolder = Paths.get(new File(examplesUrl.toURI()).getAbsolutePath());
		}
	}

	@AfterAll
	static void cleanup() {
		Util.deletePath(mavenTempDir, true);
		Util.deletePath(jdksTempDir, true);
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	public static Path mavenTempDir;
	public static Path jdksTempDir;
	public Path jbangTempDir;
	public Path cwdDir;

	protected <T> CaptureResult<Integer> checkedRun(Function<T, Integer> commandRunner, String... args)
			throws Exception {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs(args);
		while (pr.subcommand() != null) {
			pr = pr.subcommand();
		}
		@SuppressWarnings("unchecked")
		T usrobj = (T) pr.commandSpec().userObject();

		return captureOutput(() -> {
			if (commandRunner != null) {
				return commandRunner.apply(usrobj);
			} else if (usrobj instanceof BaseCommand) {
				return ((BaseCommand) usrobj).doCall();
			} else {
				throw new IllegalStateException("usrobj is of unsupported type");
			}
		});
	}

	protected <T> CaptureResult<T> captureOutput(Callable<T> func) throws Exception {
		ByteArrayOutputStream newOut = new ByteArrayOutputStream();
		PrintWriter pwOut = new PrintWriter(newOut);
		final PrintStream originalOut = System.out;
		PrintStream psOut = new PrintStream(newOut);
		System.setOut(psOut);

		ByteArrayOutputStream newErr = new ByteArrayOutputStream();
		PrintWriter pwErr = new PrintWriter(newErr);
		final PrintStream originalErr = System.err;
		PrintStream psErr = new PrintStream(newErr);
		System.setErr(psErr);

		final T result;
		String outStr, errStr;
		try {
			result = func.call();
		} finally {
			pwOut.flush();
			System.setOut(originalOut);
			outStr = newOut.toString(Charset.defaultCharset());
			System.out.println(outStr);

			pwErr.flush();
			System.setErr(originalErr);
			errStr = newErr.toString(Charset.defaultCharset());
			System.err.println(errStr);
		}

		return new CaptureResult<>(result, outStr, errStr);
	}

	protected static class CaptureResult<T> {
		public final T result;
		public final String out;
		public final String err;

		CaptureResult(T result, String out, String err) {
			this.result = result;
			this.out = out;
			this.err = err;
		}

		public String normalizedOut() {
			return out.replaceAll("\\r\\n", "\n");
		}

		public String normalizedErr() {
			return err.replaceAll("\\r\\n", "\n");
		}
	}

}
