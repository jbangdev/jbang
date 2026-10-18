package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class TestScriptNativeLookup extends AbstractScriptTest {

	private static final Path CMD_SCRIPT = Paths.get("src/main/scripts/jbang.cmd").toAbsolutePath();

	@Test
	void bashFindsNativeBinaryInDotJBang() throws Exception {
		requireBash();
		Path wrapperDir = Files.createDirectory(tempDir.resolve("bash-wrapper"));
		Path wrapper = Files.copy(BASH_SCRIPT, wrapperDir.resolve("jbang"), StandardCopyOption.COPY_ATTRIBUTES);
		Path dotJBang = Files.createDirectory(wrapperDir.resolve(".jbang"));
		for (String binaryName : Arrays.asList("jbang.bin", "jbang.bin.exe")) {
			Path binary = dotJBang.resolve(binaryName);
			Files.write(binary, "#!/bin/sh\nprintf nested-native\n".getBytes(StandardCharsets.UTF_8));
			assertTrue(binary.toFile().setExecutable(true) || Files.isExecutable(binary));
		}

		String command = "JBANG_USE_NATIVE=true JBANG_DIR=.jbang-home JBANG_CACHE_DIR=.jbang-cache "
				+ "JBANG_DOWNLOAD_RETRY=0 JBANG_DOWNLOAD_URL=http://127.0.0.1:1/jbang.tar ./jbang";
		RunResult result = runProcess(Arrays.asList("bash", "-c", command), Collections.emptyMap(), wrapperDir);

		assertTrue(result.stdout.contains("nested-native"), "stdout: " + result.stdout + ", stderr: " + result.stderr);
	}

	@Test
	void bashLocalJarTakesPrecedenceOverGlobalNativeBinary() throws Exception {
		requireBash();
		Path wrapperDir = Files.createDirectory(tempDir.resolve("bash-wrapper-with-jar"));
		Files.copy(BASH_SCRIPT, wrapperDir.resolve("jbang"), StandardCopyOption.COPY_ATTRIBUTES);
		Path dotJBang = Files.createDirectory(wrapperDir.resolve(".jbang"));
		Files.write(dotJBang.resolve("jbang.jar"), new byte[0]);
		Path globalBin = Files.createDirectories(wrapperDir.resolve(".jbang-home/bin"));
		Path globalBinary = globalBin.resolve("jbang.bin");
		Files.write(globalBinary, "#!/bin/sh\nprintf global-native\n".getBytes(StandardCharsets.UTF_8));
		assertTrue(globalBinary.toFile().setExecutable(true) || Files.isExecutable(globalBinary));

		String command = "JBANG_USE_NATIVE=true JBANG_DIR=.jbang-home JBANG_CACHE_DIR=.jbang-cache "
				+ "JBANG_DOWNLOAD_RETRY=0 JBANG_DOWNLOAD_URL=http://127.0.0.1:1/jbang.tar ./jbang";
		RunResult result = runProcess(Arrays.asList("bash", "-c", command), Collections.emptyMap(), wrapperDir);

		assertTrue(result.exitCode != 0, "the empty local JAR should fail");
		assertTrue(!result.stdout.contains("global-native"),
				"global native binary should not replace the local JAR, stdout: " + result.stdout);
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void powershellFindsNativeBinaryInDotJBang() throws Exception {
		requirePowerShell();
		Path wrapperDir = Files.createDirectory(tempDir.resolve("powershell-wrapper"));
		Path wrapper = Files.copy(PS1_SCRIPT, wrapperDir.resolve("jbang.ps1"), StandardCopyOption.COPY_ATTRIBUTES);
		Path dotJBang = Files.createDirectory(wrapperDir.resolve(".jbang"));
		Files.copy(Paths.get(System.getenv("ComSpec")), dotJBang.resolve("jbang.bin.exe"));

		Map<String, String> env = basePsEnv("native-lookup");
		env.put("JBANG_USE_NATIVE", "true");
		RunResult result = runProcess(
				Arrays.asList(psCommand, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", wrapper.toString(),
						"/d", "/c", "echo", "nested-native"),
				env);

		assertTrue(result.stdout.contains("nested-native"), "stdout: " + result.stdout + ", stderr: " + result.stderr);
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void cmdFindsNativeBinaryInDotJBang() throws Exception {
		Path wrapperDir = Files.createDirectory(tempDir.resolve("cmd-wrapper"));
		Path wrapper = Files.copy(CMD_SCRIPT, wrapperDir.resolve("jbang.cmd"), StandardCopyOption.COPY_ATTRIBUTES);
		Path dotJBang = Files.createDirectory(wrapperDir.resolve(".jbang"));
		Files.copy(Paths.get(System.getenv("ComSpec")), dotJBang.resolve("jbang.bin.exe"));

		Map<String, String> env = basePsEnv("native-lookup-cmd");
		env.put("JBANG_USE_NATIVE", "true");
		RunResult result = runProcess(
				Arrays.asList(System.getenv("ComSpec"), "/d", "/c", wrapper.toString(), "/d", "/c", "echo",
						"nested-native"),
				env);

		assertTrue(result.stdout.contains("nested-native"), "stdout: " + result.stdout + ", stderr: " + result.stderr);
	}
}
