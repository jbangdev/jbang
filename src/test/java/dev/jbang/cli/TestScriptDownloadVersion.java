package dev.jbang.cli;

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

/**
 * Functional tests verifying how JBANG_DOWNLOAD_VERSION is translated into a
 * GitHub release tag URL by the startup scripts.
 *
 * Numeric versions (e.g. "0.120.0") are mapped to the matching {@code vX.Y.Z}
 * release. Non-numeric values (e.g. "early-access") are used as a release tag
 * name as-is. This is what makes
 * {@code https://github.com/jbangdev/jbang/releases/download/early-access/}
 * usable via the existing {@code JBANG_DOWNLOAD_*} variables.
 */
class TestScriptDownloadVersion {

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
	// Bash
	// -------------------------------------------------------------------------

	@Nested
	class Bash {

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

				TarArchiveEntry jarEntry = new TarArchiveEntry("jbang/bin/jbang.jar");
				jarEntry.setSize(0);
				tar.putArchiveEntry(jarEntry);
				tar.closeArchiveEntry();
			}
			return baos.toByteArray();
		}

		private Map<String, String> bashEnv(String version) {
			Path jbdir = tempDir.resolve("jbdir-" + version);
			Path tdir = tempDir.resolve("cache-" + version);
			Map<String, String> env = new HashMap<>(System.getenv());
			env.put("JBANG_DIR", jbdir.toString());
			env.put("JBANG_CACHE_DIR", tdir.toString());
			env.put("JBANG_DOWNLOAD_BASEURL", wm.baseUrl());
			env.put("JBANG_DOWNLOAD_VERSION", version);
			env.put("JBANG_DOWNLOAD_RETRY", "0");
			env.put("JBANG_NO_VERSION_CHECK", "true");
			env.remove("JAVA_HOME");
			env.remove("JBANG_DOWNLOAD_URL");
			return env;
		}

		@BeforeEach
		void requireBash() {
			assumeTrue(isCommandAvailable("bash"), "bash is not available");
		}

		@Test
		void numericVersionGetsVPrefix() throws Exception {
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/v0.120.0/jbang.tar"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			List<String> cmd = new ArrayList<>();
			cmd.add("bash");
			cmd.add(BASH_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, bashEnv("0.120.0"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/v0.120.0/jbang.tar")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}

		@Test
		void earlyAccessTagIsUsedAsIs() throws Exception {
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/early-access/jbang.tar"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			List<String> cmd = new ArrayList<>();
			cmd.add("bash");
			cmd.add(BASH_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, bashEnv("early-access"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/early-access/jbang.tar")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}
	}

	// -------------------------------------------------------------------------
	// PowerShell
	// -------------------------------------------------------------------------

	@Nested
	class PowerShell {

		private String psCommand;

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

		private Map<String, String> psEnv(String version) {
			Path jbdir = tempDir.resolve("jbdir-" + version);
			Path tdir = tempDir.resolve("cache-" + version);
			Map<String, String> env = new HashMap<>(System.getenv());
			env.put("JBANG_DIR", jbdir.toString());
			env.put("JBANG_CACHE_DIR", tdir.toString());
			env.put("JBANG_DOWNLOAD_BASEURL", wm.baseUrl());
			env.put("JBANG_DOWNLOAD_VERSION", version);
			env.put("JBANG_DOWNLOAD_RETRY", "0");
			env.put("JBANG_NO_VERSION_CHECK", "true");
			env.remove("JAVA_HOME");
			env.remove("JBANG_DOWNLOAD_URL");
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
		void numericVersionGetsVPrefix() throws Exception {
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/v0.120.0/jbang.zip"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));

			List<String> cmd = new ArrayList<>();
			cmd.add(psCommand);
			cmd.add("-NoProfile");
			cmd.add("-ExecutionPolicy");
			cmd.add("Bypass");
			cmd.add("-File");
			cmd.add(PS1_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, psEnv("0.120.0"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/v0.120.0/jbang.zip")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}

		@Test
		void earlyAccessTagIsUsedAsIs() throws Exception {
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/early-access/jbang.zip"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));

			List<String> cmd = new ArrayList<>();
			cmd.add(psCommand);
			cmd.add("-NoProfile");
			cmd.add("-ExecutionPolicy");
			cmd.add("Bypass");
			cmd.add("-File");
			cmd.add(PS1_SCRIPT.toString());
			cmd.add("version");

			RunResult result = runProcess(cmd, psEnv("early-access"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/early-access/jbang.zip")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}
	}
}
