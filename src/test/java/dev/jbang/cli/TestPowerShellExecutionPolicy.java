package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Windows PowerShell started (indirectly) from PowerShell 7, e.g. by an IDE or
 * a build tool launched from a pwsh terminal, inherits PowerShell 7's
 * PSModulePath and can't autoload Get-ExecutionPolicy. See #2766.
 */
@EnabledOnOs(OS.WINDOWS)
class TestPowerShellExecutionPolicy extends AbstractScriptTest {

	@Test
	void runsWithPowerShell7ModulePath() throws Exception {
		assumeTrue(isCommandAvailable("pwsh"), "pwsh is not available");
		// Windows PowerShell has no --version, so look for it where it's always
		// installed
		Path powershell = Paths.get(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0",
				"powershell.exe");
		assumeTrue(Files.isRegularFile(powershell), "powershell is not available");

		// the PSModulePath pwsh hands to the processes it starts
		RunResult pwsh = runProcess(Arrays.asList("pwsh", "-NoProfile", "-Command", "$env:PSModulePath"),
				new HashMap<>(System.getenv()));
		assertEquals(0, pwsh.exitCode, pwsh.stderr);

		Path binDir = Files.createDirectories(tempDir.resolve("bin"));
		Path launcher = binDir.resolve("jbang.ps1");
		Files.copy(PS1_SCRIPT, launcher);
		createProbeJar(binDir.resolve("jbang.jar"));

		Map<String, String> env = new HashMap<>(System.getenv());
		env.put("PSModulePath", pwsh.stdout.trim());
		env.put("JAVA_HOME", System.getProperty("java.home"));
		env.put("JBANG_DIR", tempDir.resolve("jbang-home").toString());
		env.put("JBANG_CACHE_DIR", tempDir.resolve("cache").toString());
		env.put("JBANG_NO_VERSION_CHECK", "true");

		RunResult result = runProcess(
				Arrays.asList(powershell.toString(), "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
						launcher.toString()),
				env);

		assertEquals(0, result.exitCode, result.stderr);
		assertEquals("probe ran", result.stdout.trim(), result.stderr);
	}

	private static void createProbeJar(Path jar) throws IOException {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Probe.class.getName());
		String classResource = Probe.class.getName().replace('.', '/') + ".class";
		try (InputStream input = Probe.class.getClassLoader().getResourceAsStream(classResource);
				JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
			if (input == null) {
				throw new IOException("Could not find test probe class: " + classResource);
			}
			output.putNextEntry(new JarEntry(classResource));
			input.transferTo(output);
			output.closeEntry();
		}
	}

	/** Stands in for jbang. */
	public static class Probe {
		public static void main(String[] args) {
			System.out.println("probe ran");
		}
	}
}
