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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The bash launcher captures jbang's output, so jbang itself never sees a
 * terminal. It must only force colors (CLICOLOR_FORCE) when the launcher's own
 * stdout is a terminal, so piped/redirected output stays free of ANSI codes.
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
		assertEquals("CLICOLOR_FORCE=null", result.stdout.trim());
	}

	@Test
	void keepsUserSettingWhenPiped() throws Exception {
		Map<String, String> env = env();
		env.put("CLICOLOR_FORCE", "1");
		RunResult result = runProcess(List.of(launcher.toString()), env);
		assertEquals(0, result.exitCode, result.stderr);
		assertEquals("CLICOLOR_FORCE=1", result.stdout.trim());
	}

	@Test
	void forcesColorOnTerminal() throws Exception {
		// run the launcher with a pseudo terminal as stdout via script(1)
		List<String> cmd = new ArrayList<>();
		cmd.add("script");
		if (OS.MAC.isCurrentOs()) {
			cmd.addAll(List.of("-q", "/dev/null", launcher.toString()));
		} else {
			cmd.addAll(List.of("-qec", launcher.toString(), "/dev/null"));
		}
		assumeTrue(isScriptAvailable(), "script(1) is not available");
		Map<String, String> env = env();
		env.remove("CLICOLOR_FORCE");
		RunResult result = runProcess(cmd, env);
		assertEquals(0, result.exitCode, result.stderr);
		assertEquals("CLICOLOR_FORCE=1", result.stdout.replace("\r", "").replace("^D", "").trim());
	}

	private Map<String, String> env() {
		Map<String, String> env = baseBashEnv("color");
		env.put("JAVA_HOME", System.getProperty("java.home"));
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

	/** Stands in for jbang: prints the CLICOLOR_FORCE value it was started with. */
	public static class ColorEnvProbe {
		public static void main(String[] args) {
			System.out.println("CLICOLOR_FORCE=" + System.getenv("CLICOLOR_FORCE"));
		}
	}
}
