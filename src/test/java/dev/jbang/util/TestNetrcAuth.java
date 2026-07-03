package dev.jbang.util;

import static dev.jbang.util.NetUtil.ConnectionConfigurator.authentication;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;

/**
 * Tests for .netrc integration in the authentication flow.
 */
public class TestNetrcAuth extends BaseTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void cleanup() {
		NetUtil.resetNetrcCache();
	}

	private void setNetrc(String content) throws IOException {
		Path netrc = tempDir.resolve(".netrc");
		Files.writeString(netrc, content);
		NetUtil.setNetrcFile(netrc);
	}

	@Test
	void testNetrcCredentialsForGitlab() throws IOException {
		setNetrc("machine gitlab.mycompany.com\nlogin __token__\npassword glpat-testtoken123\n");

		URLConnection connection = new NoOpUrlConnection(
				new URL("https://gitlab.mycompany.com/group/project/-/raw/main/script.java"));

		authentication().configure(connection);

		String expected = "Basic " + Base64.getEncoder()
			.encodeToString("__token__:glpat-testtoken123".getBytes(StandardCharsets.UTF_8));
		assertThat(connection.getRequestProperty("Authorization"), equalTo(expected));
	}

	@Test
	void testNetrcNoMatchNoAuth() throws IOException {
		// Skip if global fallback auth env vars are set — they'd add auth
		// independent of .netrc and make this assertion meaningless
		org.junit.jupiter.api.Assumptions.assumeTrue(
				System.getenv(NetUtil.JBANG_AUTH_BASIC_USERNAME) == null
						|| System.getenv(NetUtil.JBANG_AUTH_BASIC_PASSWORD) == null,
				"Skipping: JBANG_AUTH_BASIC_* env vars are set");

		setNetrc("machine gitlab.com\nlogin __token__\npassword glpat-xxxx\n");

		URLConnection connection = new NoOpUrlConnection(
				new URL("https://unknown.example.com/file.java"));

		authentication().configure(connection);

		assertThat(connection.getRequestProperty("Authorization"), nullValue());
	}

	@Test
	void testNetrcDefaultEntry() throws IOException {
		setNetrc("default\nlogin user\npassword pass123\n");

		URLConnection connection = new NoOpUrlConnection(
				new URL("https://any-host.example.com/file.java"));

		authentication().configure(connection);

		String expected = "Basic " + Base64.getEncoder()
			.encodeToString("user:pass123".getBytes(StandardCharsets.UTF_8));
		assertThat(connection.getRequestProperty("Authorization"), equalTo(expected));
	}
}
