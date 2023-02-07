package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.net.jdkproviders.JBangJdkProvider;
import dev.jbang.util.Util;

import picocli.CommandLine;

class TestJdk extends BaseTest {

	private static final int SUCCESS_EXIT = CommandLine.ExitCode.OK;

	@BeforeEach
	void initJdk() {
		environmentVariables.clear(Settings.JBANG_CACHE_DIR + "_JDKS");
	}

	@Test
	void testNoJdksInstalled() throws IOException {
		ExecutionResult result = checkedRun(jdk -> jdk.list(false, false, FormatMixin.Format.text));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), equalTo("No JDKs installed\n"));
	}

	@Test
	void testHasJdksInstalled() throws IOException {
		final Path jdkPath = JBangJdkProvider.getJdksPath();
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.list(false, false, FormatMixin.Format.text));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n   11 (11.0.7) <\n   12 (12.0.7)\n   13 (13.0.7)\n"));
	}

	@Test
	void testHasJdksInstalledWithJavaHome() throws IOException {
		Arrays.asList(11, 12).forEach(this::createMockJdk);

		Path jdkPath = jbangTempDir.resolve("jdk13");
		Util.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "13.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		ExecutionResult result = checkedRun((Jdk jdk) -> jdk.list(false, false, FormatMixin.Format.text),
				"jdk", "--jdk-providers", "javahome,jbang");

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n   11 (11.0.7) <\n   12 (12.0.7)\n   13 (13.0.7)\n"));
	}

	@Test
	void testDefault() throws IOException {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.defaultJdk("12"));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 12"));

		result = checkedRun(jdk -> jdk.defaultJdk(null));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), equalTo("[jbang] Default JDK is currently set to 12\n"));
	}

	@Test
	void testDefaultPlus() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.defaultJdk("16+"));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 17"));

		result = checkedRun(jdk -> jdk.defaultJdk(null));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), equalTo("[jbang] Default JDK is currently set to 17\n"));
	}

	@Test
	void testHome() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.home(null));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith(File.separator + "currentjdk\n"));
	}

	@Test
	void testHomeDefault() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.home("default"));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith(File.separator + "currentjdk\n"));
	}

	@Test
	void testHomeWithVersion() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.home("17"));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith("cache" + File.separator + "jdks" + File.separator + "17\n"));
	}

	@Test
	void testHomePlus() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.home("16+"));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith("cache" + File.separator + "jdks" + File.separator + "17\n"));
	}

	@Test
	void testJavaEnv() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.javaEnv(null));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString(File.separator + "currentjdk"));
	}

	@Test
	void testJavaEnvDefault() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.javaEnv("default"));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString(File.separator + "currentjdk"));
	}

	@Test
	void testJavaEnvWithVersion() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.javaEnv("17"));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "17"));
	}

	@Test
	void testJavaEnvPlus() throws IOException {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> jdk.javaEnv("16+"));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "17"));
	}

	@Test
	void testDefaultWithJavaHome() throws IOException {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		Path jdkPath = jbangTempDir.resolve("jdk12");
		Util.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "12.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		ExecutionResult result = checkedRun((Jdk jdk) -> jdk.defaultJdk("12"), "jdk", "--jdk-providers",
				"javahome,jbang");

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 12"));

		result = checkedRun(jdk -> jdk.defaultJdk(null));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), equalTo("[jbang] Default JDK is currently set to 12\n"));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenPathIsInvalid() {
		checkedRunWithException(jdk -> {
			try {
				jdk.install(true, "11", "/non-existent-path");
			} catch (Exception e) {
				assertTrue(e instanceof ExitException);
				assertEquals("Unable to resolve path as directory: /non-existent-path", e.getMessage());
			}
			return null;
		});
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenJBangManagedVersionDoesNotExist(@TempDir File javaDir)
			throws IOException {
		initMockJdkDir(javaDir.toPath(), "11.0.14");
		final Path jdkPath = JBangJdkProvider.getJdksPath();
		jdkPath.toFile().mkdir();

		ExecutionResult result = checkedRun(jdk -> {
			try {
				return jdk.install(false, "11", javaDir.toPath().toString());
			} catch (IOException e) {
				// Escaping with a runtime exception
				throw new RuntimeException(e);
			}
		});

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] JDK 11 has been linked to: " + javaDir.toPath().toString() + "\n"));
		assertTrue(Files.isSymbolicLink(jdkPath.resolve("11")));
		assertEquals(javaDir.toPath(), Files.readSymbolicLink(jdkPath.resolve("11")));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenJBangManagedVersionExistsAndInstallIsForced(
			@TempDir File javaDir) throws IOException {
		initMockJdkDir(javaDir.toPath(), "11.0.14");
		final Path jdkPath = JBangJdkProvider.getJdksPath();
		Arrays	.asList(11)
				.forEach(this::createMockJdk);

		ExecutionResult result = checkedRun(jdk -> {
			try {
				return jdk.install(true, "11", javaDir.toPath().toString());
			} catch (IOException e) {
				// Escaping with a runtime exception
				throw new RuntimeException(e);
			}
		});

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] JDK 11 has been linked to: " + javaDir.toPath().toString() + "\n"));
		assertTrue(Files.isSymbolicLink(jdkPath.resolve("11")));
		assertEquals(javaDir.toPath(), Files.readSymbolicLink(jdkPath.resolve("11")));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWithDifferentVersion(@TempDir File javaDir)
			throws IOException {
		initMockJdkDir(javaDir.toPath(), "11.0.14");

		checkedRunWithException(jdk -> {
			try {
				jdk.install(true, "13", javaDir.toPath().toString());
			} catch (Exception e) {
				assertTrue(e instanceof ExitException);
				assertEquals("Java version in given path: " + javaDir.toPath()
						+ " is " + 11 + " which does not match the requested version " + 13 + "", e.getMessage());
			}
			return null;
		});
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWithNoVersion(@TempDir File javaDir) throws IOException {

		File release = new File(javaDir, "release");

		checkedRunWithException(jdk -> {
			try {
				jdk.install(true, "13", javaDir.toPath().toString());
			} catch (Exception e) {
				assertTrue(e instanceof ExitException);
				assertEquals("Unable to determine Java version in given path: " + javaDir.toPath(), e.getMessage());
			}
			return null;
		});
	}

	@Test
	void testJdkInstallWithLinkingToExistingBrokenLink(
			@TempDir File javaDir) throws IOException {
		Path jdkBroken = javaDir.toPath().resolve("14broken");
		Path jdkOk = javaDir.toPath().resolve("14ok");
		initMockJdkDir(jdkBroken, "11.0.14-broken");
		initMockJdkDir(jdkOk, "11.0.14-ok");
		final Path jdkPath = JBangJdkProvider.getJdksPath();

		ExecutionResult result = checkedRun(jdk -> {
			try {
				return jdk.install(true, "11", jdkBroken.toString());
			} catch (IOException e) {
				// Escaping with a runtime exception
				throw new RuntimeException(e);
			}
		});

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));

		Util.deletePath(jdkBroken, false);

		result = checkedRun(jdk -> {
			try {
				return jdk.install(true, "11", jdkOk.toString());
			} catch (IOException e) {
				// Escaping with a runtime exception
				throw new RuntimeException(e);
			}
		});

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] JDK 11 has been linked to: " + jdkOk + "\n"));
		assertTrue(Files.isSymbolicLink(jdkPath.resolve("11")));
		assertEquals(jdkOk, Files.readSymbolicLink(jdkPath.resolve("11")));
	}

	@Test
	void testExistingJdkUninstall() throws IOException {
		int jdkVersion = 14;
		createMockJdk(jdkVersion);

		ExecutionResult result = checkedRun(jdk -> jdk.uninstall(Integer.toString(jdkVersion)));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Default JDK unset"));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Uninstalled JDK:\n  " + jdkVersion));
	}

	@Test
	void testExistingJdkUninstallWithJavaHome() throws IOException {
		int jdkVersion = 14;
		createMockJdk(jdkVersion);

		Path jdkPath = JBangJdkProvider.getJdksPath().resolve("14");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		ExecutionResult result = checkedRun((Jdk jdk) -> jdk.uninstall(Integer.toString(jdkVersion)), "jdk",
				"--jdk-providers",
				"javahome,jbang");

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Default JDK unset"));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Uninstalled JDK:\n  " + jdkVersion));
	}

	@Test
	void testNonExistingJdkUninstall() throws IOException {
		checkedRunWithException(jdk -> {
			try {
				jdk.uninstall("16");
			} catch (Exception e) {
				assertTrue(e instanceof ExitException);
				assertEquals("JDK 16 is not installed", e.getMessage());
			}
			return null;
		});
	}

	private ExecutionResult checkedRun(Function<Jdk, Integer> commandRunner) throws IOException {
		return checkedRun(commandRunner, "jdk", "--jdk-providers", "default,jbang");
	}

	private void checkedRunWithException(Function<Jdk, Integer> commandRunner) {
		try {
			checkedRun(commandRunner, "jdk");
		} catch (Exception e) {
			// Ignore
		}
	}

	private void createMockJdk(int jdkVersion) {
		Path jdkPath = JBangJdkProvider.getJdksPath().resolve(String.valueOf(jdkVersion));
		initMockJdkDir(jdkPath, jdkVersion + ".0.7");
		Path def = Settings.getCurrentJdkDir();
		if (!Files.exists(def)) {
			Util.createLink(def, jdkPath);
		}
	}

	private void initMockJdkDir(Path jdkPath, String version) {
		Util.mkdirs(jdkPath);
		String rawJavaVersion = "JAVA_VERSION=\"" + version + "\"";
		Path release = jdkPath.resolve("release");
		try {
			Util.writeString(release, rawJavaVersion);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
