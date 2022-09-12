package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.net.JdkManager;
import dev.jbang.util.Util;

import picocli.CommandLine;

class TestJdk extends BaseTest {

	private static final int SUCCESS_EXIT = CommandLine.ExitCode.OK;

	@Test
	void testNoJdksInstalled() throws IOException {
		ExecutionResult result = checkedRun(jdk -> jdk.list(false, FormatMixin.Format.text));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("No JDKs installed\n"));
	}

	@Test
	void testHasJdksInstalled() throws IOException {
		final Path jdkPath = JdkManager.getJdksPath();
		Arrays	.asList("11", "12", "13")
				.forEach(jdkId -> new File(jdkPath.toFile(), jdkId).mkdirs());

		ExecutionResult result = checkedRun(jdk -> jdk.list(false, FormatMixin.Format.text));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedOut(),
				equalTo("Installed JDKs (<=default):\n  11\n  12\n  13\n"));
	}

	@Test
	void testJdkInstallWithLinkingToExistingJdkPathWhenPathIsInvalid() {
		checkedRunWithException(jdk -> {
			try {
				jdk.install(true, 11, "/non-existent-path");
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
		initMockJdkDir(javaDir);
		final Path jdkPath = JdkManager.getJdksPath();
		jdkPath.toFile().mkdir();

		ExecutionResult result = checkedRun(jdk -> {
			try {
				return jdk.install(false, 11, javaDir.toPath().toString());
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
		initMockJdkDir(javaDir);
		final Path jdkPath = JdkManager.getJdksPath();
		Arrays	.asList("11")
				.forEach(jdkId -> new File(jdkPath.toFile(), jdkId).mkdirs());

		ExecutionResult result = checkedRun(jdk -> {
			try {
				return jdk.install(true, 11, javaDir.toPath().toString());
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
		initMockJdkDir(javaDir);

		checkedRunWithException(jdk -> {
			try {
				jdk.install(true, 13, javaDir.toPath().toString());
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
				jdk.install(true, 13, javaDir.toPath().toString());
			} catch (Exception e) {
				assertTrue(e instanceof ExitException);
				assertEquals("Unable to determine Java version in given path: " + javaDir.toPath(), e.getMessage());
			}
			return null;
		});
	}

	@Test
	void testExistingJdkUninstall() throws IOException {
		final Path jdkPath = JdkManager.getJdksPath();
		int jdkVersion = 14;
		new File(jdkPath.toFile(), String.valueOf(jdkVersion)).mkdirs();

		ExecutionResult result = checkedRun(jdk -> jdk.uninstall(jdkVersion));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] Uninstalled JDK:\n  " + jdkVersion + "\n"));
	}

	@Test
	void testNonExistingJdkUninstall() throws IOException {
		int jdkVersion = 16;

		ExecutionResult result = checkedRun(jdk -> jdk.uninstall(jdkVersion));

		assertThat(result.exitCode, equalTo(SUCCESS_EXIT));
		assertThat(result.normalizedErr(),
				equalTo("[jbang] JDK 16 is not installed\n"));
	}

	private ExecutionResult checkedRun(Function<Jdk, Integer> commandRunner) throws IOException {
		return checkedRun(commandRunner, "jdk");
	}

	private void checkedRunWithException(Function<Jdk, Integer> commandRunner) {
		try {
			checkedRun(commandRunner, "jdk");
		} catch (Exception e) {
			// Ignore
		}
	}

	private void initMockJdkDir(File javaDir) throws IOException {
		String rawJavaVersion = "JAVA_VERSION=\"11.0.14\"";
		File release = new File(javaDir, "release");
		Util.writeString(release.toPath(), rawJavaVersion);
	}
}
