package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

/**
 * Functional tests for download retry support in JBang startup scripts. Runs
 * the real jbang/jbang.ps1 scripts against a WireMock server that simulates
 * transient download failures, using JBANG_DOWNLOAD_URL to redirect downloads
 * to WireMock.
 *
 * See https://github.com/jbangdev/jbang/issues/2459
 */
class TestScriptRetry extends AbstractScriptTest {

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

	private Map<String, String> bashEnv(int retryCount) {
		Map<String, String> env = baseBashEnv("retry-" + retryCount);
		env.put("JBANG_DOWNLOAD_URL", wm.url("/jbang.tar"));
		env.put("JBANG_DOWNLOAD_RETRY", String.valueOf(retryCount));
		env.put("JBANG_DOWNLOAD_RETRY_DELAY", "0");
		return env;
	}

	private Map<String, String> psEnv(int retryCount) {
		Map<String, String> env = basePsEnv("retry-" + retryCount);
		env.put("JBANG_DOWNLOAD_URL", wm.url("/jbang.zip"));
		env.put("JBANG_DOWNLOAD_RETRY", String.valueOf(retryCount));
		env.put("JBANG_DOWNLOAD_RETRY_DELAY", "0");
		return env;
	}

	// -------------------------------------------------------------------------
	// Bash tests — runs src/main/scripts/jbang with JBANG_DOWNLOAD_URL
	// pointing at WireMock
	// -------------------------------------------------------------------------

	@Nested
	class BashDownloadRetry {

		@BeforeEach
		void checkBash() {
			requireBash();
		}

		@Test
		void downloadSucceedsAfterTransientFailures() throws Exception {
			byte[] tar = createJbangTar();
			stubFlakyEndpoint("/jbang.tar", 3, tar);

			RunResult result = runProcess(bashCmd("version"), bashEnv(5));

			// Download should have succeeded (no download error in stderr)
			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded after retries, stderr: " + result.stderr);
		}

		@Test
		void downloadFailsWhenRetriesExhausted() throws Exception {
			byte[] tar = createJbangTar();
			stubFlakyEndpoint("/jbang.tar", 10, tar);

			RunResult result = runProcess(bashCmd("version"), bashEnv(2));

			assertNotEquals(0, result.exitCode, "script should have failed");
			assertTrue(result.stderr.contains("Error downloading JBang"),
					"stderr should mention download error, was: " + result.stderr);
		}

		@Test
		void downloadFailsWithZeroRetries() throws Exception {
			byte[] tar = createJbangTar();
			stubFlakyEndpoint("/jbang.tar", 1, tar);

			RunResult result = runProcess(bashCmd("version"), bashEnv(0));

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

		@BeforeEach
		void checkPowerShell() {
			requirePowerShell();
		}

		@Test
		void downloadSucceedsAfterTransientFailures() throws Exception {
			byte[] zip = createJbangZip();
			stubFlakyEndpoint("/jbang.zip", 3, zip);

			RunResult result = runProcess(psCmd("version"), psEnv(5));

			assertTrue(!result.stderr.contains("Error downloading JBang"),
					"download should have succeeded after retries, stderr: " + result.stderr);
		}

		@Test
		void downloadFailsWhenRetriesExhausted() throws Exception {
			byte[] zip = createJbangZip();
			stubFlakyEndpoint("/jbang.zip", 10, zip);

			RunResult result = runProcess(psCmd("version"), psEnv(2));

			assertNotEquals(0, result.exitCode, "script should have failed");
			assertTrue(result.stderr.contains("Error downloading JBang"),
					"stderr should mention download error, was: " + result.stderr);
		}

		@Test
		void downloadFailsWithZeroRetries() throws Exception {
			byte[] zip = createJbangZip();
			stubFlakyEndpoint("/jbang.zip", 1, zip);

			RunResult result = runProcess(psCmd("version"), psEnv(0));

			assertNotEquals(0, result.exitCode, "script should have failed");
			assertTrue(result.stderr.contains("Error downloading JBang"),
					"stderr should mention download error, was: " + result.stderr);
		}
	}
}
