package dev.jbang.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;

/**
 * Tests for {@link NetUtil#describeAuthMethod(String)}.
 */
public class TestDescribeAuthMethod extends BaseTest {

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
	void testUrlCredentialsDetected() {
		assertThat(NetUtil.describeAuthMethod("https://user:pass@example.com/file.java"),
				equalTo("URL credentials"));
		// Non-matching URL without credentials returns null
		assertThat(NetUtil.describeAuthMethod("https://example.com/file.java"), nullValue());
	}

	@Test
	void testNetrcDetected() throws IOException {
		setNetrc("machine myserver.example.com\nlogin admin\npassword secret\n");
		assertThat(NetUtil.describeAuthMethod("https://myserver.example.com/path/file.java"),
				equalTo(".netrc"));
		// Host not in .netrc returns null
		assertThat(NetUtil.describeAuthMethod("https://otherserver.example.com/file.java"), nullValue());
	}

	@Test
	void testNetrcDefaultEntryDetected() throws IOException {
		setNetrc("default\nlogin fallback\npassword fallbackpass\n");
		assertThat(NetUtil.describeAuthMethod("https://any-host.example.com/file.java"),
				equalTo(".netrc"));
	}

	@Test
	void testNoAuthReturnsNull() throws IOException {
		// Set an empty .netrc so it won't match anything
		setNetrc("");
		assertThat(NetUtil.describeAuthMethod("https://example.com/file.java"), nullValue());
	}

	@Test
	void testNetrcNoMatchFallsThrough() throws IOException {
		setNetrc("machine other.example.com\nlogin user\npassword pass\n");
		assertThat(NetUtil.describeAuthMethod("https://nomatch.example.com/file.java"), nullValue());
	}

	@Test
	void testInvalidUrlReturnsNull() {
		assertThat(NetUtil.describeAuthMethod("not-a-url"), nullValue());
	}

	@Test
	void testUrlCredentialsTakePriorityOverNetrc() throws IOException {
		setNetrc("machine example.com\nlogin netrcuser\npassword netrcpass\n");
		// URL credentials (step 2) should win over .netrc (step 3)
		assertThat(NetUtil.describeAuthMethod("https://user:pass@example.com/file.java"),
				equalTo("URL credentials"));
	}
}
