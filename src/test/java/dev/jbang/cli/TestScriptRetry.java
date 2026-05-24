package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

/**
 * Functional tests for download retry support in JBang startup scripts. Runs
 * the real jbang/jbang.ps1 scripts against a WireMock server that simulates
 * transient download failures, using JBANG_DOWNLOAD_URL to redirect downloads
 * to WireMock.
 *
 * See https://github.com/jbangdev/jbang/issues/2459
 */
class TestScriptRetry {

	private static final Path BASH_SCRIPT = Paths.get("src/main/scripts/jbang").toAbsolutePath();
	private static final Path PS1_SCRIPT = Paths.get("src/main/scripts/jbang.ps1").toAbsolutePath();

	private WireMockServer wm;

	@TempDir
	Path tempDir;

	@BeforeEach
	void startWireMock() {
		wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		wm.start();
	}

	@AfterEach
	void stopWireMock() {
		if (wm != null) {
			wm.stop();
		}
	}

	/**
	 * Configures WireMock to fail {@code failCount} times with a 500 error, then
	 * return 200 with the given body.
	 */
	private void stubFlakyEndpoint(String path, int failCount, byte[] body) {
		String scenarioName = "flaky";
		for (int i = 0; i < failCount; i++) {
			String currentState = (i == 0) ? Scenario.STARTED : "attempt-" + i;
			String nextState = "attempt-" + (i + 1);
			wm.stubFor(WireMock.get(WireMock.urlEqualTo(path))
				.inScenario(scenarioName)
				.whenScenarioStateIs(currentState)
				.willReturn(WireMock.aResponse().withStatus(500).withBody("Server Error"))
				.willSetStateTo(nextState));
		}
		String finalState = failCount == 0 ? Scenario.STARTED : "attempt-" + failCount;
		wm.stubFor(WireMock.get(WireMock.urlEqualTo(path))
			.inScenario(scenarioName)
			.whenScenarioStateIs(finalState)
			.willReturn(WireMock.aResponse().withStatus(200).withBody(body)));
	}

