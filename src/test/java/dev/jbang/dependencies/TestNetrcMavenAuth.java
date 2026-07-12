package dev.jbang.dependencies;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.jbang.BaseTest;
import dev.jbang.util.NetUtil;

/**
 * Tests that .netrc and settings.xml credentials are sent in HTTP requests when
 * the Aether artifact resolver contacts Maven repositories. Uses WireMock to
 * inspect the actual Authorization headers on outgoing requests.
 */
@WireMockTest
public class TestNetrcMavenAuth extends BaseTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void clearAuthEnv() {
		// Ensure no env var fallback interferes with tests
		environmentVariables.clear(NetUtil.JBANG_AUTH_BASIC_USERNAME, NetUtil.JBANG_AUTH_BASIC_PASSWORD);
		environmentVariables.clear("GITHUB_TOKEN", "GITLAB_TOKEN");
	}

	@AfterEach
	void cleanup() {
		NetUtil.setNetrcFile(null);
	}

	private void setNetrc(String content) throws IOException {
		Path netrc = tempDir.resolve(".netrc");
		Files.writeString(netrc, content);
		try {
			Files.setPosixFilePermissions(netrc,
					EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		} catch (UnsupportedOperationException ignored) {
		}
		NetUtil.setNetrcFile(netrc);
	}

	private Path writeSettingsXml(String content) throws IOException {
		Path settingsXml = tempDir.resolve("settings.xml");
		Files.writeString(settingsXml, content);
		return settingsXml;
	}

	private static String basicAuth(String user, String pass) {
		return "Basic " + Base64.getEncoder()
			.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Stub a Maven repo that requires Basic auth. Unauthenticated requests get 401
	 * (triggering Aether's challenge-response), authenticated ones get 404
	 * (artifact not found — we only care about the auth headers).
	 */
	private void stubAuthenticatedMavenRepo(String expectedUser, String expectedPass) {
		String creds = basicAuth(expectedUser, expectedPass);
		// Correct credentials → 404 (artifact not found, but auth OK)
		WireMock.stubFor(WireMock.any(WireMock.anyUrl())
			.withHeader("Authorization", WireMock.equalTo(creds))
			.willReturn(WireMock.aResponse().withStatus(404)));
		// Missing credentials → 401 challenge
		WireMock.stubFor(WireMock.any(WireMock.anyUrl())
			.withHeader("Authorization", WireMock.absent())
			.willReturn(WireMock.aResponse()
				.withStatus(401)
				.withHeader("WWW-Authenticate", "Basic realm=\"Maven Repo\"")));
	}

	/**
	 * Stub a Maven repo that accepts any request (no auth required).
	 */
	private void stubOpenMavenRepo() {
		WireMock.stubFor(WireMock.any(WireMock.anyUrl())
			.willReturn(WireMock.aResponse().withStatus(404)));
	}

	/**
	 * Try to resolve a dummy artifact (will fail) and return the logged requests so
	 * we can inspect their Authorization headers.
	 */
	private List<LoggedRequest> resolveAndCapture(WireMockRuntimeInfo wmri,
			String repoId, Path settingsXml) {

		MavenRepo repo = new MavenRepo(repoId, wmri.getHttpBaseUrl() + "/maven/");

		ArtifactResolver.Builder builder = ArtifactResolver.Builder
			.create()
			.repositories(Collections.singletonList(repo))
			.logging(false);

		if (settingsXml != null) {
			builder.withUserSettings(true).settingsXml(settingsXml);
		}

		try (ArtifactResolver resolver = builder.build()) {
			resolver.resolve(Collections.singletonList(
					"com.example.test:nonexistent-artifact:1.0.0"));
		} catch (Exception e) {
			// Expected — artifact doesn't exist
		}

		return WireMock.findAll(WireMock.anyRequestedFor(WireMock.anyUrl()));
	}

	// --- Unit-level tests for NetUtil.getCredentialsForHost ---

	@Test
	void testGetCredentialsForHostReturnsCredentials() throws IOException {
		setNetrc("machine maven.pkg.github.com\nlogin __token__\npassword ghp_testtoken123\n");

		String[] creds = NetUtil.getCredentialsForHost("maven.pkg.github.com");
		assertThat(creds, notNullValue());
		assertThat(creds[0], equalTo("__token__"));
		assertThat(creds[1], equalTo("ghp_testtoken123"));
	}

	@Test
	void testGetCredentialsForHostReturnsNullWhenNoMatch() throws IOException {
		setNetrc("machine other.example.com\nlogin user\npassword pass\n");

		String[] creds = NetUtil.getCredentialsForHost("unmatched.example.com");
		assertThat(creds, nullValue());
	}

	@Test
	void testGetCredentialsForHostUsesDefaultEntry() throws IOException {
		setNetrc("default\nlogin fallback\npassword fallbackpass\n");

		String[] creds = NetUtil.getCredentialsForHost("any.example.com");
		assertThat(creds, notNullValue());
		assertThat(creds[0], equalTo("fallback"));
		assertThat(creds[1], equalTo("fallbackpass"));
	}

	// --- WireMock integration tests: verify actual HTTP auth headers ---

	@Test
	void testNetrcCredentialsSentToMavenRepo(WireMockRuntimeInfo wmri) throws IOException {
		setNetrc("machine localhost\nlogin deploy-user\npassword deploy-pass\n");

		stubAuthenticatedMavenRepo("deploy-user", "deploy-pass");
		List<LoggedRequest> requests = resolveAndCapture(wmri, "my-repo", null);

		assertThat("Should have made at least one request", requests.size(), greaterThan(0));

		// After the 401 challenge, Aether should retry with credentials
		String expectedAuth = basicAuth("deploy-user", "deploy-pass");
		List<LoggedRequest> authed = requests.stream()
			.filter(r -> expectedAuth.equals(r.getHeader("Authorization")))
			.collect(Collectors.toList());
		assertThat("At least one request should carry .netrc credentials",
				authed.size(), greaterThan(0));
	}

	@Test
	void testSettingsXmlCredentialsSentToMavenRepo(WireMockRuntimeInfo wmri) throws IOException {
		// No netrc
		setNetrc("");

		Path settings = writeSettingsXml(
				"<settings>\n"
						+ "  <servers>\n"
						+ "    <server>\n"
						+ "      <id>settings-repo</id>\n"
						+ "      <username>settings-user</username>\n"
						+ "      <password>settings-pass</password>\n"
						+ "    </server>\n"
						+ "  </servers>\n"
						+ "</settings>\n");

		stubAuthenticatedMavenRepo("settings-user", "settings-pass");
		// Repo ID matches the server ID in settings.xml
		List<LoggedRequest> requests = resolveAndCapture(wmri, "settings-repo", settings);

		assertThat("Should have made at least one request", requests.size(), greaterThan(0));

		String expectedAuth = basicAuth("settings-user", "settings-pass");
		List<LoggedRequest> authed = requests.stream()
			.filter(r -> expectedAuth.equals(r.getHeader("Authorization")))
			.collect(Collectors.toList());
		assertThat("At least one request should carry settings.xml credentials",
				authed.size(), greaterThan(0));
	}

	@Test
	void testSettingsXmlOverridesNetrcForSameRepo(WireMockRuntimeInfo wmri) throws IOException {
		// Both provide credentials — settings.xml should win
		setNetrc("machine localhost\nlogin netrc-user\npassword netrc-pass\n");

		Path settings = writeSettingsXml(
				"<settings>\n"
						+ "  <servers>\n"
						+ "    <server>\n"
						+ "      <id>my-repo</id>\n"
						+ "      <username>settings-user</username>\n"
						+ "      <password>settings-pass</password>\n"
						+ "    </server>\n"
						+ "  </servers>\n"
						+ "</settings>\n");

		// Server expects the settings.xml credentials (not the netrc ones)
		stubAuthenticatedMavenRepo("settings-user", "settings-pass");
		List<LoggedRequest> requests = resolveAndCapture(wmri, "my-repo", settings);

		assertThat("Should have made at least one request", requests.size(), greaterThan(0));

		String settingsAuth = basicAuth("settings-user", "settings-pass");
		String netrcAuth = basicAuth("netrc-user", "netrc-pass");

		List<LoggedRequest> authedSettings = requests.stream()
			.filter(r -> settingsAuth.equals(r.getHeader("Authorization")))
			.collect(Collectors.toList());
		List<LoggedRequest> authedNetrc = requests.stream()
			.filter(r -> netrcAuth.equals(r.getHeader("Authorization")))
			.collect(Collectors.toList());

		assertThat("At least one request should use settings.xml credentials",
				authedSettings.size(), greaterThan(0));
		assertThat("No request should use .netrc credentials when settings.xml matches",
				authedNetrc.size(), equalTo(0));
	}

	@Test
	void testNetrcUsedWhenSettingsXmlServerDoesNotMatch(WireMockRuntimeInfo wmri) throws IOException {
		// settings.xml has a server, but for a different repo ID
		setNetrc("machine localhost\nlogin netrc-user\npassword netrc-pass\n");

		Path settings = writeSettingsXml(
				"<settings>\n"
						+ "  <servers>\n"
						+ "    <server>\n"
						+ "      <id>other-repo</id>\n"
						+ "      <username>other-user</username>\n"
						+ "      <password>other-pass</password>\n"
						+ "    </server>\n"
						+ "  </servers>\n"
						+ "</settings>\n");

		// Server expects netrc credentials (settings.xml server ID doesn't match)
		stubAuthenticatedMavenRepo("netrc-user", "netrc-pass");
		// Repo ID does NOT match the settings.xml server ID
		List<LoggedRequest> requests = resolveAndCapture(wmri, "unmatched-repo", settings);

		assertThat("Should have made at least one request", requests.size(), greaterThan(0));

		String expectedAuth = basicAuth("netrc-user", "netrc-pass");
		List<LoggedRequest> authed = requests.stream()
			.filter(r -> expectedAuth.equals(r.getHeader("Authorization")))
			.collect(Collectors.toList());
		assertThat(".netrc should be used when no settings.xml server matches",
				authed.size(), greaterThan(0));
	}

	@Test
	void testNoAuthWhenNeitherNetrcNorSettingsXml(WireMockRuntimeInfo wmri) throws IOException {
		setNetrc("");

		stubOpenMavenRepo();
		List<LoggedRequest> requests = resolveAndCapture(wmri, "anon-repo", null);

		assertThat("Should have made at least one request", requests.size(), greaterThan(0));

		for (LoggedRequest req : requests) {
			assertThat("No auth should be sent for " + req.getUrl(),
					req.containsHeader("Authorization"), equalTo(false));
		}
	}

	// --- jbang-auth credential helper tests ---

	@Test
	void testGitCredentialFallsBackToLoginPassword() throws IOException {
		// jbang-auth git-credential for a host git doesn't know,
		// but also has login/password — should fall back to login/password
		setNetrc("machine no-git-creds.example.com\n"
				+ "login fallback-user\n"
				+ "password fallback-pass\n"
				+ "jbang-auth git-credential\n");

		String[] creds = NetUtil.getCredentialsForHost("no-git-creds.example.com");
		assertThat("Should fall back to .netrc login/password", creds, notNullValue());
		assertThat(creds[0], equalTo("fallback-user"));
		assertThat(creds[1], equalTo("fallback-pass"));
	}

	@Test
	void testGetCredentialsForHostWithJbangAuthOnly() throws IOException {
		// jbang-auth git-credential for a host git doesn't know, no login/password
		setNetrc("machine unknown-git-host.example.com\njbang-auth git-credential\n");

		String[] creds = NetUtil.getCredentialsForHost("unknown-git-host.example.com");
		// git credential fill will fail for unknown host, no login/password fallback
		assertThat("Should return null when git-credential fails and no login/password",
				creds, nullValue());
	}

	@Test
	void testGhAuthFallsBackToLoginPassword() throws IOException {
		// gh-auth for a host gh doesn't know, with login/password fallback
		setNetrc("machine no-gh-host.example.com\n"
				+ "login fallback-user\n"
				+ "password fallback-pass\n"
				+ "jbang-auth gh-auth\n");

		String[] creds = NetUtil.getCredentialsForHost("no-gh-host.example.com");
		assertThat("Should fall back to login/password after gh-auth fails", creds, notNullValue());
		assertThat(creds[0], equalTo("fallback-user"));
		assertThat(creds[1], equalTo("fallback-pass"));
	}

	@Test
	void testGlabAuthFallsBackToLoginPassword() throws IOException {
		// glab-auth for a host glab doesn't know, with login/password fallback
		setNetrc("machine no-glab-host.example.com\n"
				+ "login fallback-user\n"
				+ "password fallback-pass\n"
				+ "jbang-auth glab-auth\n");

		String[] creds = NetUtil.getCredentialsForHost("no-glab-host.example.com");
		assertThat("Should fall back to login/password after glab-auth fails", creds, notNullValue());
		assertThat(creds[0], equalTo("fallback-user"));
		assertThat(creds[1], equalTo("fallback-pass"));
	}

	@Test
	void testCommaSeparatedFallsThrough() throws IOException {
		// All methods fail for unknown host, falls back to login/password
		setNetrc("machine unknown.example.com\n"
				+ "login fallback-user\n"
				+ "password fallback-pass\n"
				+ "jbang-auth gh-auth,glab-auth,git-credential\n");

		String[] creds = NetUtil.getCredentialsForHost("unknown.example.com");
		assertThat("Should fall back to login/password", creds, notNullValue());
		assertThat(creds[0], equalTo("fallback-user"));
		assertThat(creds[1], equalTo("fallback-pass"));
	}

	@Test
	void testEnvAuthMethodUsesLoginAndUppercasePasswordVariable() throws IOException {
		environmentVariables.set("MY_REPO_TOKEN", "env-pass");
		setNetrc("machine example.com\n"
				+ "login env-user\n"
				+ "jbang-auth env.my_repo_token\n");

		String[] creds = NetUtil.getCredentialsForHost("example.com");

		assertThat(creds, notNullValue());
		assertThat(creds[0], equalTo("env-user"));
		assertThat(creds[1], equalTo("env-pass"));
	}

	@Test
	void testEnvAuthMethodCanReadUsernameAndPasswordVariables() throws IOException {
		environmentVariables.set("MY_REPO_USER", "env-user");
		environmentVariables.set("MY_REPO_TOKEN", "env-pass");
		setNetrc("machine example.com\n"
				+ "jbang-auth env.my_repo_user-my_repo_token\n");

		String[] creds = NetUtil.getCredentialsForHost("example.com");

		assertThat(creds, notNullValue());
		assertThat(creds[0], equalTo("env-user"));
		assertThat(creds[1], equalTo("env-pass"));
	}

	@Test
	void testUnknownAuthMethodIgnored() throws IOException {
		// Unknown method is skipped, falls back to login/password
		setNetrc("machine example.com\n"
				+ "login user\n"
				+ "password pass\n"
				+ "jbang-auth nonexistent-method\n");

		String[] creds = NetUtil.getCredentialsForHost("example.com");
		assertThat(creds, notNullValue());
		assertThat(creds[0], equalTo("user"));
		assertThat(creds[1], equalTo("pass"));
	}

	@Test
	void testJbangAuthHostRedirectsLookup() throws IOException {
		// jbang-auth-host redirects the lookup but we can test the plumbing
		// without real gh/git by having it fail and fall back to login/password
		setNetrc("machine maven.pkg.github.com\n"
				+ "login myuser\n"
				+ "password fallback-pass\n"
				+ "jbang-auth gh-auth\n"
				+ "jbang-auth-host some-other-host.example.com\n");

		String[] creds = NetUtil.getCredentialsForHost("maven.pkg.github.com");
		// gh-auth will fail for some-other-host.example.com, falls back to
		// login/password
		assertThat(creds, notNullValue());
		assertThat(creds[0], equalTo("myuser"));
		assertThat(creds[1], equalTo("fallback-pass"));
	}
}
