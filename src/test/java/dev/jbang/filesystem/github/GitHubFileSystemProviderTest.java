package dev.jbang.filesystem.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import dev.jbang.BaseTest;

@WireMockTest
public class GitHubFileSystemProviderTest extends BaseTest {

	@Test
	void testParseGitHubUrl() {
		GitHubRepoInfo info = GitHubFileSystemProvider.parseGitHubUrl(
				"https://github.com/owner/repo/tree/main/src");
		assertThat(info.getOwner(), equalTo("owner"));
		assertThat(info.getRepo(), equalTo("repo"));
		assertThat(info.getRef(), equalTo("main"));
		assertThat(info.getBasePath(), equalTo("/src"));

		info = GitHubFileSystemProvider.parseGitHubUrl(
				"https://github.com/owner/repo/blob/main/src/App.java");
		assertThat(info.getOwner(), equalTo("owner"));
		assertThat(info.getRepo(), equalTo("repo"));
		assertThat(info.getRef(), equalTo("main"));
		assertThat(info.getBasePath(), equalTo("/src/App.java"));

		info = GitHubFileSystemProvider.parseGitHubUrl(
				"https://raw.githubusercontent.com/owner/repo/branch/path/to/file.java");
		assertThat(info.getOwner(), equalTo("owner"));
		assertThat(info.getRepo(), equalTo("repo"));
		assertThat(info.getRef(), equalTo("branch"));
		assertThat(info.getBasePath(), equalTo("/path/to/file.java"));
	}

	@Test
	void testCreateFileSystem(WireMockRuntimeInfo wmri) throws IOException {
		// Mock GitHub API response for directory listing
		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("["
								+ "{\"name\":\"App.java\",\"type\":\"file\",\"path\":\"src/App.java\"},"
								+ "{\"name\":\"util\",\"type\":\"dir\",\"path\":\"src/util\"}"
								+ "]")));

		// Mock raw content for file
		wmri.getWireMock().stubFor(get(urlEqualTo("/owner/repo/main/src/App.java"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "text/plain")
						.withBody("public class App {}")));

		GitHubRepoInfo repoInfo = new GitHubRepoInfo("owner", "repo", "main", "/src");
		URI fsUri = GitHubFileSystemProvider.toGitHubUri(repoInfo);

		try (FileSystem fs = FileSystems.newFileSystem(fsUri, Collections.emptyMap())) {
			assertThat(fs, notNullValue());
			assertThat(fs.isReadOnly(), is(true));

			// Test listing directory
			Path srcPath = fs.getPath("/src");
			assertThat(Files.exists(srcPath), is(true));
			assertThat(Files.isDirectory(srcPath), is(true));

			List<Path> children = Files.list(srcPath).collect(Collectors.toList());
			assertThat(children.size(), greaterThan(0));
		}
	}

	@Test
	void testReadFile(WireMockRuntimeInfo wmri) throws IOException {
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

		GitHubRepoInfo repoInfo = new GitHubRepoInfo("owner", "repo", "main", "/src/App.java");
		URI fsUri = GitHubFileSystemProvider.toGitHubUri(repoInfo);

		try (FileSystem fs = FileSystems.newFileSystem(fsUri, Collections.emptyMap())) {
			Path filePath = fs.getPath("/src/App.java");
			assertThat(Files.exists(filePath), is(true));
			assertThat(Files.isRegularFile(filePath), is(true));

			String content = new String(Files.readAllBytes(filePath));
			assertThat(content, containsString("public class App"));
		}
	}

	@Test
	void testDirectoryListing(WireMockRuntimeInfo wmri) throws IOException {
		// Mock GitHub API response for directory
		wmri.getWireMock().stubFor(get(urlEqualTo("/repos/owner/repo/contents/src?ref=main"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody("["
								+ "{\"name\":\"App.java\",\"type\":\"file\",\"path\":\"src/App.java\"},"
								+ "{\"name\":\"Helper.java\",\"type\":\"file\",\"path\":\"src/Helper.java\"},"
								+ "{\"name\":\"util\",\"type\":\"dir\",\"path\":\"src/util\"}"
								+ "]")));

		GitHubRepoInfo repoInfo = new GitHubRepoInfo("owner", "repo", "main", "/src");
		URI fsUri = GitHubFileSystemProvider.toGitHubUri(repoInfo);

		try (FileSystem fs = FileSystems.newFileSystem(fsUri, Collections.emptyMap())) {
			Path srcPath = fs.getPath("/src");
			List<Path> children = Files.list(srcPath).collect(Collectors.toList());
			assertThat(children.size(), equalTo(3));

			List<String> names = children.stream()
					.map(p -> p.getFileName().toString())
					.collect(Collectors.toList());
			assertThat(names, containsInAnyOrder("App.java", "Helper.java", "util"));
		}
	}
}