	private static boolean isCommandAvailable(String command) {
		try {
			Process p = new ProcessBuilder(command, "--version")
				.redirectErrorStream(true)
				.start();
			p.getInputStream().transferTo(new ByteArrayOutputStream());
			return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private static RunResult runProcess(List<String> cmd, Map<String, String> env) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.environment().putAll(env);
		pb.redirectErrorStream(false);
		Process process = pb.start();

		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();
		Thread t1 = new Thread(() -> {
			try {
				process.getInputStream().transferTo(stdout);
			} catch (Exception e) {
				/* ignore */ }
		});
		Thread t2 = new Thread(() -> {
			try {
				process.getErrorStream().transferTo(stderr);
			} catch (Exception e) {
				/* ignore */ }
		});
		t1.start();
		t2.start();

		boolean finished = process.waitFor(120, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
		}
		t1.join(5000);
		t2.join(5000);
		assertTrue(finished, "script timed out");
		return new RunResult(process.exitValue(),
				stdout.toString(StandardCharsets.UTF_8),
				stderr.toString(StandardCharsets.UTF_8));
	}

	static class RunResult {
		final int exitCode;
		final String stdout;
		final String stderr;

		RunResult(int exitCode, String stdout, String stderr) {
			this.exitCode = exitCode;
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}

	// -------------------------------------------------------------------------
	// Bash tests — runs src/main/scripts/jbang with JBANG_DOWNLOAD_URL
	// pointing at WireMock
	// -------------------------------------------------------------------------

	@Nested
	class BashDownloadRetry {

		/**
		 * Creates a minimal jbang.tar containing jbang/bin/jbang (a dummy script that
		 * just exits 0) so the real jbang script can download, extract, and "run" it
		 * successfully.
		 */
		private byte[] createJbangTar() throws Exception {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
				byte[] script = "#!/bin/bash\nexit 0\n".getBytes(StandardCharsets.UTF_8);
				TarArchiveEntry entry = new TarArchiveEntry("jbang/bin/jbang");
				entry.setSize(script.length);
				entry.setMode(0755);
				tar.putArchiveEntry(entry);
				tar.write(script);
				tar.closeArchiveEntry();

				// dummy jbang.jar (the script checks for its existence)
				byte[] jar = new byte[0];
				TarArchiveEntry jarEntry = new TarArchiveEntry("jbang/bin/jbang.jar");
				jarEntry.setSize(jar.length);
				tar.putArchiveEntry(jarEntry);
				tar.write(jar);
				tar.closeArchiveEntry();
			}
			return baos.toByteArray();
		}

		private Map<String, String> bashEnv(int retryCount) {
			Path jbdir = tempDir.resolve("jbdir");
			Path tdir = tempDir.resolve("cache");
			Map<String, String> env = new HashMap<>(System.getenv());
			env.put("JBANG_DIR", jbdir.toString());
			env.put("JBANG_CACHE_DIR", tdir.toString());
			env.put("JBANG_DOWNLOAD_URL", wm.url("/jbang.tar"));
			env.put("JBANG_DOWNLOAD_RETRY", String.valueOf(retryCount));
			env.put("JBANG_DOWNLOAD_RETRY_DELAY", "0");
			env.put("JBANG_NO_VERSION_CHECK", "true");
			// Remove JAVA_HOME to avoid interference
			env.remove("JAVA_HOME");
			return env;
		}

		@BeforeEach
		void requireBash() {
			assumeTrue(isCommandAvailable("bash"), "bash is not available");
		}

		@Test
		void downloadSucceedsAfterTransientFailures() throws Exception {
			byte[] tar = createJbangTar();
			stubFlakyEndpoint("/jbang.tar", 3, tar);

			List<String> cmd = new ArrayList<>();
			cmd.add("bash");
			cmd.add(BASH_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, bashEnv(5));

			// Download should have succeeded (no download error in stderr)
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded after retries, stderr: " + result.stderr);
		}

		@Test
		void downloadFailsWhenRetriesExhausted() throws Exception {
			byte[] tar = createJbangTar();
			stubFlakyEndpoint("/jbang.tar", 10, tar);

			List<String> cmd = new ArrayList<>();
			cmd.add("bash");
			cmd.add(BASH_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, bashEnv(2));

			assertNotEquals(0, result.exitCode, "script should have failed");
			assertTrue(result.stderr.contains("Error downloading JBang"),
					"stderr should mention download error, was: " + result.stderr);
		}

		@Test
		void downloadFailsWithZeroRetries() throws Exception {
			byte[] tar = createJbangTar();
			stubFlakyEndpoint("/jbang.tar", 1, tar);

			List<String> cmd = new ArrayList<>();
			cmd.add("bash");
			cmd.add(BASH_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, bashEnv(0));

			assertNotEquals(0, result.exitCode, "script should have failed");
			assertTrue(result.stderr.contains("Error downloading JBang"),
					"stderr should mention download error, was: " + result.stderr);
		}
	}

	// -------------------------------------------------------------------------
	// PowerShell tests — runs src/main/scripts/jbang.ps1 with
	// JBANG_DOWNLOAD_URL pointing at WireMock
	// -------------------------------------------------------------------------

	@Nested
	class PowerShellDownloadRetry {

		private String psCommand;

		/**
		 * Creates a minimal jbang.zip containing jbang/bin/jbang.ps1 (a dummy script
		 * that just exits 0) so the real jbang.ps1 script can download, extract, and
		 * "run" it successfully.
		 */
		private byte[] createJbangZip() throws Exception {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(baos)) {
				zip.putNextEntry(new ZipEntry("jbang/bin/jbang.ps1"));
				zip.write("exit 0\n".getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();

				zip.putNextEntry(new ZipEntry("jbang/bin/jbang.jar"));
				zip.closeEntry();

				zip.putNextEntry(new ZipEntry("jbang/bin/jbang.cmd"));
				zip.write("@exit /b 0\r\n".getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
			return baos.toByteArray();
		}

		private Map<String, String> psEnv(int retryCount) {
			Path jbdir = tempDir.resolve("jbdir");
			Path tdir = tempDir.resolve("cache");
			Map<String, String> env = new HashMap<>(System.getenv());
			env.put("JBANG_DIR", jbdir.toString());
			env.put("JBANG_CACHE_DIR", tdir.toString());
			env.put("JBANG_DOWNLOAD_URL", wm.url("/jbang.zip"));
			env.put("JBANG_DOWNLOAD_RETRY", String.valueOf(retryCount));
			env.put("JBANG_DOWNLOAD_RETRY_DELAY", "0");
			env.put("JBANG_NO_VERSION_CHECK", "true");
			env.remove("JAVA_HOME");
			return env;
		}

		@BeforeEach
		void requirePowerShell() {
			if (isCommandAvailable("pwsh")) {
				psCommand = "pwsh";
			} else if (isCommandAvailable("powershell")) {
				psCommand = "powershell";
			} else {
				assumeTrue(false, "PowerShell is not available (neither pwsh nor powershell found)");
			}
		}

		@Test
		void downloadSucceedsAfterTransientFailures() throws Exception {
			byte[] zip = createJbangZip();
			stubFlakyEndpoint("/jbang.zip", 3, zip);

			List<String> cmd = new ArrayList<>();
			cmd.add(psCommand);
			cmd.add("-NoProfile");
			cmd.add("-ExecutionPolicy");
			cmd.add("Bypass");
			cmd.add("-File");
			cmd.add(PS1_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, psEnv(5));

			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded after retries, stderr: " + result.stderr);
		}

		@Test
		void downloadFailsWhenRetriesExhausted() throws Exception {
			byte[] zip = createJbangZip();
			stubFlakyEndpoint("/jbang.zip", 10, zip);

			List<String> cmd = new ArrayList<>();
			cmd.add(psCommand);
			cmd.add("-NoProfile");
			cmd.add("-ExecutionPolicy");
			cmd.add("Bypass");
			cmd.add("-File");
			cmd.add(PS1_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, psEnv(2));

			assertNotEquals(0, result.exitCode, "script should have failed");
			assertTrue(result.stderr.contains("Error downloading JBang"),
					"stderr should mention download error, was: " + result.stderr);
		}

		@Test
		void downloadFailsWithZeroRetries() throws Exception {
			byte[] zip = createJbangZip();
			stubFlakyEndpoint("/jbang.zip", 1, zip);

			List<String> cmd = new ArrayList<>();
			cmd.add(psCommand);
			cmd.add("-NoProfile");
			cmd.add("-ExecutionPolicy");
			cmd.add("Bypass");
			cmd.add("-File");
			cmd.add(PS1_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, psEnv(0));

			assertNotEquals(0, result.exitCode, "script should have failed");
			assertTrue(result.stderr.contains("Error downloading JBang"),
					"stderr should mention download error, was: " + result.stderr);
		}
	}
}
