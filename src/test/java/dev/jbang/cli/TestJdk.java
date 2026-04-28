package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
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

class TestJdk extends BaseTest {

	private static final int SUCCESS_EXIT = BaseCommand.EXIT_OK;
	private static final String[] DEFAULT_PROVIDERS = { "--jdk-providers", "default,jbang,linked" };

	@BeforeEach
	void initJdk() {
		environmentVariables.clear(Settings.JBANG_CACHE_DIR + "_JDKS");
	}

	private String[] withProviders(String... args) {
		String[] result = new String[args.length + DEFAULT_PROVIDERS.length];
		System.arraycopy(args, 0, result, 0, args.length);
		System.arraycopy(DEFAULT_PROVIDERS, 0, result, args.length, DEFAULT_PROVIDERS.length);
		return result;
	}

	@Test
	void testNoJdksInstalled() throws Exception {
		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "list"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), equalTo("No JDKs installed\n"));
	}

	@Test
	void testHasJdksInstalled() throws Exception {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "list"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n   11 (11.0.7) <\n   12 (12.0.7)\n   13 (13.0.7)\n"));
	}

	@Test
	void testHasJdksInstalledWithJavaHome() throws Exception {
		Arrays.asList(11, 12).forEach(this::createMockJdk);

		Path jdkPath = jbangTempDir.resolve("jdk13");
		Util.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "13.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		CaptureResult<Integer> result = checkedRun(
				"jdk", "list", "--jdk-providers", "default,javahome,jbang");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n   11 (11.0.7) <\n   12 (12.0.7)\n   13 (13.0.7)\n"));
	}

	@Test
	void testJdksAvailable() throws Exception {
		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "list", "--available"));
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		Pattern p = Pattern.compile("^ {3}\\d+ \\(.+?\\)$", Pattern.MULTILINE);
		Matcher m = p.matcher(result.normalizedOut());
		assertThat(m.find(), equalTo(true));
	}

	@Test
	void testDefault() throws Exception {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "default", "12"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 12"));

		result = checkedRun(withProviders("jdk", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), equalTo("[jbang] Default JDK is currently set to 12\n"));
	}

	@Test
	void testDefaultPlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "default", "16+"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 17"));

		result = checkedRun(withProviders("jdk", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), equalTo("[jbang] Default JDK is currently set to 17\n"));
	}

	@Test
	void testHome() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "home"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith(File.separator + "currentjdk\n"));
	}

	@Test
	void testHomeDefault() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "home", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith(File.separator + "currentjdk\n"));
	}

	@Test
	void testHomeWithVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "home", "17"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith("cache" + File.separator + "jdks" + File.separator + "17\n"));
	}

	@Test
	void testHomePlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "home", "16+"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith("cache" + File.separator + "jdks" + File.separator + "17\n"));
	}

	@Test
	void testJavaEnv() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				containsString(File.separator + "currentjdk" + File.separator + "bin" + File.pathSeparator));

		if (Util.isWindows()) {
			environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "powershell");
			result = checkedRun(withProviders("jdk", "java-env"));

			assertThat(result.result, equalTo(SUCCESS_EXIT));
			assertThat(result.normalizedOut(),
					containsString(File.separator + "currentjdk" + File.separator + "bin" + File.pathSeparator));
		}
	}

	@Test
	void testJavaEnvDefault() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString(File.separator + "currentjdk"));
	}

	@Test
	void testJavaEnvWithVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "17"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "17"));
	}

	@Test
	void testJavaEnvWithDefaultVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "11"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "11"));
	}

	@Test
	void testJavaRuntimeVersion() throws Exception {
		Arrays.asList(21).forEach(this::createMockJdkRuntime);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "21"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "21"));
	}

	@Test
	void testJavaEnvPlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "16+"));

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
				"jdk", "default", "12", "--jdk-providers", "default,javahome,jbang");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 12"));

		result = checkedRun(withProviders("jdk", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), equalTo("[jbang] Default JDK is currently set to 12\n"));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenPathIsInvalid() {
		try {
			checkedRun(withProviders("jdk", "install", "11", "--path", "/non-existent-path"));
		} catch (Exception e) {
			Throwable target = e.getCause() != null ? e.getCause() : e;
			assertInstanceOf(IllegalArgumentException.class, target);
		}
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenJBangManagedVersionDoesNotExist(@TempDir File javaDir)
			throws Exception {
		initMockJdkDir(javaDir.toPath(), "11.0.14");
		final Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks);
		jdkPath.toFile().mkdir();

		CaptureResult<Integer> result = checkedRun(withProviders(
				"jdk", "install", "11", "--path", javaDir.toPath().toString()));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] JDK 11 has been linked to: " + javaDir.toPath() + "\n"));
		assertTrue(Util.isLink(jdkPath.resolve("11")));
		System.err.println("ASSERT: " + javaDir.toPath() + " - " + jdkPath.resolve("11").toRealPath());
		assertTrue(Files.isSameFile(javaDir.toPath(), jdkPath.resolve("11").toRealPath()));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenJBangManagedVersionExistsAndInstallIsForced(
			@TempDir File javaDir) throws Exception {
		initMockJdkDir(javaDir.toPath(), "11.0.14");
		final Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks);
		Arrays.asList(11)
			.forEach(this::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders(
				"jdk", "install", "--force", "11", "--path", javaDir.toPath().toString()));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] JDK 11 has been linked to: " + javaDir.toPath().toString() + "\n"));
		assertTrue(Util.isLink(jdkPath.resolve("11")));
		assertTrue(Files.isSameFile(javaDir.toPath(), jdkPath.resolve("11").toRealPath()));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWithDifferentVersion(@TempDir File javaDir) {
		initMockJdkDir(javaDir.toPath(), "11.0.14");

		try {
			checkedRun(withProviders(
					"jdk", "install", "--force", "13", "--path", javaDir.toPath().toString()));
		} catch (Exception e) {
			// expected
		}
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWithNoVersion(@TempDir File javaDir) {
		try {
			checkedRun(withProviders(
					"jdk", "install", "--force", "13", "--path", javaDir.toPath().toString()));
		} catch (Exception e) {
			// expected
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

		CaptureResult<Integer> result = checkedRun(withProviders(
				"jdk", "install", "--force", "11", "--path", jdkBroken.toString()));

		assertThat(result.result, equalTo(SUCCESS_EXIT));

		Util.deletePath(jdkBroken, false);

		result = checkedRun(withProviders(
				"jdk", "install", "--force", "11", "--path", jdkOk.toString()));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] JDK 11 has been linked to: " + jdkOk + "\n"));
		assertTrue(Util.isLink(jdkPath.resolve("11")));
		assertTrue(Files.isSameFile(jdkOk, (jdkPath.resolve("11").toRealPath())));
	}

	@Test
	void testExistingJdkUninstall() throws Exception {
		int jdkVersion = 14;
		createMockJdk(jdkVersion);

		CaptureResult<Integer> result = checkedRun(withProviders(
				"jdk", "uninstall", Integer.toString(jdkVersion)));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Default JDK unset"));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Uninstalled JDK:\n  " + jdkVersion));
	}

	@Test
	void testExistingJdkUninstallWithJavaHome() throws Exception {
		int jdkVersion = 14;
		createMockJdk(jdkVersion);

		Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks).resolve("14");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		CaptureResult<Integer> result = checkedRun(
				"jdk", "uninstall", Integer.toString(jdkVersion),
				"--jdk-providers", "default,javahome,jbang");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Default JDK unset"));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Uninstalled JDK:\n  " + jdkVersion));
	}

	@Test
	void testNonExistingJdkUninstall() {
		try {
			checkedRun(withProviders("jdk", "uninstall", "16"));
		} catch (Exception e) {
			// expected
		}
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
