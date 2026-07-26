package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;

import dev.jbang.util.Util;

import io.qameta.allure.Description;

/**
 * Regression tests for #2621: `jbang deps search` hangs on Windows because both
 * jbang.cmd and jbang.ps1 capture the launched Java process's stdout in order
 * to support the "exit code 255" convention (used by commands like `run`, which
 * can print a generated native command for the launcher to re-exec instead of
 * running the JVM path directly).
 *
 * That capture is what breaks the interactive `deps search` TUI: with stdout no
 * longer attached to a real console, Aesh can't detect a native Windows
 * terminal and the UI never receives keystrokes after its first render.
 *
 * A real interactive terminal isn't available in headless CI, so instead of
 * testing the TUI's visual behavior, these tests verify the actual mechanism
 * that was fixed: for `deps search`, the launcher must skip the
 * capture-and-possibly-re-exec path entirely.
 *
 * We prove this with a tiny stub "jbang.jar" that mimics the "print a command
 * and exit 255" convention. If a launcher wrongly intercepts this for `deps
 * search`, it will read the printed line and execute it as a new command, and
 * the final exit code will be that command's exit code (0), not 255. If the
 * launcher correctly bypasses the capture, the real exit code (255) from the
 * stub passes straight through untouched.
 */
public class DepsSearchLauncherIT extends BaseIT {

	private static final String MARKER_COMMAND = "echo SHOULD_NOT_RUN";

	@Test
	@Description("jbang.cmd must not intercept exit code 255 / re-exec printed output for 'deps search'")
	public void cmdLauncherDoesNotInterceptDepsSearch() throws IOException {
		assumeTrue(Util.isWindows(), "jbang.cmd is only used on Windows");

		Path launcher = prepareStubLauncher("jbang.cmd");
		List<String> command = Arrays.asList("cmd", "/c", launcher.toString(), "deps", "search");

		assertLauncherDoesNotReexec(command);
	}

	@Test
	@Description("jbang.ps1 must not intercept exit code 255 / re-exec printed output for 'deps search'")
	public void ps1LauncherDoesNotInterceptDepsSearch() throws IOException {
		assumeTrue(Util.isWindows(), "jbang.ps1 is only used on Windows");

		Path launcher = prepareStubLauncher("jbang.ps1");
		List<String> command = Arrays.asList("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
				"-File", launcher.toString(), "deps", "search");

		assertLauncherDoesNotReexec(command);
	}

	/**
	 * Copies the real (already built) launcher script into its own scratch
	 * directory alongside a stub jbang.jar, and returns the path to the copied
	 * launcher.
	 */
	private Path prepareStubLauncher(String launcherName) throws IOException {
		Path stubDir = Files.createDirectories(scratch().resolve("deps-search-stub-" + launcherName));

		Path realLauncher = Paths.get("build/install/jbang/bin").resolve(launcherName).toAbsolutePath();
		Path launcher = stubDir.resolve(launcherName);
		Files.copy(realLauncher, launcher, StandardCopyOption.REPLACE_EXISTING);

		Path stubJar = buildStubJar(stubDir);
		Files.copy(stubJar, stubDir.resolve("jbang.jar"), StandardCopyOption.REPLACE_EXISTING);

		return launcher;
	}

	/**
	 * The stub always prints MARKER_COMMAND to its own stdout regardless of what
	 * the launcher does with it, so checking stdout content proves nothing. The
	 * real signal is the exit code: if the launcher wrongly intercepted this as the
	 * "print a command, exit 255" convention, it would execute MARKER_COMMAND
	 * itself and the final exit code would be that command's exit code (0 for
	 * `echo`), not the stub's real 255.
	 */
	private void assertLauncherDoesNotReexec(List<String> command) {
		CommandResult result = shell(Collections.emptyMap(), command.toArray(new String[0]));

		assertThat(result).hasExitCode(255);
	}

	/**
	 * Builds a tiny jbang.jar stand-in whose Main-Class prints
	 * {@link #MARKER_COMMAND} to stdout and exits with code 255 - exactly the
	 * convention the real jbang.jar uses when it wants the launcher to re-exec a
	 * generated native command.
	 */
	private Path buildStubJar(Path dir) throws IOException {
		Path srcFile = dir.resolve("StubMain.java");
		String source = "public class StubMain {\n" +
				"    public static void main(String[] args) {\n" +
				"        System.out.println(\"" + MARKER_COMMAND + "\");\n" +
				"        System.exit(255);\n" +
				"    }\n" +
				"}\n";
		Files.write(srcFile, source.getBytes(StandardCharsets.UTF_8));

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException(
					"No system Java compiler available - integration tests must run on a JDK, not a JRE");
		}
		int compileResult = compiler.run(null, null, null, srcFile.toString());
		if (compileResult != 0) {
			throw new IllegalStateException("Failed to compile stub jar source for " + srcFile);
		}

		Path classFile = dir.resolve("StubMain.class");
		Path jarFile = dir.resolve("stub.jar");

		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "StubMain");

		try (OutputStream fos = Files.newOutputStream(jarFile);
				JarOutputStream jos = new JarOutputStream(fos, manifest)) {
			jos.putNextEntry(new JarEntry("StubMain.class"));
			jos.write(Files.readAllBytes(classFile));
			jos.closeEntry();
		}

		return jarFile;
	}
}