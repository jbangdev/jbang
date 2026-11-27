package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import dev.jbang.util.Util;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JreStartupIT extends BaseIT {
	private Path jreHome;
	private Path jbangDir;

	@BeforeAll
	void createRuntime() throws Exception {
		Path javaHome = Paths.get(System.getProperty("java.home"));
		Path jlink = javaHome.resolve("bin").resolve(Util.isWindows() ? "jlink.exe" : "jlink");
		assumeTrue(Files.isExecutable(jlink), "A JDK with jlink is required");
		jreHome = scratch().resolve("jre with spaces");
		Process process = new ProcessBuilder(jlink.toString(),
				"--add-modules", "java.se,jdk.unsupported,jdk.crypto.ec",
				"--output", jreHome.toString())
			.redirectErrorStream(true)
			.start();
		boolean finished = process.waitFor(60, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
		}
		assertTrue(finished, "Creating the JRE timed out");
		assertTrue(process.exitValue() == 0, new String(process.getInputStream().readAllBytes()));
		assertFalse(Files.exists(jreHome.resolve("bin").resolve(Util.isWindows() ? "javac.exe" : "javac")));

		jbangDir = scratch().resolve("startup-jbang");
		Path bin = Files.createDirectories(jbangDir.resolve("bin"));
		try (Stream<Path> files = Files.list(Paths.get("build/install/jbang/bin").toAbsolutePath())) {
			for (Path file : (Iterable<Path>) files::iterator) {
				Files.copy(file, bin.resolve(file.getFileName()));
			}
		}
	}

	private void assertJreStartup(String script, boolean javaHome) {
		Map<String, String> env = new HashMap<>(baseEnv);
		env.put("JAVA_HOME", javaHome ? jreHome.toString() : "");
		env.put("JBANG_DIR", jbangDir.toString());
		env.put("JBANG_CACHE_DIR", jbangDir.resolve("cache").toString());
		env.put("JBANG_USE_NATIVE", "false");
		String path = jreHome.resolve("bin").toString();
		if (Util.isWindows()) {
			path += File.pathSeparator + Paths.get(System.getenv("SystemRoot"), "System32")
					+ File.pathSeparator
					+ Paths.get(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0");
		} else {
			path += File.pathSeparator + "/usr/bin" + File.pathSeparator + "/bin";
		}
		env.put("PATH", path);

		Path launcher = Paths.get("src/main/scripts").resolve(script).toAbsolutePath();
		List<String> command = new ArrayList<>();
		if ("jbang.cmd".equals(script)) {
			command.addAll(Arrays.asList(System.getenv("ComSpec"), "/c", launcher.toString(), "version"));
		} else if ("jbang.ps1".equals(script)) {
			command.addAll(Arrays.asList("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
					"$env:PSModulePath = $PSHOME + '\\Modules'; & '" + launcher + "' version; exit $LASTEXITCODE"));
		} else {
			Path bash = Util.searchPath("bash");
			assumeTrue(bash != null, "Bash is not available");
			env.put("PATH", env.get("PATH") + File.pathSeparator + bash.getParent());
			command.addAll(Arrays.asList(bash.toString(), launcher.toString(), "version"));
		}

		assertThat(run(baseDir(), env, command))
			.succeeded()
			.errNotContains("Downloading JDK")
			.errNotContains("valid Java");
		assertFalse(Files.exists(jbangDir.resolve("cache").resolve("jdks")));
	}

	@Test
	void bashUsesJreFromJavaHome() {
		assertJreStartup("jbang", true);
	}

	@Test
	void bashUsesJreFromPath() {
		assertJreStartup("jbang", false);
	}

	@Test
	void cmdUsesJreFromJavaHome() {
		assumeTrue(Util.isWindows());
		assertJreStartup("jbang.cmd", true);
	}

	@Test
	void cmdUsesJreFromPath() {
		assumeTrue(Util.isWindows());
		assertJreStartup("jbang.cmd", false);
	}

	@Test
	void powershellUsesJreFromJavaHome() {
		assumeTrue(Util.isWindows());
		assertJreStartup("jbang.ps1", true);
	}

	@Test
	void powershellUsesJreFromPath() {
		assumeTrue(Util.isWindows());
		assertJreStartup("jbang.ps1", false);
	}
}
