package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Functional tests for SHA-256 checksum verification in JBang startup scripts.
 *
 * Covers: archive download checksums (sidecar and explicit override),
 * JBANG_JAR_CHECKSUM for jar/binary verification, hash tool failure, empty
 * sidecar handling, and case normalization.
 */
class TestScriptChecksum extends AbstractScriptTest {

	private static String sha256Hex(byte[] data) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
		StringBuilder sb = new StringBuilder(64);
		for (byte b : hash) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	/**
	 * Builds a PATH for bash script tests with only the commands the jbang bash
	 * script needs, minus any excluded ones. PowerShell tests don't need this —
	 * pwsh uses built-in cmdlets (Get-FileHash, Expand-Archive, etc.).
	 *
	 * Symlinks (required - excluded) from system PATH dirs into a temp dir.
	 */
	private static final java.util.Set<String> BASH_SCRIPT_COMMANDS = java.util.Set.of(
			"bash", "cat", "chmod", "cp", "curl", "cut", "dirname",
			"echo", "env", "grep", "head", "ls", "mkdir", "mktemp",
			"mv", "printf", "readlink", "rm", "sed", "sha256sum", "shasum",
			"sleep", "sort", "tar", "test", "touch", "tr", "uname",
			"wc", "wget", "basename", "expr", "id", "tail", "tee", "xargs");

	private String buildBashPath(Path binDir, String... excludes) throws Exception {
		java.util.Set<String> excluded = java.util.Set.of(excludes);
		java.util.Set<String> wanted = new java.util.HashSet<>(BASH_SCRIPT_COMMANDS);
		wanted.removeAll(excluded);
		for (String dir : System.getenv("PATH").split(java.io.File.pathSeparator)) {
			Path dirPath = Path.of(dir);
			if (!Files.isDirectory(dirPath)) {
				continue;
			}
			for (String cmd : wanted) {
				Path src = dirPath.resolve(cmd);
				Path link = binDir.resolve(cmd);
				if (Files.exists(src) && !Files.exists(link)) {
					Files.createSymbolicLink(link, src);
				}
			}
		}
		return binDir.toString();
	}

	/**
	 * Pre-populates JBDIR/bin with a dummy jbang script + jar so the download path
	 * is skipped and the jar checksum path runs.
	 *
	 * @return the content of the dummy jbang.jar (for computing its checksum)
	 */
	private byte[] prePopulateJbdir(Path jbdir, boolean bash) throws Exception {
		Path binDir = jbdir.resolve("bin");
		Files.createDirectories(binDir);
		byte[] jarContent = "fake-jar-content".getBytes();
		Files.write(binDir.resolve("jbang.jar"), jarContent);
		if (bash) {
			Path script = binDir.resolve("jbang");
			Files.writeString(script, "#!/bin/bash\nexit 0\n");
			script.toFile().setExecutable(true);
		} else {
			Files.writeString(binDir.resolve("jbang.ps1"), "exit 0\n");
			Files.writeString(binDir.resolve("jbang.cmd"), "@exit /b 0\r\n");
		}
		return jarContent;
	}

	// -------------------------------------------------------------------------
	// Bash
	// -------------------------------------------------------------------------

	@Nested
	class Bash {

		@BeforeEach
		void checkBash() {
			requireBash();
		}

		private Map<String, String> bashEnv(String suffix) {
			Map<String, String> env = baseBashEnv(suffix);
			env.put("JBANG_DOWNLOAD_URL", wm.url("/jbang.tar"));
			env.put("JBANG_DOWNLOAD_RETRY", "0");
			return env;
		}

