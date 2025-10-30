package dev.jbang.it;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.assertj.core.api.Assertions;
import org.assertj.core.presentation.StandardRepresentation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import dev.jbang.util.Util;

import io.qameta.allure.Allure;

public class BaseIT {

	static Map<String, String> baseEnv;
	private static Path scratch;
	private static Path baseDir;

	protected Path scratch() {
		return scratch;
	}

	protected Path baseDir() {
		return baseDir;
	}

	public static List<String> prefixShellArgs(List<String> cmd) {
		List<String> list = new ArrayList<>(cmd);
		if (Util.isWindows()) {
			if ("true".equals(System.getProperty("jbang.it.usePowershell"))) {
				list.addAll(0, Arrays.asList("powershell", "-Command"));
				list.addAll(Arrays.asList(";", "exit", "$LastExitCode"));
			} else {
				list.addAll(0, Arrays.asList("cmd", "/c"));
			}
		} else {
			list.addAll(0, Arrays.asList("sh", "-c"));
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
		env.put("JBANG_REPO", scratch + sep + "itest-m2");
		env.put("JBANG_DIR", scratch + sep + "itest-jbang");
		env.put("JBANG_NO_VERSION_CHECK", "true");
		env.put("NO_COLOR", "1");

		// Add built jbang to PATH (not a gurantee that this will work if other jbang
		// instances are installed)
		env.put("PATH", Paths.get("build/install/jbang/bin").toAbsolutePath() + File.pathSeparator
				+ System.getenv("PATH"));
		System.out.println("PATH: " + env.get("PATH"));
		return env;
	}

	@BeforeAll
	public static void setup(@TempDir Path tempscratch) throws URISyntaxException, IOException {
		Assertions.useRepresentation(new StandardRepresentation() {
			@Override
			public String toStringOf(Object object) {
				if (object instanceof String) {
					String str = (String) object;
					return str.replace("\t", "\\t")
						.replace("\n", "\\n\n")
						.replace("\r", "\\r");
				}
				return super.toStringOf(object);
			}
		});
		scratch = tempscratch;
		baseEnv = baseEnv(scratch);

		Path itestsDir;
		URL examplesUrl = RunIT.class.getClassLoader().getResource("itests");
		if (examplesUrl == null) {
			itestsDir = Paths.get("itests").toAbsolutePath();
		} else {
			itestsDir = Paths.get(new File(examplesUrl.toURI()).getAbsolutePath());
		}
		baseDir = scratch.resolve("itests");
		// Make a copy of the itests folder to run our tests in
		Files.walk(itestsDir).forEach(source -> {
			try {
				Files.copy(source, baseDir.resolve(itestsDir.relativize(source)));
			} catch (IOException e) {
				throw new IllegalStateException("Could not copy " + source, e);
			}
		});
	}

	public static CommandResult run(Path baseDir, Map<String, String> env, List<String> command) {

		ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
		ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

		ProcessResult execute;
		String out;
		String err;
		try {
			execute = new ProcessExecutor().command(command)
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

	public CommandResult shell(Map<String, String> env, String... command) {
		final CommandResult[] resultHolder = new CommandResult[1];

		// merge base env with provided env
		Map<String, String> envToUse = new HashMap<>(baseEnv);
		envToUse.putAll(env);

		Allure.step(Arrays.toString(command),
				step -> {
					resultHolder[0] = run(baseDir, envToUse, prefixShellArgs(Arrays.asList(command)));

					step.parameter("command", resultHolder[0].command().toString());
					step.parameter("out", resultHolder[0].out());
					step.parameter("err", resultHolder[0].err());
					step.parameter("exitCode", resultHolder[0].exitCode());
				});

		return resultHolder[0];
	}

	public CommandResult shell(String... command) {
		return shell(Collections.emptyMap(), command);
	}

	public void rmrf(String... paths) {
		for (String p : paths) {
			Util.deletePath(baseDir().resolve(p), true);
		}
	}

}
