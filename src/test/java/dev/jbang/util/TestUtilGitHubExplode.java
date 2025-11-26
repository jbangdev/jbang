package dev.jbang.util;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import dev.jbang.BaseTest;

@WireMockTest
public class TestUtilGitHubExplode extends BaseTest {

	@Test
	void testExplodeGitHubPatternSimple(WireMockRuntimeInfo wmri) throws IOException {
		// Mock GitHub API response for directory
		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("["
								+ "{\"name\":\"App.java\",\"type\":\"file\",\"path\":\"src/App.java\"},"
								+ "{\"name\":\"Helper.java\",\"type\":\"file\",\"path\":\"src/Helper.java\"}"
								+ "]")));

		String sourceUrl = "https://github.com/owner/repo/tree/main/src";
		List<String> results = Util.explode(sourceUrl, Path.of("."), "*.java");

		assertThat(results, notNullValue());
		assertThat(results.size(), greaterThan(0));
		assertThat(results, hasItem("App.java"));
		assertThat(results, hasItem("Helper.java"));
	}

	@Test
	void testExplodeGitHubPatternRecursive(WireMockRuntimeInfo wmri) throws IOException {
		// Mock GitHub API responses for nested directories
		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("["
								+ "{\"name\":\"App.java\",\"type\":\"file\",\"path\":\"src/App.java\"},"
								+ "{\"name\":\"util\",\"type\":\"dir\",\"path\":\"src/util\"}"
								+ "]")));

		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src/util?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("["
								+ "{\"name\":\"Helper.java\",\"type\":\"file\",\"path\":\"src/util/Helper.java\"}"
								+ "]")));

		String sourceUrl = "https://github.com/owner/repo/tree/main/src";
		List<String> results = Util.explode(sourceUrl, Path.of("."), "**/*.java");

		assertThat(results, notNullValue());
		assertThat(results.size(), greaterThanOrEqualTo(2));
		assertThat(results, hasItem("App.java"));
		assertThat(results, hasItem("util/Helper.java"));
	}

	@Test
	void testExplodeGitHubUrlWithoutPattern(WireMockRuntimeInfo wmri) throws IOException {
		// Mock GitHub API response for file
		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src/App.java?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("{\"name\":\"App.java\",\"type\":\"file\",\"path\":\"src/App.java\"}")));

		String sourceUrl = "https://github.com/owner/repo/blob/main/src/App.java";
		List<String> results = Util.explode(sourceUrl, Path.of("."), "App.java");

		assertThat(results, notNullValue());
		assertThat(results.size(), equalTo(1));
		assertThat(results.get(0), equalTo("App.java"));
	}

	@Test
	void testIsGitHubUrl() {
		assertThat(Util.isGitHubUrl("https://github.com/owner/repo"), is(true));
		assertThat(Util.isGitHubUrl("https://raw.githubusercontent.com/owner/repo/branch/file"), is(true));
		assertThat(Util.isGitHubUrl("https://example.com/file"), is(false));
		assertThat(Util.isGitHubUrl(null), is(false));
	}
}