		@Test
		void correctSidecarChecksumPasses() throws Exception {
			byte[] tar = createJbangTar();
			String checksum = sha256Hex(tar);
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));
			wm.stubFor(WireMock.get("/jbang.tar.sha256")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(checksum)));

			RunResult r = runProcess(bashCmd("version"), bashEnv("sidecar-ok"));

			assertEquals(0, r.exitCode, "should pass with correct sidecar checksum, stderr: " + r.stderr);
		}

		@Test
		void mismatchedSidecarChecksumFails() throws Exception {
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));
			wm.stubFor(WireMock.get("/jbang.tar.sha256")
				.willReturn(WireMock.aResponse()
					.withStatus(200)
					.withBody("0000000000000000000000000000000000000000000000000000000000000000")));

			RunResult r = runProcess(bashCmd("version"), bashEnv("sidecar-bad"));

			assertNotEquals(0, r.exitCode, "should fail on checksum mismatch");
			assertTrue(r.stderr.contains("checksum mismatch"), "stderr should say mismatch: " + r.stderr);
		}

		@Test
		void explicitChecksumOverridePasses() throws Exception {
			byte[] tar = createJbangTar();
			String checksum = sha256Hex(tar);
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));
			// No sidecar stub — should not be fetched

			Map<String, String> env = bashEnv("override-ok");
			env.put("JBANG_DOWNLOAD_CHECKSUM", checksum);

			RunResult r = runProcess(bashCmd("version"), env);

			assertEquals(0, r.exitCode, "should pass with correct explicit checksum, stderr: " + r.stderr);
			// Verify sidecar was NOT requested
			wm.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/jbang.tar.sha256")));
		}

		@Test
		void explicitChecksumOverrideMismatchFails() throws Exception {
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			Map<String, String> env = bashEnv("override-bad");
			env.put("JBANG_DOWNLOAD_CHECKSUM", "badhash");

			RunResult r = runProcess(bashCmd("version"), env);

			assertNotEquals(0, r.exitCode, "should fail on explicit checksum mismatch");
			assertTrue(r.stderr.contains("checksum mismatch"), "stderr should say mismatch: " + r.stderr);
		}

		@Test
		void uppercaseChecksumMatchesLowercase() throws Exception {
			byte[] tar = createJbangTar();
			String checksum = sha256Hex(tar).toUpperCase();
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			Map<String, String> env = bashEnv("upper");
			env.put("JBANG_DOWNLOAD_CHECKSUM", checksum);

			RunResult r = runProcess(bashCmd("version"), env);

			assertEquals(0, r.exitCode, "uppercase checksum should match, stderr: " + r.stderr);
		}

		@Test
		void emptySidecarChecksumShouldWarnNotSilentlyContinue() throws Exception {
			// Security concern: empty .sha256 file should not silently skip verification
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));
			wm.stubFor(WireMock.get("/jbang.tar.sha256")
				.willReturn(WireMock.aResponse().withStatus(200).withBody("")));

			RunResult r = runProcess(bashCmd("version"), bashEnv("empty-sidecar"));

			assertEquals(0, r.exitCode, "should succeed with empty sidecar, stderr: " + r.stderr);
			assertTrue(r.stderr.contains("Warning") || r.stderr.contains("skipping"),
					"should warn about empty sidecar checksum, stderr: " + r.stderr);
		}

		@Test
		void missingSidecarChecksumWarnsAndContinues() throws Exception {
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));
			wm.stubFor(WireMock.get("/jbang.tar.sha256")
				.willReturn(WireMock.aResponse().withStatus(404)));

			RunResult r = runProcess(bashCmd("version"), bashEnv("no-sidecar"));

			// Should warn about missing sidecar and proceed past checksum step.
			// (exit code may be non-zero due to dummy archive extraction, but
			// the important thing is it does NOT fail on checksum mismatch)
			assertTrue(r.stderr.contains("Warning") || r.stderr.contains("skipping"),
					"should warn about missing sidecar, stderr: " + r.stderr);
			assertTrue(!r.stderr.contains("checksum mismatch"),
					"should not fail on checksum, stderr: " + r.stderr);
		}

		@Test
		void uppercaseSidecarChecksumNormalized() throws Exception {
			// Security concern: sidecar checksum should be normalized before comparison
			byte[] tar = createJbangTar();
			String checksum = sha256Hex(tar).toUpperCase();
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));
			wm.stubFor(WireMock.get("/jbang.tar.sha256")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(checksum)));

			RunResult r = runProcess(bashCmd("version"), bashEnv("upper-sidecar"));

			assertEquals(0, r.exitCode,
					"uppercase sidecar checksum should match after normalization, stderr: " + r.stderr);
		}

		// -- Hash tool failure (CodeRabbit #1) ---------------------------------

		@Test
		void hashToolUnavailableWithExplicitChecksumShouldFail() throws Exception {
			// Security concern: when JBANG_DOWNLOAD_CHECKSUM is set but no hash
			// tool exists, the script must fail — not silently skip verification.
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			Map<String, String> env = bashEnv("no-hash-tool");
			env.put("JBANG_DOWNLOAD_CHECKSUM", sha256Hex(tar));
			// Shadow PATH so sha256sum and shasum are not found
			Path emptyBin = tempSubDir("empty-bin");
			Files.createDirectories(emptyBin);
			// Provide only essential commands: need bash builtins + curl/wget
			// plus tar, mkdir, cp, rm — but NOT sha256sum/shasum
			env.put("PATH", buildBashPath(emptyBin, "sha256sum", "shasum"));

			RunResult r = runProcess(bashCmd("version"), env);

			assertNotEquals(0, r.exitCode,
					"should fail when hash tools unavailable with explicit checksum");
			assertTrue(
					r.stderr.contains("could not compute SHA-256")
							|| r.stderr.contains("JBANG_DOWNLOAD_CHECKSUM"),
					"stderr should mention hash failure, was: " + r.stderr);
		}

		@Test
		void hashToolUnavailableWithoutChecksumShouldWarn() throws Exception {
			// When no explicit checksum and no hash tool: warn and continue
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get("/jbang.tar")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			Map<String, String> env = bashEnv("no-hash-warn");
			Path emptyBin = tempSubDir("empty-bin-warn");
			Files.createDirectories(emptyBin);
			env.put("PATH", buildBashPath(emptyBin, "sha256sum", "shasum"));

			RunResult r = runProcess(bashCmd("version"), env);

			// Should warn but NOT fail on checksum
			assertTrue(
					r.stderr.contains("Warning") || r.stderr.contains("could not compute"),
					"should warn about missing hash tool, stderr: " + r.stderr);
			assertTrue(!r.stderr.contains("checksum mismatch"),
					"should not fail on checksum, stderr: " + r.stderr);
		}

		// -- JBANG_JAR_CHECKSUM / JBANG_BIN_CHECKSUM -------------------------

		@Test
		void jarChecksumMatchPasses() throws Exception {
			Path jbdir = tempSubDir("jbdir-jar-ok");
			byte[] jarContent = prePopulateJbdir(jbdir, true);

			Map<String, String> env = baseBashEnv("jar-ok");
			env.put("JBANG_DIR", jbdir.toString());
			env.put("JBANG_JAR_CHECKSUM", sha256Hex(jarContent));

			RunResult r = runProcess(bashCmd("version"), env);

			assertEquals(0, r.exitCode, "should pass with correct jar checksum, stderr: " + r.stderr);
		}

		@Test
		void jarChecksumMismatchFails() throws Exception {
			Path jbdir = tempSubDir("jbdir-jar-bad");
			prePopulateJbdir(jbdir, true);

			Map<String, String> env = baseBashEnv("jar-bad");
			env.put("JBANG_DIR", jbdir.toString());
			env.put("JBANG_JAR_CHECKSUM", "badhash");

			RunResult r = runProcess(bashCmd("version"), env);

			assertNotEquals(0, r.exitCode, "should fail on jar checksum mismatch");
			assertTrue(r.stderr.contains("checksum mismatch"),
					"stderr should say mismatch: " + r.stderr);
		}
	}

	// -------------------------------------------------------------------------
	// PowerShell
	// -------------------------------------------------------------------------

	// Note: no hash-tool-unavailable tests for PowerShell. The PS1 script
	// uses Get-FileHash which is a built-in PowerShell cmdlet — always
	// available, cannot be removed via PATH. The bash script shells out to
	// sha256sum/shasum which are external binaries and may be absent.

	@Nested
	class PowerShell {

		@BeforeEach
		void checkPowerShell() {
			requirePowerShell();
		}

		private Map<String, String> psEnv(String suffix) {
			Map<String, String> env = basePsEnv(suffix);
			env.put("JBANG_DOWNLOAD_URL", wm.url("/jbang.zip"));
			env.put("JBANG_DOWNLOAD_RETRY", "0");
			return env;
		}

		@Test
		void correctSidecarChecksumPasses() throws Exception {
			byte[] zip = createJbangZip();
			String checksum = sha256Hex(zip);
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));
			wm.stubFor(WireMock.get("/jbang.zip.sha256")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(checksum)));

			RunResult r = runProcess(psCmd("version"), psEnv("sidecar-ok"));

			assertEquals(0, r.exitCode, "should pass with correct sidecar checksum, stderr: " + r.stderr);
		}

		@Test
		void mismatchedSidecarChecksumFails() throws Exception {
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));
			wm.stubFor(WireMock.get("/jbang.zip.sha256")
				.willReturn(WireMock.aResponse()
					.withStatus(200)
					.withBody("0000000000000000000000000000000000000000000000000000000000000000")));

			RunResult r = runProcess(psCmd("version"), psEnv("sidecar-bad"));

			assertNotEquals(0, r.exitCode, "should fail on checksum mismatch");
			assertTrue(r.stderr.contains("checksum mismatch"), "stderr should say mismatch: " + r.stderr);
		}

		@Test
		void explicitChecksumOverridePasses() throws Exception {
			byte[] zip = createJbangZip();
			String checksum = sha256Hex(zip);
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));

			Map<String, String> env = psEnv("override-ok");
			env.put("JBANG_DOWNLOAD_CHECKSUM", checksum);

			RunResult r = runProcess(psCmd("version"), env);

			assertEquals(0, r.exitCode, "should pass with correct explicit checksum, stderr: " + r.stderr);
			wm.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/jbang.zip.sha256")));
		}

		@Test
		void explicitChecksumOverrideMismatchFails() throws Exception {
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));

			Map<String, String> env = psEnv("override-bad");
			env.put("JBANG_DOWNLOAD_CHECKSUM", "badhash");

			RunResult r = runProcess(psCmd("version"), env);

			assertNotEquals(0, r.exitCode, "should fail on explicit checksum mismatch");
			assertTrue(r.stderr.contains("checksum mismatch"), "stderr should say mismatch: " + r.stderr);
		}

		@Test
		void uppercaseChecksumMatchesLowercase() throws Exception {
			byte[] zip = createJbangZip();
			String checksum = sha256Hex(zip).toUpperCase();
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));

			Map<String, String> env = psEnv("upper");
			env.put("JBANG_DOWNLOAD_CHECKSUM", checksum);

			RunResult r = runProcess(psCmd("version"), env);

			assertEquals(0, r.exitCode, "uppercase checksum should match, stderr: " + r.stderr);
		}

		@Test
		void emptySidecarChecksumShouldWarnNotSilentlyContinue() throws Exception {
			// Empty .sha256 should be treated as unavailable: warn and continue.
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));
			wm.stubFor(WireMock.get("/jbang.zip.sha256")
				.willReturn(WireMock.aResponse().withStatus(200).withBody("")));

			RunResult r = runProcess(psCmd("version"), psEnv("empty-sidecar"));

			assertEquals(0, r.exitCode, "should succeed with empty sidecar, stderr: " + r.stderr);
			assertTrue(!r.stderr.contains("InvalidOperation"),
					"should not crash on empty sidecar, stderr: " + r.stderr);
			assertTrue(r.stderr.contains("Warning") || r.stderr.contains("skipping"),
					"should warn about empty sidecar checksum, stderr: " + r.stderr);
		}

		@Test
		void missingSidecarChecksumWarnsAndContinues() throws Exception {
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));
			wm.stubFor(WireMock.get("/jbang.zip.sha256")
				.willReturn(WireMock.aResponse().withStatus(404)));

			RunResult r = runProcess(psCmd("version"), psEnv("no-sidecar"));

			assertEquals(0, r.exitCode, "should succeed when sidecar is unavailable, stderr: " + r.stderr);
			assertTrue(r.stderr.contains("Warning") || r.stderr.contains("skipping"),
					"should warn about missing sidecar, stderr: " + r.stderr);
		}

		@Test
		void uppercaseSidecarChecksumNormalized() throws Exception {
			byte[] zip = createJbangZip();
			String checksum = sha256Hex(zip).toUpperCase();
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));
			wm.stubFor(WireMock.get("/jbang.zip.sha256")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(checksum)));

			RunResult r = runProcess(psCmd("version"), psEnv("upper-sidecar"));

			assertEquals(0, r.exitCode,
					"uppercase sidecar checksum should match after normalization, stderr: " + r.stderr);
		}

		@Test
		void emptyExplicitChecksumShouldFallbackToSidecar() throws Exception {
			// Security concern: empty JBANG_DOWNLOAD_CHECKSUM should behave like unset
			byte[] zip = createJbangZip();
			String checksum = sha256Hex(zip);
			wm.stubFor(WireMock.get("/jbang.zip")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));
			wm.stubFor(WireMock.get("/jbang.zip.sha256")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(checksum)));

			Map<String, String> env = psEnv("empty-override");
			env.put("JBANG_DOWNLOAD_CHECKSUM", "");

			RunResult r = runProcess(psCmd("version"), env);

			// With empty override, should fall back to sidecar and verify
			assertEquals(0, r.exitCode, "empty override should fall back to sidecar, stderr: " + r.stderr);
			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/jbang.zip.sha256")));
		}

		// -- JBANG_JAR_CHECKSUM / JBANG_BIN_CHECKSUM -------------------------

		@Test
		void jarChecksumMatchPasses() throws Exception {
			Path jbdir = tempSubDir("jbdir-jar-ok");
			byte[] jarContent = prePopulateJbdir(jbdir, false);

			Map<String, String> env = basePsEnv("jar-ok");
			env.put("JBANG_DIR", jbdir.toString());
			env.put("JBANG_JAR_CHECKSUM", sha256Hex(jarContent));

			RunResult r = runProcess(psCmd("version"), env);

			assertEquals(0, r.exitCode,
					"should pass with correct jar checksum, stderr: " + r.stderr);
		}

		@Test
		void jarChecksumMismatchFails() throws Exception {
			Path jbdir = tempSubDir("jbdir-jar-bad");
			prePopulateJbdir(jbdir, false);

			Map<String, String> env = basePsEnv("jar-bad");
			env.put("JBANG_DIR", jbdir.toString());
			env.put("JBANG_JAR_CHECKSUM", "badhash");

			RunResult r = runProcess(psCmd("version"), env);

			assertNotEquals(0, r.exitCode, "should fail on jar checksum mismatch");
			assertTrue(r.stderr.contains("checksum mismatch"),
					"stderr should say mismatch: " + r.stderr);
		}
	}
}
