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

import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JreRuntimeIT extends BaseIT {
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

		jbangDir = scratch().resolve("runtime-jbang");
		Path bin = Files.createDirectories(jbangDir.resolve("bin"));
		try (Stream<Path> files = Files.list(Paths.get("build/install/jbang/bin").toAbsolutePath())) {
			for (Path file : (Iterable<Path>) files::iterator) {
				Files.copy(file, bin.resolve(file.getFileName()));
			}
		}
		Path source = baseDir().resolve("jreapp.java");
		Util.writeString(source, "public class jreapp {\n"
				+ "  public static void main(String[] args) {\n"
				+ "    System.out.println(System.getProperty(\"java.home\"));\n"
				+ "    System.out.println(javax.tools.ToolProvider.getSystemJavaCompiler() == null);\n"
				+ "    System.out.println(String.join(\" \", args));\n"
				+ "  }\n"
				+ "}\n");
		Map<String, String> buildEnv = runtimeEnv();
		buildEnv.put("JAVA_HOME", javaHome.toString());
		String launcher = bin.resolve(Util.isWindows() ? "jbang.cmd" : "jbang").toString();
		CommandResult build = shell(buildEnv, "\"" + launcher + "\" export local --output runtime-app.jar jreapp.java");
		assertTrue(build.exitCode() == 0, build.err() + build.out());
	}

	private Map<String, String> runtimeEnv() {
		Map<String, String> env = new HashMap<>(baseEnv);
		System.getenv()
			.keySet()
			.stream()
			.filter(name -> name.startsWith("JAVA_HOME_"))
			.forEach(name -> env.put(name, ""));
		// Launch JBang on a JDK; runtime selection is independent of startup support.
		env.put("JAVA_HOME", System.getProperty("java.home"));
		env.put("JAVA_HOME_JRE", jreHome.toString());
		env.put("JBANG_DIR", jbangDir.toString());
		env.put("JBANG_CACHE_DIR", jbangDir.resolve("cache").toString());
		env.put("JBANG_USE_NATIVE", "false");
		String path = Paths.get(System.getProperty("java.home")).resolve("bin").toString();
		if (Util.isWindows()) {
			path += File.pathSeparator + Paths.get(System.getenv("SystemRoot"), "System32")
					+ File.pathSeparator
					+ Paths.get(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0");
		} else {
			path += File.pathSeparator + "/usr/bin" + File.pathSeparator + "/bin";
		}
		env.put("PATH", path);
		return env;
	}

	private CommandResult launch(String script, String... arguments) {
		return launch(runtimeEnv(), script, arguments);
	}

	private CommandResult launch(Map<String, String> env, String script, String... arguments) {
		Path path = Paths.get("src/main/scripts").resolve(script).toAbsolutePath();
		List<String> command = new ArrayList<>();
		if ("jbang.cmd".equals(script)) {
			command.addAll(Arrays.asList(System.getenv("ComSpec"), "/c", path.toString()));
		} else if ("jbang.ps1".equals(script)) {
			// Do not inherit PowerShell 7 modules into Windows PowerShell.
			command.addAll(Arrays.asList("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
					"$env:PSModulePath = $PSHOME + '\\Modules'; & '" + path + "' " + String.join(" ", arguments)
							+ "; exit $LASTEXITCODE"));
		} else {
			Path bash = Util.searchPath("bash");
			assumeTrue(bash != null, "Bash is not available");
			env.put("PATH", env.get("PATH") + File.pathSeparator + bash.getParent());
			command.addAll(Arrays.asList(bash.toString(), path.toString()));
		}
		if (!"jbang.ps1".equals(script)) {
			command.addAll(Arrays.asList(arguments));
		}
		return run(baseDir(), env, command);
	}

	@Test
	void runsArchiveOnJre() {
		String script = Util.isWindows() ? "jbang.cmd" : "jbang";
		CommandResult result = launch(script, "run", "--jdk-providers", "multihome",
				"--java", JavaUtil.getCurrentMajorJavaVersion() + "",
				"runtime-app.jar", "hello", "JRE");
		assertTrue(result.exitCode() == 0, result.err() + result.out());
		assertThat(result)
			.succeeded()
			.outContains(jreHome.toString())
			.outContains("true")
			.outContains("hello JRE")
			.errNotContains("Downloading JDK")
			.errNotContains("valid Java");
		assertFalse(Files.exists(jbangDir.resolve("cache").resolve("jdks")));
	}

	@Test
	void reusesCachedScriptOnJre() {
		String script = Util.isWindows() ? "jbang.cmd" : "jbang";
		assertThat(launch(script, "run", "--jdk-providers", "multihome", "jreapp.java", "cached"))
			.succeeded()
			.outContains(jreHome.toString())
			.outContains("true")
			.outContains("cached")
			.errNotContains("Building jar")
			.errNotContains("Downloading JDK");
	}

	@Test
	void buildsAndRunsWithSameJdkWhenJreIsAvailable() {
		Map<String, String> env = runtimeEnv();
		env.put("JAVA_HOME_COMPILER", System.getProperty("java.home"));
		String script = Util.isWindows() ? "jbang.cmd" : "jbang";
		assertThat(launch(env, script, "run", "--fresh", "--jdk-providers", "multihome",
				"jreapp.java", "rebuilt"))
			.succeeded()
			.outContains(System.getProperty("java.home"))
			.outContains("false")
			.outContains("rebuilt")
			.errNotContains("Downloading JDK");
	}

	@Test
	void runsArchiveAliasOnJre() {
		String script = Util.isWindows() ? "jbang.cmd" : "jbang";
		assertThat(launch(script, "alias", "add", "--name", "runtime-app", "runtime-app.jar")).succeeded();
		assertThat(launch(script, "run", "--jdk-providers", "multihome", "runtime-app", "alias"))
			.succeeded()
			.outContains(jreHome.toString())
			.outContains("true")
			.outContains("alias")
			.errNotContains("Downloading JDK");
	}
}
