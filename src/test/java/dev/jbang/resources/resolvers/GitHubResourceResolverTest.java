package dev.jbang.resources.resolvers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import dev.jbang.BaseTest;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;

@WireMockTest
public class GitHubResourceResolverTest extends BaseTest {

	@Test
	void testResolveGitHubUrl(WireMockRuntimeInfo wmri) throws IOException {
		// Mock GitHub API response for file
		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src/App.java?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("{\"name\":\"App.java\",\"type\":\"file\",\"path\":\"src/App.java\"}")));

		// Mock raw content
		wmri.getWireMock().stubFor(get(urlEqualTo("/owner/repo/main/src/App.java"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "text/plain")
						.withBody("public class App { }")));

		ResourceResolver resolver = new GitHubResourceResolver();
		ResourceRef ref = resolver.resolve("https://github.com/owner/repo/blob/main/src/App.java", true);

		assertThat(ref, notNullValue());
		assertThat(ref.exists(), is(true));
		assertThat(ref.getFile(), notNullValue());
		assertThat(Files.isRegularFile(ref.getFile()), is(true));
	}

	@Test
	void testResolveGitHubDirectory(WireMockRuntimeInfo wmri) throws IOException {
		// Mock GitHub API response for directory
		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("["
								+ "{\"name\":\"App.java\",\"type\":\"file\",\"path\":\"src/App.java\"},"
								+ "{\"name\":\"Helper.java\",\"type\":\"file\",\"path\":\"src/Helper.java\"}"
								+ "]")));

		ResourceResolver resolver = new GitHubResourceResolver();
		ResourceRef ref = resolver.resolve("https://github.com/owner/repo/tree/main/src", true);

		assertThat(ref, notNullValue());
		assertThat(ref.exists(), is(true));
		assertThat(ref.isParent(), is(true));

		ResourceRef.ResourceChildren children = ref.children();
		assertThat(children, notNullValue());
	}

	@Test
	void testResolveNonGitHubUrl() {
		ResourceResolver resolver = new GitHubResourceResolver();
		ResourceRef ref = resolver.resolve("https://example.com/file.java", true);

		assertThat(ref, nullValue());
	}

	@Test
	void testResolveRelativePath(WireMockRuntimeInfo wmri) throws IOException {
		// Mock GitHub API responses
		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("["
								+ "{\"name\":\"App.java\",\"type\":\"file\",\"path\":\"src/App.java\"},"
								+ "{\"name\":\"Helper.java\",\"type\":\"file\",\"path\":\"src/Helper.java\"}"
								+ "]")));

		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src/Helper.java?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("{\"name\":\"Helper.java\",\"type\":\"file\",\"path\":\"src/Helper.java\"}")));

		wmri.getWireMock().stubFor(get(urlEqualTo("/owner/repo/main/src/Helper.java"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "text/plain")
						.withBody("public class Helper { }")));

		ResourceResolver resolver = new GitHubResourceResolver();
		ResourceRef baseRef = resolver.resolve("https://github.com/owner/repo/tree/main/src", true);

		assertThat(baseRef, notNullValue());

		ResourceRef helperRef = baseRef.resolve("Helper.java", true);
		assertThat(helperRef, notNullValue());
		assertThat(helperRef.exists(), is(true));
	}
}

