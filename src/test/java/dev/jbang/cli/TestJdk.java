package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.util.Util;

import picocli.CommandLine;

class TestJdk extends BaseTest {

	private static final int SUCCESS_EXIT = CommandLine.ExitCode.OK;

	@BeforeEach
	void initJdk() {
		environmentVariables.clear(Settings.JBANG_CACHE_DIR + "_JDKS");
	}

	@Test
	void testNoJdksInstalled() throws Exception {
		CaptureResult<Integer> result = checkedRun("list");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), equalTo("No JDKs installed\n"));
	}

	@Test
	void testHasJdksInstalled() throws Exception {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("list");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n   13 (13.0.7)\n   12 (12.0.7)\n   11 (11.0.7) <\n"));
	}

	@Test
	void testHasJdksInstalledWithJavaHome() throws Exception {
		Arrays.asList(11, 12).forEach(this::createMockJdk);

		Path jdkPath = jbangTempDir.resolve("jdk13");
		Util.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "13.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		CaptureResult<Integer> result = checkedRun("jdk", "--jdk-providers", "default,javahome,jbang", "list");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n   13 (13.0.7)\n   12 (12.0.7)\n   11 (11.0.7) <\n"));
	}

	@Test
	void testJdksAvailable() throws Exception {
		CaptureResult<Integer> result = checkedRun("list", "-a");
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		Pattern p = Pattern.compile("^ {3}\\d+ \\(.+?\\)$", Pattern.MULTILINE);
		Matcher m = p.matcher(result.normalizedOut());
		assertThat(m.find(), equalTo(true));
	}

	@Test
	void testDefault() throws Exception {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("default", "12");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), containsString("Default JDK set to 12"));

		result = checkedRun("default");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("* -> 12-jbang"));
	}

	@Test
	void testDefaultPlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("default", "16+");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), containsString("Default JDK set to 17"));

		result = checkedRun("default");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("* -> 17-jbang"));
	}

	@Test
	void testHome() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("home");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith(File.separator + "currentjdk\n"));
	}

	@Test
	void testHomeDefault() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("home", "default");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith(File.separator + "currentjdk\n"));
	}

	@Test
	void testHomeWithVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("home", "17");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith("cache" + File.separator + "jdks" + File.separator + "17\n"));
	}

	@Test
	void testHomePlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("home", "16+");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith("cache" + File.separator + "jdks" + File.separator + "17\n"));
	}

	@Test
	void testJavaEnv() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("java-env");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				containsString(File.separator + "currentjdk" + File.separator + "bin" + File.pathSeparator));

		if (Util.isWindows()) {
			// By default, on Windows we only test with CMD, so let's retest
			// pretending we're running from PowerShell
			environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "powershell");
			result = checkedRun("java-env");

			assertThat(result.result, equalTo(SUCCESS_EXIT));
			assertThat(result.normalizedOut(),
					containsString(File.separator + "currentjdk" + File.separator + "bin" + File.pathSeparator));
		}
	}

	@Test
	void testJavaEnvDefault() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("java-env", "default");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString(File.separator + "currentjdk"));
	}

	@Test
	void testJavaEnvWithVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("java-env", "17");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "17"));
	}

	@Test
	void testJavaEnvWithDefaultVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("java-env", "11");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "11"));
	}

	@Test
	void testJavaRuntimeVersion() throws Exception {
		Arrays.asList(21).forEach(this::createMockJdkRuntime);

		CaptureResult<Integer> result = checkedRun("java-env", "21");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "21"));
	}

	@Test
	void testJavaEnvPlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("java-env", "16+");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "17"));
	}

	@Test
	void testDefaultWithJavaHome() throws Exception {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		Path jdkPath = jbangTempDir.resolve("jdk12");
		Util.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "12.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		CaptureResult<Integer> result = checkedRun(
				"jdk", "--jdk-providers", "default,javahome,jbang", "default", "12");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), containsString("Default JDK set to 12"));

		result = checkedRun(
				"jdk", "--jdk-providers", "default,javahome,jbang", "default");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("* -> javahome"));
	}

	@Test
	void testJdkInstallWithLinkingAndIntegerId() {
		try {
			checkedRun("install", "--force", "11", "/non-existent-path");
			assertThat("Expected an exception to be thrown", false);
		} catch (Exception e) {
			assertInstanceOf(IllegalArgumentException.class, e);
			assertEquals("When providing an existing JDK path, the versionOrId parameter must be a non-integer id",
					e.getMessage());
		}
	}

	@Test
	void testJdkInstallWithLinkingToExistingJBangJdkPath() {
		final Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks).resolve("11");
		initMockJdkDir(jdkPath, "11.0.14");

		try {
			checkedRun("install", "my11", jdkPath.toString());
			assertThat("Expected an exception to be thrown", false);
		} catch (Exception e) {
			assertInstanceOf(IllegalArgumentException.class, e);
			assertEquals("The provided path cannot point to a JBang managed JDK", e.getMessage());
		}
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenPathIsInvalid() {
		try {
			checkedRun("install", "--force", "my11", "/non-existent-path");
			assertThat("Expected an exception to be thrown", false);
		} catch (Exception e) {
			assertInstanceOf(IllegalArgumentException.class, e);
			assertEquals("Unable to resolve path as directory: " + File.separator + "non-existent-path",
					e.getMessage());
		}
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenJBangManagedVersionDoesNotExist(@TempDir File javaDir)
			throws Exception {
		initMockJdkDir(javaDir.toPath(), "11.0.14");
		final Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks);
		jdkPath.toFile().mkdir();

		CaptureResult<Integer> result = checkedRun("install", "my11", javaDir.toPath().toString());

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("JDK my11-linked has been linked to: " + javaDir.toPath() + "\n"));
		assertTrue(Util.isLink(jdkPath.resolve("my11-linked")));
		assertTrue(Files.isSameFile(javaDir.toPath(), jdkPath.resolve("my11-linked").toRealPath()));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenJBangManagedVersionExistsAndInstallIsForced(
			@TempDir File javaDir) throws Exception {
		initMockJdkDir(javaDir.toPath(), "11.0.14");
		final Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks);
		Arrays.asList(11)
			.forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun("install", "--force", "my11", javaDir.toPath().toString());

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("JDK my11-linked has been linked to: " + javaDir.toPath().toString() + "\n"));
		assertTrue(Util.isLink(jdkPath.resolve("my11-linked")));
		assertTrue(Files.isSameFile(javaDir.toPath(), jdkPath.resolve("my11-linked").toRealPath()));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWithNoVersion(@TempDir File javaDir) {
		try {
			checkedRun("install", "--force", "my13", javaDir.toPath().toString());
			assertThat("Expected an exception to be thrown", false);
		} catch (Exception e) {
			assertInstanceOf(IllegalArgumentException.class, e);
			assertEquals("Unable to create link to JDK in path: " + javaDir.toPath(), e.getMessage());
		}
	}

	@Test
	void testJdkInstallWithLinkingToExistingBrokenLink(
			@TempDir File javaDir) throws Exception {
		Path jdkBroken = javaDir.toPath().resolve("14broken");
		Path jdkOk = javaDir.toPath().resolve("14ok");
		initMockJdkDir(jdkBroken, "11.0.14-broken");
		initMockJdkDir(jdkOk, "11.0.14-ok");
		final Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks);

		CaptureResult<Integer> result = checkedRun("install", "--force", "my11", jdkBroken.toString());

		assertThat(result.result, equalTo(SUCCESS_EXIT));

		Util.deletePath(jdkBroken, false);

		result = checkedRun("install", "--force", "my11", jdkOk.toString());

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString(" JDK my11-linked has been linked to: " + jdkOk + "\n"));
		assertTrue(Util.isLink(jdkPath.resolve("my11-linked")));
		assertTrue(Files.isSameFile(jdkOk, (jdkPath.resolve("my11-linked").toRealPath())));
	}

	@Test
	void testExistingJdkUninstall() throws Exception {
		int jdkVersion = 14;
		createMockJdk(jdkVersion);

		CaptureResult<Integer> result = checkedRun("uninstall", Integer.toString(jdkVersion));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("Global default JDK unset"));
		assertThat(result.normalizedErr(),
				containsString("Versioned default JDK unset"));
		assertThat(result.normalizedErr(),
				containsString("Uninstalled JDK:\n  " + jdkVersion));
	}

	@Test
	void testExistingJdkUninstallWithJavaHome() throws Exception {
		int jdkVersion = 14;
		createMockJdk(jdkVersion);

		Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks).resolve("14");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		CaptureResult<Integer> result = checkedRun("jdk", "--jdk-providers", "default,javahome,jbang", "uninstall",
				Integer.toString(jdkVersion));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("Global default JDK unset"));
		assertThat(result.normalizedErr(),
				containsString("Versioned default JDK unset"));
		assertThat(result.normalizedErr(),
				containsString("Uninstalled JDK:\n  " + jdkVersion));
	}

	@Test
	void testNonExistingJdkUninstall() throws IOException {
		try {
			checkedRun("uninstall", "16");
		} catch (Exception e) {
			assertInstanceOf(ExitException.class, e);
			assertEquals("JDK 16 is not installed", e.getMessage());
		}
	}

	private CaptureResult<Integer> checkedRun(String... args) throws Exception {
		String[] newArgs;
		if (args.length > 0 && "jdk".equals(args[0])) {
			newArgs = args;
		} else {
			String[] additionalArgs = { "jdk", "--jdk-providers", "default,jbang,linked" };
			newArgs = new String[additionalArgs.length + args.length];
			System.arraycopy(additionalArgs, 0, newArgs, 0, additionalArgs.length);
			System.arraycopy(args, 0, newArgs, additionalArgs.length, args.length);
		}
		return checkedRun(null, newArgs);
	}

	private void createMockJdk(int jdkVersion) {
		createMockJdk(jdkVersion, this::initMockJdkDir);
	}

	private void createMockJdkRuntime(int jdkVersion) {
		createMockJdk(jdkVersion, this::initMockJdkDirRuntime);
	}

	private void createMockJdk(int jdkVersion, BiConsumer<Path, String> init) {
		Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks).resolve(String.valueOf(jdkVersion));
		init.accept(jdkPath, jdkVersion + ".0.7");
		Path link = Settings.getDefaultJdkDir();
		if (!Files.exists(link)) {
			Util.createLink(link, jdkPath);
		}
	}

	private void initMockJdkDirRuntime(Path jdkPath, String version) {
		initMockJdkDir(jdkPath, version, "JAVA_RUNTIME_VERSION");
	}

	private void initMockJdkDir(Path jdkPath, String version) {
		initMockJdkDir(jdkPath, version, "JAVA_VERSION");
	}

	private void initMockJdkDir(Path jdkPath, String version, String key) {
		Util.mkdirs(jdkPath);
		Path jdkBinPath = jdkPath.resolve("bin");
		Util.mkdirs(jdkBinPath);
		String rawJavaVersion = key + "=\"" + version + "\"";
		Path release = jdkPath.resolve("release");
		try {
			Path javacPath = jdkBinPath.resolve("javac");
			Util.writeString(javacPath, "dummy");
			javacPath.toFile().setExecutable(true, true);
			Util.writeString(jdkBinPath.resolve("javac.exe"), "dummy");
			Util.writeString(release, rawJavaVersion);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
