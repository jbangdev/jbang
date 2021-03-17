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
import java.util.function.Function;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.Jbang;
import dev.jbang.cli.TestRun;
import dev.jbang.util.Util;

import picocli.CommandLine;

public abstract class BaseTest {

	@BeforeEach
	void initEnv(@TempDir Path tempPath) throws IOException {
		jbangTempDir = Files.createDirectory(tempPath.resolve("jbang"));
		cwdDir = Files.createDirectory(tempPath.resolve("cwd"));
		Util.setCwd(cwdDir);
		environmentVariables.set(Settings.JBANG_DIR, jbangTempDir.toString());
		environmentVariables.set(Settings.JBANG_CACHE_DIR, jbangTempDir.resolve("cache").toString());
		environmentVariables.set(Settings.ENV_NO_VERSION_CHECK, "true");
	}

	public static final String EXAMPLES_FOLDER = "itests";
	public static Path examplesTestFolder;

	@BeforeAll
	static void init() throws URISyntaxException, IOException {
		URL examplesUrl = TestRun.class.getClassLoader().getResource(EXAMPLES_FOLDER);
		if (examplesUrl == null) {
			examplesTestFolder = Paths.get(EXAMPLES_FOLDER).toAbsolutePath();
		} else {
			examplesTestFolder = Paths.get(new File(examplesUrl.toURI()).getAbsolutePath());
		}
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	public Path jbangTempDir;
	public Path cwdDir;

	protected <T> ExecutionResult checkedRun(Function<T, Integer> commandRunner, String... args) throws IOException {
		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs(args);
		while (pr.subcommand() != null) {
			pr = pr.subcommand();
		}
		@SuppressWarnings("unchecked")
		T usrobj = (T) pr.commandSpec().userObject();

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

		final Integer result;
		String outStr, errStr;
		try {
			if (commandRunner != null) {
				result = commandRunner.apply(usrobj);
			} else if (usrobj instanceof BaseCommand) {
				result = ((BaseCommand) usrobj).doCall();
			} else {
				throw new IllegalStateException("usrobj is of unsupported type");
			}
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

		return new ExecutionResult(result, outStr, errStr);
	}

	protected static class ExecutionResult {
		public final Integer exitCode;
		public final String out;
		public final String err;

		ExecutionResult(Integer value, String out, String err) {
			this.exitCode = value;
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
