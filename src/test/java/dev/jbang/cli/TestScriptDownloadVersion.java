package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

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
class TestScriptDownloadVersion extends AbstractScriptTest {

	private Map<String, String> bashEnv(String version) {
		Map<String, String> env = baseBashEnv(version);
		env.put("JBANG_DOWNLOAD_BASEURL", wm.baseUrl());
		env.put("JBANG_DOWNLOAD_VERSION", version);
		env.put("JBANG_DOWNLOAD_RETRY", "0");
		env.remove("JBANG_DOWNLOAD_URL");
		return env;
	}

	private Map<String, String> psEnv(String version) {
		Map<String, String> env = basePsEnv(version);
		env.put("JBANG_DOWNLOAD_BASEURL", wm.baseUrl());
		env.put("JBANG_DOWNLOAD_VERSION", version);
		env.put("JBANG_DOWNLOAD_RETRY", "0");
		env.remove("JBANG_DOWNLOAD_URL");
		return env;
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

		@Test
		void numericVersionGetsVPrefix() throws Exception {
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/v0.120.0/jbang.tar"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			RunResult result = runProcess(bashCmd("version"), bashEnv("0.120.0"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/v0.120.0/jbang.tar")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}

		@Test
		void earlyAccessTagIsUsedAsIs() throws Exception {
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/early-access/jbang.tar"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			RunResult result = runProcess(bashCmd("version"), bashEnv("early-access"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/early-access/jbang.tar")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}

		@Test
		void prereleaseTagIsUsedAsIs() throws Exception {
			byte[] tar = createJbangTar();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/1.0.0-rc1/jbang.tar"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(tar)));

			RunResult result = runProcess(bashCmd("version"), bashEnv("1.0.0-rc1"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/1.0.0-rc1/jbang.tar")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}
	}

	// -------------------------------------------------------------------------
	// PowerShell
	// -------------------------------------------------------------------------

	@Nested
	class PowerShell {

		@BeforeEach
		void checkPowerShell() {
			requirePowerShell();
		}

		@Test
		void numericVersionGetsVPrefix() throws Exception {
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/v0.120.0/jbang.zip"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));

			RunResult result = runProcess(psCmd("version"), psEnv("0.120.0"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/v0.120.0/jbang.zip")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}

		@Test
		void earlyAccessTagIsUsedAsIs() throws Exception {
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/early-access/jbang.zip"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));

			RunResult result = runProcess(psCmd("version"), psEnv("early-access"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/early-access/jbang.zip")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}

		@Test
		void prereleaseTagIsUsedAsIs() throws Exception {
			byte[] zip = createJbangZip();
			wm.stubFor(WireMock.get(WireMock.urlEqualTo("/download/1.0.0-rc1/jbang.zip"))
				.willReturn(WireMock.aResponse().withStatus(200).withBody(zip)));

			RunResult result = runProcess(psCmd("version"), psEnv("1.0.0-rc1"));

			wm.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/download/1.0.0-rc1/jbang.zip")));
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded, stderr: " + result.stderr);
		}
	}
}
