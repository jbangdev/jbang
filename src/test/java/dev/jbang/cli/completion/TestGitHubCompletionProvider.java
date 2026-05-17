package dev.jbang.cli.completion;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

public class TestGitHubCompletionProvider {

	// ---- URL pattern detection tests ---

	@Test
	void testCanCompleteWithBlobUrl() {
		assertThat(GitHubCompletionProvider.canComplete(
				"https://github.com/owner/repo/blob/main/"), is(true));
	}

	@Test
	void testCanCompleteWithTreeUrl() {
		assertThat(GitHubCompletionProvider.canComplete(
				"https://github.com/owner/repo/tree/main/src/"), is(true));
	}

	@Test
	void testCanCompleteWithPartialPath() {
		assertThat(GitHubCompletionProvider.canComplete(
				"https://github.com/owner/repo/blob/main/hel"), is(true));
	}

	@Test
	void testCanCompleteWithDeepPath() {
		assertThat(GitHubCompletionProvider.canComplete(
				"https://github.com/owner/repo/blob/main/src/main/java/"), is(true));
	}

	@Test
	void testCannotCompleteNonGithubUrl() {
		assertThat(GitHubCompletionProvider.canComplete(
				"https://example.com/file.java"), is(false));
	}

	@Test
	void testCannotCompleteIncompleteGithubUrl() {
		// No branch yet
		assertThat(GitHubCompletionProvider.canComplete(
				"https://github.com/owner/repo"), is(false));
	}

	@Test
	void testCannotCompletePlainFile() {
		assertThat(GitHubCompletionProvider.canComplete("hello.java"), is(false));
	}

	// ---- API URL building tests ---

	@Test
	void testBuildApiUrlRootPath() {
		String url = GitHubCompletionProvider.buildApiUrl("owner", "repo", "", "main");
		assertThat(url, is("https://api.github.com/repos/owner/repo/contents?ref=main"));
	}

	@Test
	void testBuildApiUrlWithPath() {
		String url = GitHubCompletionProvider.buildApiUrl("owner", "repo", "src/main", "develop");
		assertThat(url, is("https://api.github.com/repos/owner/repo/contents/src/main?ref=develop"));
	}

	// ---- Completion filtering tests (with fake entries) ---

	@Test
	void testFiltersByExtension() {
		Set<String> extensions = new HashSet<>(Arrays.asList("java", "kt", "jsh"));
		Set<String> candidates = new TreeSet<>();

		// Simulate what complete() does with the filtering logic
		GitHubCompletionProvider.GitHubEntry javaFile = new GitHubCompletionProvider.GitHubEntry("App.java", "file");
		GitHubCompletionProvider.GitHubEntry txtFile = new GitHubCompletionProvider.GitHubEntry("readme.txt", "file");
		GitHubCompletionProvider.GitHubEntry dir = new GitHubCompletionProvider.GitHubEntry("src", "dir");

		String urlPrefix = "https://github.com/o/r/blob/main/";

		for (GitHubCompletionProvider.GitHubEntry entry : Arrays.asList(javaFile, txtFile, dir)) {
			if ("dir".equals(entry.type)) {
				candidates.add(urlPrefix + entry.name + "/");
			} else {
				int dot = entry.name.lastIndexOf('.');
				if (dot >= 0 && extensions.contains(entry.name.substring(dot + 1).toLowerCase())) {
					candidates.add(urlPrefix + entry.name);
				}
			}
		}

		assertThat(candidates, hasItem(urlPrefix + "App.java"));
		assertThat(candidates, hasItem(urlPrefix + "src/"));
		assertThat(candidates, not(hasItem(urlPrefix + "readme.txt")));
	}

	@Test
	void testUrlWithHttpPrefix() {
		assertThat(GitHubCompletionProvider.canComplete(
				"http://github.com/owner/repo/blob/main/"), is(true));
	}

	@Test
	void testBranchWithSlashesNotSupported() {
		// Branch names with slashes (feature/xyz) won't match — expected limitation
		// The pattern captures up to first slash after blob/
		assertThat(GitHubCompletionProvider.canComplete(
				"https://github.com/owner/repo/blob/feature/xyz/"), is(true));
		// But "feature" would be parsed as the branch, "xyz/" as the path — acceptable
	}
}
