package dev.jbang.dependencies;

import static dev.jbang.dependencies.JitPackUtil.ensureGAV;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class TestJitPack extends BaseTest {

	@Test
	void testExtractGithubUrlDependencies() {
		assertEquals(ensureGAV("https://github.com/jbangdev/jbang"),
				"com.github.jbangdev:jbang:main-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/main"),
				"com.github.jbangdev:jbang:main-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/master"),
				"com.github.jbangdev:jbang:master-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/master/foo"),
				"com.github.jbangdev.jbang:foo:master-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/v0.20.0"),
				"com.github.jbangdev:jbang:v0.20.0");

		assertEquals(
				ensureGAV(
						"https://github.com/jbangdev/jbang/commit/90dfcbf354fc0838f08eea0680bf736b7c069b4e"),
				"com.github.jbangdev:jbang:90dfcbf354");

	}

	@Test
	void testDocumentedLocators() {
		assertEquals(ensureGAV("https://github.com/jbangdev/jbang"),
				"com.github.jbangdev:jbang:main-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/v1.2.3"),
				"com.github.jbangdev:jbang:v1.2.3");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/f1f34b031d2163e0cdc6f9a3725b59f47129c923"),
				"com.github.jbangdev:jbang:f1f34b031d");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang#mymodule"),
				"com.github.jbangdev.jbang:mymodule:main-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/mybranch#:SNAPSHOT"),
				"com.github.jbangdev:jbang:mybranch-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/mybranch#mymodule:SNAPSHOT"),
				"com.github.jbangdev.jbang:mymodule:mybranch-SNAPSHOT");
	}

	@Test
	void testExtractGithubUrlWithHashDependencies() {
		assertEquals(ensureGAV("https://github.com/jbangdev/jbang#foo"),
				"com.github.jbangdev.jbang:foo:main-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/master#foo"),
				"com.github.jbangdev.jbang:foo:master-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/master#:"),
				"com.github.jbangdev:jbang:master");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/master#foo:"),
				"com.github.jbangdev.jbang:foo:master");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/master/foo#bar"),
				"com.github.jbangdev.jbang:bar:master-SNAPSHOT");

		assertEquals(ensureGAV("https://github.com/jbangdev/jbang/tree/somebranch#foo:SNAPSHOT"),
				"com.github.jbangdev.jbang:foo:somebranch-SNAPSHOT");

		assertEquals("com.github.jbangdev:jbang:somebranch",
				ensureGAV("https://github.com/jbangdev/jbang/tree/somebranch"));

		assertEquals("com.github.jbangdev.jbang:artid:somebranch",
				ensureGAV("https://github.com/jbangdev/jbang/tree/somebranch#artid"));

		assertEquals("com.github.jbangdev:jbang:somebranch-SNAPSHOT",
				ensureGAV("https://github.com/jbangdev/jbang/tree/somebranch#:SNAPSHOT"));

		assertEquals("com.github.jbangdev.jbang:artid:somebranch-SNAPSHOT",
				ensureGAV("https://github.com/jbangdev/jbang/tree/somebranch#artid:SNAPSHOT"));

		assertEquals(
				ensureGAV(
						"https://github.com/jbangdev/jbang/commit/90dfcbf354fc0838f08eea0680bf736b7c069b4e#foo"),
				"com.github.jbangdev.jbang:foo:90dfcbf354");

	}

	@Test
	void testExtractGitlabUrlDependencies() {
		assertEquals(ensureGAV("https://gitlab.com/gitlab-org/gitlab"),
				"com.gitlab.gitlab-org:gitlab:main-SNAPSHOT");

		assertEquals(ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/master"),
				"com.gitlab.gitlab-org:gitlab:master-SNAPSHOT");

		assertEquals(ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/master/foo"),
				"com.gitlab.gitlab-org.gitlab:foo:master-SNAPSHOT");

		assertEquals(ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/v12.7.9-ee"),
				"com.gitlab.gitlab-org:gitlab:v12.7.9-ee");

		assertEquals(
				ensureGAV(
						"https://gitlab.com/gitlab-org/gitlab/-/commit/120262d85822e6a3d4e04f5c84d0075c60309d97"),
				"com.gitlab.gitlab-org:gitlab:120262d858");

	}

	@Test
	void testExtractGitlabUrlWithHashDependencies() {
		assertEquals(ensureGAV("https://gitlab.com/gitlab-org/gitlab#foo"),
				"com.gitlab.gitlab-org.gitlab:foo:main-SNAPSHOT");

		assertEquals(ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/master#foo"),
				"com.gitlab.gitlab-org.gitlab:foo:master-SNAPSHOT");

		assertEquals(ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/master/foo#bar"),
				"com.gitlab.gitlab-org.gitlab:bar:master-SNAPSHOT");

		assertEquals(ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/somebranch#foo:SNAPSHOT"),
				"com.gitlab.gitlab-org.gitlab:foo:somebranch-SNAPSHOT");

		assertEquals(
				ensureGAV(
						"https://gitlab.com/gitlab-org/gitlab/-/commit/120262d85822e6a3d4e04f5c84d0075c60309d97#foo"),
				"com.gitlab.gitlab-org.gitlab:foo:120262d858");

	}

	@Test
	void testExtractBitbucketUrlDependencies() {
		assertEquals(ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler"),
				"org.bitbucket.ceylon:ceylon-compiler:main-SNAPSHOT");

		assertEquals(ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/master/"),
				"org.bitbucket.ceylon:ceylon-compiler:master-SNAPSHOT");

		assertEquals(ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/master/foo/"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:master-SNAPSHOT");

		assertEquals(ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/0.4/"),
				"org.bitbucket.ceylon:ceylon-compiler:0.4");

		assertEquals(ensureGAV(
				"https://bitbucket.org/ceylon/ceylon-compiler/commits/9a5e4667af5ae03e036dff1294b81b653be6dffc"),
				"org.bitbucket.ceylon:ceylon-compiler:9a5e4667af");

	}

	@Test
	void testExtractBitbucketUrlWithHashDependencies() {
		assertEquals(ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler#foo"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:main-SNAPSHOT");

		assertEquals(ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/master/#foo"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:master-SNAPSHOT");

		assertEquals(ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/master/foo/#bar"),
				"org.bitbucket.ceylon.ceylon-compiler:bar:master-SNAPSHOT");

		assertEquals(ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/somebranch/#foo:SNAPSHOT"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:somebranch-SNAPSHOT");

		assertEquals(ensureGAV(
				"https://bitbucket.org/ceylon/ceylon-compiler/commits/9a5e4667af5ae03e036dff1294b81b653be6dffc#foo"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:9a5e4667af");

	}
}
