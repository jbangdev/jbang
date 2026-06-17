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
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class TestJdk extends BaseTest {

	private static final int SUCCESS_EXIT = BaseCommand.EXIT_OK;
	private static final String[] DEFAULT_PROVIDERS = { "--jdk-providers", "default,jbang,linked" };

	@BeforeEach
	void initJdk() {
		environmentVariables.clear(Settings.JBANG_CACHE_DIR + "_JDKS");
	}

	@Override
	protected CaptureResult<Integer> checkedRun(String... args) throws Exception {
		for (String arg : args) {
			if ("--jdk-providers".equals(arg)) {
				if (args.length > 0 && !"jdk".equals(args[0])) {
					String[] withJdk = new String[args.length + 1];
					withJdk[0] = "jdk";
					System.arraycopy(args, 0, withJdk, 1, args.length);
					return super.checkedRun(withJdk);
				}
				return super.checkedRun(args);
			}
		}
		String[] suffix = { "--jdk-providers", "default,jbang,linked", "--jdk-installer",
				"mock;versions=11.1,17.7,24.4" };
		int start = 0;
		java.util.List<String> newArgs = new java.util.ArrayList<>();
		newArgs.add("jdk");
		if (args.length > 0 && "jdk".equals(args[0])) {
			start = 1;
		}
		if (start < args.length) {
			newArgs.add(args[start]);
			start++;
		}
		for (String s : suffix) {
			newArgs.add(s);
		}
		for (int i = start; i < args.length; i++) {
			newArgs.add(args[i]);
		}
		return super.checkedRun(newArgs.toArray(new String[0]));
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
		Arrays.asList(11, 12, 13).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "list"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n   13 (13.0.7)\n   12 (12.0.7)\n   11 (11.0.7) <\n"));
	}

	@Test
	void testHasJdksInstalledWithJavaHome() throws Exception {
		Arrays.asList(11, 12).forEach(TestJdk::createMockJdk);

		Path jdkPath = jbangTempDir.resolve("jdk13");
		Util.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "13.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		CaptureResult<Integer> result = checkedRun(
				"jdk", "list", "--jdk-providers", "default,javahome,jbang");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n   13 (13.0.7)\n   12 (12.0.7)\n   11 (11.0.7) <\n"));
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
	void testJdkListDistros() throws Exception {
		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "list", "--distros"));
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		Pattern p = Pattern.compile("^ {3}[a-z0-9_]+?$", Pattern.MULTILINE);
		Matcher m = p.matcher(result.normalizedOut());
		assertThat(m.find(), equalTo(true));
	}

	@Test
	void testJdkListProviders() throws Exception {
		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "list", "--providers"));
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), equalTo("Available JDK Providers:\n" +
				"   default\n" +
				"   jbang\n" +
				"   linked\n"));
	}

	@Test
	void testDefault() throws Exception {
		Arrays.asList(11, 12, 13).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "default", "12"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 12"));

		result = checkedRun(withProviders("jdk", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("* -> 12.0.7-jbang"));
	}

	@Test
	void testDefaultPlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "default", "16+"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 17"));

		result = checkedRun(withProviders("jdk", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("* -> 17.0.7-jbang"));
	}

	@Test
	void testVersionDefaultAsDefault() throws Exception {
		Arrays.asList("11.0.7", "12.0.7", "12.0.9", "13.0.7").forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "default", "12"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK set to 12"));

		result = checkedRun(withProviders("jdk", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("* -> 12.0.7-jbang"));

		result = checkedRun(withProviders("jdk", "default", "-v", "12.0.9-jbang"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), startsWith("[jbang] Default JDK for version 12 set to 12"));

		result = checkedRun(withProviders("jdk", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("* -> 12.0.9-jbang"));
	}

	@Test
	void testHome() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "home"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith(File.separator + "currentjdk\n"));
	}

	@Test
	void testHomeDefault() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "home", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith(File.separator + "currentjdk\n"));
	}

	@Test
	void testHomeWithVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "home", "17"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith("cache" + File.separator + "jdks" + File.separator + "17\n"));
	}

	@Test
	void testHomePlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "home", "16+"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), endsWith("cache" + File.separator + "jdks" + File.separator + "17\n"));
	}

	@Test
	void testJavaEnv() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

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
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "default"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString(File.separator + "currentjdk"));
	}

	@Test
	void testJavaEnvWithVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "17"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "17"));
	}

	@Test
	void testJavaEnvWithDefaultVersion() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "11"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("jdk"));
	}

	@Test
	void testJavaRuntimeVersion() throws Exception {
		Arrays.asList(21).forEach(TestJdk::createMockJdkRuntime);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "21"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("jdk"));
	}

	@Test
	void testJavaEnvPlus() throws Exception {
		Arrays.asList(11, 14, 17).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "java-env", "16+"));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("cache" + File.separator + "jdks" + File.separator + "17"));
	}

	@Test
	void testDefaultWithJavaHome() throws Exception {
		Arrays.asList(11, 12, 13).forEach(TestJdk::createMockJdk);

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
		assertThat(result.normalizedOut(), containsString("* ->"));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenPathIsInvalid() {
		Exception e = assertThrows(Exception.class,
				() -> checkedRun(withProviders("jdk", "install", "11", "/non-existent-path")));
		Throwable target = e.getCause() != null ? e.getCause() : e;
		assertInstanceOf(IllegalArgumentException.class, target);
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
				containsString("JDK my11-linked has been linked to: " + javaDir.toPath()));
		assertTrue(Util.isLink(jdkPath.resolve("my11-linked")));
		assertTrue(Files.isSameFile(javaDir.toPath(), jdkPath.resolve("my11-linked").toRealPath()));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenJBangManagedVersionExistsAndInstallIsForced(
			@TempDir File javaDir) throws Exception {
		initMockJdkDir(javaDir.toPath(), "11.0.14");
		final Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks);
		Arrays.asList(11)
			.forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun("install", "--force", "my11", javaDir.toPath().toString());

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("JDK my11-linked has been linked to: " + javaDir.toPath().toString()));
		assertTrue(Util.isLink(jdkPath.resolve("my11-linked")));
		assertTrue(Files.isSameFile(javaDir.toPath(), jdkPath.resolve("my11-linked").toRealPath()));
	}

	@Test
	void testJdkInstallWithLinkingAndIntegerId() {
		assertThrows(IllegalArgumentException.class,
				() -> checkedRun("install", "--force", "11", "/non-existent-path"),
				"When providing an existing JDK path, the versionOrId parameter must be a non-integer id");
	}

	@Test
	void testJdkInstallWithLinkingToExistingJBangJdkPath() {
		final Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks).resolve("11");
		initMockJdkDir(jdkPath, "11.0.14");

		assertThrows(IllegalArgumentException.class,
				() -> checkedRun("install", "my11", jdkPath.toString()),
				"The provided path cannot point to a JBang managed JDK");
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWithDifferentVersion(@TempDir File javaDir) {
		initMockJdkDir(javaDir.toPath(), "11.0.14");

		assertThrows(Exception.class,
				() -> checkedRun(withProviders(
						"jdk", "install", "--force", "13", javaDir.toPath().toString())));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWithNoVersion(@TempDir File javaDir) {
		assertThrows(Exception.class,
				() -> checkedRun(withProviders(
						"jdk", "install", "--force", "13", javaDir.toPath().toString())));
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
				containsString("JDK my11-linked has been linked to: " + jdkOk));
		assertTrue(Util.isLink(jdkPath.resolve("my11-linked")));
		assertTrue(Files.isSameFile(jdkOk, (jdkPath.resolve("my11-linked").toRealPath())));
	}

	@Test
	void testJdkInstallSameVersion() throws Exception {
		Arrays.asList(11).forEach(TestJdk::createMockJdk);

		CaptureResult<Integer> result = checkedRun("install", "11");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), containsString("JDK is already installed"));
		assertThat(result.normalizedErr(), containsString("Use --force to install anyway"));
	}

	@Test
	void testJdkInstallSameVersionForced() throws Exception {
		Arrays.asList(11).forEach(TestJdk::createMockJdk);
		Path jdkPath = Settings.getCacheDir(Cache.CacheClass.jdks);

		CaptureResult<Integer> result = checkedRun("install", "--force", "11");

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(), containsString("Installing Mock JDK 11.1"));
		assertTrue(Files.isDirectory(jdkPath.resolve("11.1")));
		assertTrue(Util.isLink(jdkPath.resolve("11")));
	}

	@Test
	void testExistingJdkUninstall() throws Exception {
		int jdkVersion = 14;
		createMockJdk(jdkVersion);

		CaptureResult<Integer> result = checkedRun(withProviders("jdk", "list", "--show-details"));
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(countLines(result.normalizedOut()), equalTo(4));
		assertThat(result.normalizedOut(), containsString("default"));
		assertThat(result.normalizedOut(), containsString("14-default"));
		assertThat(result.normalizedOut(), containsString("14.0.7-jbang"));

		result = checkedRun(withProviders(
				"jdk", "uninstall", Integer.toString(jdkVersion)));

		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				containsString("Uninstalled JDK:"));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Uninstalled JDK:\n  " + jdkVersion));

		result = checkedRun(withProviders("jdk", "list", "--show-details"));
		assertThat(result.result, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(), containsString("No JDKs installed"));
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
				containsString("Uninstalled JDK:"));
		assertThat(result.normalizedErr(),
				containsString("[jbang] Uninstalled JDK:\n  " + jdkVersion));
	}

	@Test
	void testNonExistingJdkUninstall() {
		assertThrows(Exception.class,
				() -> checkedRun(withProviders("jdk", "uninstall", "16")));
	}

	public static void createMockJdk(int jdkVersion) {
		String fullVersion = jdkVersion + ".0.7";
		createMockJdk(fullVersion);
	}

	public static void createMockJdk(String fullVersion) {
		createMockJdk(fullVersion, TestJdk::initMockJdkDir);
	}

	public static void createMockJdkRuntime(int jdkVersion) {
		String fullVersion = jdkVersion + ".0.7";
		createMockJdk(fullVersion, TestJdk::initMockJdkDirRuntime);
	}

	public static void createMockJdk(String fullVersion, BiConsumer<Path, String> init) {
		Path jdksPath = Settings.getCacheDir(Cache.CacheClass.jdks);
		Path jdkPath = jdksPath.resolve(fullVersion + "-jbang");
		init.accept(jdkPath, fullVersion);
		Path deflink = Settings.getDefaultJdkDir();
		if (!Files.exists(deflink)) {
			Util.createLink(deflink, jdkPath);
		}
		Path defvlink = jdksPath.resolve(String.valueOf(JavaUtil.parseJavaVersion(fullVersion)));
		if (!Files.exists(defvlink)) {
			Util.createLink(defvlink, jdkPath);
		}
	}

	public static void initMockJdkDirRuntime(Path jdkPath, String version) {
		initMockJdkDir(jdkPath, version, "JAVA_RUNTIME_VERSION");
	}

	public static void initMockJdkDir(Path jdkPath, String version) {
		initMockJdkDir(jdkPath, version, "JAVA_VERSION");
	}

	private static void initMockJdkDir(Path jdkPath, String version, String key) {
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

	private int countLines(String text) {
		return text.lines()
			.filter(line -> !line.trim().isEmpty())
			.mapToInt(line -> 1)
			.sum();
	}
}
