package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The bash launcher normally leaves jbang's output attached to the terminal, so
 * jbang detects colors itself. When it can't create a temporary file it falls
 * back to capturing jbang's output, and then must only force colors
 * (CLICOLOR_FORCE) when the launcher's own stdout is a terminal, so
 * piped/redirected output stays free of ANSI codes.
 */
@DisabledOnOs(OS.WINDOWS)
class TestScriptColor extends AbstractScriptTest {

	private Path launcher;

	@BeforeEach
	void setup() throws IOException {
		requireBash();
		Path binDir = Files.createDirectories(tempDir.resolve("bin"));
		launcher = binDir.resolve("jbang");
		Files.copy(BASH_SCRIPT, launcher);
		Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwxr-xr-x"));
		createProbeJar(binDir.resolve("jbang.jar"));
	}

	@Test
	void doesNotForceColorWhenPiped() throws Exception {
		Map<String, String> env = env();
		env.remove("CLICOLOR_FORCE");
		RunResult result = runProcess(List.of(launcher.toString()), env);
		assertEquals(0, result.exitCode, result.stderr);
		assertEquals("CLICOLOR_FORCE=null console=false", result.stdout.trim());
	}

	@Test
	void doesNotForceColorWhenPipedWithoutTempFile() throws Exception {
		Map<String, String> env = env();
		env.remove("CLICOLOR_FORCE");
		env.put("TMPDIR", tempDir.resolve("missing").toString());
		RunResult result = runProcess(List.of(launcher.toString()), env);
		assertEquals(0, result.exitCode, result.stderr);
		assertEquals("CLICOLOR_FORCE=null console=false", result.stdout.trim());
	}

	@Test
	void keepsUserSettingWhenPiped() throws Exception {
		Map<String, String> env = env();
		env.put("CLICOLOR_FORCE", "1");
		RunResult result = runProcess(List.of(launcher.toString()), env);
		assertEquals(0, result.exitCode, result.stderr);
		assertEquals("CLICOLOR_FORCE=1 console=false", result.stdout.trim());
	}

	@Test
	void leavesTerminalToJbang() throws Exception {
		assumeTrue(isScriptAvailable(), "script(1) is not available");
		Map<String, String> env = env();
		env.remove("CLICOLOR_FORCE");
		RunResult result = runProcess(onTerminal(), env);
		assertEquals(0, result.exitCode, result.stderr);
		assertEquals("CLICOLOR_FORCE=null console=true", terminalOutput(result));
	}

	@ParameterizedTest
	@ValueSource(strings = { "_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS" })
	void forcesColorOnTerminalWithoutTempFile(String javaOptionsVariable) throws Exception {
		assumeTrue(isScriptAvailable(), "script(1) is not available");
		EnvironmentVariables variables = new EnvironmentVariables();
		String originalValue = System.getenv(javaOptionsVariable);
		variables.set(javaOptionsVariable, "-Djbang.color.probe=true");
		try {
			Map<String, String> env = env();
			env.remove("CLICOLOR_FORCE");
			env.put("TMPDIR", tempDir.resolve("missing").toString());
			RunResult result = runProcess(onTerminal(), env);
			assertEquals(0, result.exitCode, result.stderr);
			assertEquals("CLICOLOR_FORCE=1 console=false", terminalOutput(result));
		} finally {
			variables.set(javaOptionsVariable, originalValue);
		}
	}

	/** Runs the launcher with a pseudo terminal as stdin/stdout via script(1). */
	private List<String> onTerminal() {
		List<String> cmd = new ArrayList<>();
		cmd.add("script");
		if (OS.MAC.isCurrentOs()) {
			cmd.addAll(List.of("-q", "/dev/null", launcher.toString()));
		} else {
			cmd.addAll(List.of("-qec", launcher.toString(), "/dev/null"));
		}
		return cmd;
	}

	private static String terminalOutput(RunResult result) {
		return result.stdout.replace("\r", "").replace("^D", "").trim();
	}

	private Map<String, String> env() {
		Map<String, String> env = baseBashEnv("color");
		env.put("JAVA_HOME", System.getProperty("java.home"));
		// JVM startup notices appear in stdout when the launcher runs under script(1).
		env.remove("_JAVA_OPTIONS");
		env.remove("JAVA_TOOL_OPTIONS");
		env.remove("JDK_JAVA_OPTIONS");
		return env;
	}

	private static boolean isScriptAvailable() {
		try {
			Process p = new ProcessBuilder("sh", "-c", "command -v script").start();
			return p.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private static void createProbeJar(Path jar) throws IOException {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, ColorEnvProbe.class.getName());
		String classResource = ColorEnvProbe.class.getName().replace('.', '/') + ".class";
		try (InputStream input = ColorEnvProbe.class.getClassLoader().getResourceAsStream(classResource);
				JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
			if (input == null) {
				throw new IOException("Could not find test probe class: " + classResource);
			}
			output.putNextEntry(new JarEntry(classResource));
			input.transferTo(output);
			output.closeEntry();
		}
	}

	/**
	 * Stands in for jbang: prints the CLICOLOR_FORCE value it was started with and
	 * whether it is attached to a terminal.
	 */
	public static class ColorEnvProbe {
		public static void main(String[] args) {
			System.out.println("CLICOLOR_FORCE=" + System.getenv("CLICOLOR_FORCE")
					+ " console=" + (System.console() != null));
		}
	}
}
