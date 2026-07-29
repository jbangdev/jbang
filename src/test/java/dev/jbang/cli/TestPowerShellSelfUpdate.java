package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class TestPowerShellSelfUpdate extends AbstractScriptTest {

	@BeforeEach
	void checkPowerShell() {
		requirePowerShell();
	}

	@Test
	void replacesRunningPowerShellLauncher() throws Exception {
		Path binDir = Files.createDirectories(tempDir.resolve("bin"));
		Path launcher = binDir.resolve("jbang.ps1");
		Files.copy(PS1_SCRIPT, launcher);
		createUpdaterJar(binDir.resolve("jbang.jar"));

		Map<String, String> env = new HashMap<>(System.getenv());
		env.put("JAVA_HOME", System.getProperty("java.home"));
		env.put("JBANG_DIR", tempDir.resolve("jbang-home").toString());
		env.put("JBANG_CACHE_DIR", tempDir.resolve("cache").toString());
		env.put("JBANG_NO_VERSION_CHECK", "true");

		List<String> command = new ArrayList<>();
		command.add(psCommand);
		command.add("-NoProfile");
		command.add("-ExecutionPolicy");
		command.add("Bypass");
		command.add("-File");
		command.add(launcher.toString());
		RunResult result = runProcess(command, env);

		assertEquals(0, result.exitCode, result.stderr);
		assertEquals("# updated while running\n", Files.readString(launcher).replace("\r\n", "\n"));
		assertFalse(Files.exists(launcher.resolveSibling("jbang.ps1.new")));
	}

	private static void createUpdaterJar(Path jar) throws IOException {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, PowerShellUpdater.class.getName());
		String classResource = PowerShellUpdater.class.getName().replace('.', '/') + ".class";
		try (InputStream input = PowerShellUpdater.class.getClassLoader().getResourceAsStream(classResource);
				JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
			if (input == null) {
				throw new IOException("Could not find test updater class: " + classResource);
			}
			output.putNextEntry(new JarEntry(classResource));
			input.transferTo(output);
			output.closeEntry();
		}
	}

	public static class PowerShellUpdater {
		public static void main(String[] args) throws IOException {
			Path launcher = Paths.get(System.getenv("JBANG_LAUNCH_CMD"));
			Files.write(launcher, "# updated while running\n".getBytes(StandardCharsets.UTF_8));
		}
	}
}
