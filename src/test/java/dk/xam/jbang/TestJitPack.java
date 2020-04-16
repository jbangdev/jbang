package dk.xam.jbang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TestJitPack {

	@Test
	void testExtractGithubUrlDependencies() {
		assertEquals(JitPackUtil.ensureGAV("https://github.com/maxandersen/jbang"),
				"com.github.maxandersen:jbang:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://github.com/maxandersen/jbang/tree/master"),
				"com.github.maxandersen:jbang:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://github.com/maxandersen/jbang/tree/master/foo"),
				"com.github.maxandersen.jbang:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://github.com/maxandersen/jbang/tree/v0.20.0"),
				"com.github.maxandersen:jbang:v0.20.0");

		assertEquals(
				JitPackUtil.ensureGAV(
						"https://github.com/maxandersen/jbang/commit/90dfcbf354fc0838f08eea0680bf736b7c069b4e"),
				"com.github.maxandersen:jbang:90dfcbf354");

	}

	@Test
	void testExtractGithubUrlWithHashDependencies() {
		assertEquals(JitPackUtil.ensureGAV("https://github.com/maxandersen/jbang#foo"),
				"com.github.maxandersen.jbang:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://github.com/maxandersen/jbang/tree/master#foo"),
				"com.github.maxandersen.jbang:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://github.com/maxandersen/jbang/tree/master/foo#bar"),
				"com.github.maxandersen.jbang:bar:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://github.com/maxandersen/jbang/tree/somebranch#foo:SNAPSHOT"),
				"com.github.maxandersen.jbang:foo:somebranch-SNAPSHOT");

		assertEquals(
				JitPackUtil.ensureGAV(
						"https://github.com/maxandersen/jbang/commit/90dfcbf354fc0838f08eea0680bf736b7c069b4e#foo"),
				"com.github.maxandersen.jbang:foo:90dfcbf354");

	}

	@Test
	void testExtractGitlabUrlDependencies() {
		assertEquals(JitPackUtil.ensureGAV("https://gitlab.com/gitlab-org/gitlab"),
				"com.gitlab.gitlab-org:gitlab:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/master"),
				"com.gitlab.gitlab-org:gitlab:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/master/foo"),
				"com.gitlab.gitlab-org.gitlab:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/v12.7.9-ee"),
				"com.gitlab.gitlab-org:gitlab:v12.7.9-ee");

		assertEquals(
				JitPackUtil.ensureGAV(
						"https://gitlab.com/gitlab-org/gitlab/-/commit/120262d85822e6a3d4e04f5c84d0075c60309d97"),
				"com.gitlab.gitlab-org:gitlab:120262d858");

	}

	@Test
	void testExtractGitlabUrlWithHashDependencies() {
		assertEquals(JitPackUtil.ensureGAV("https://gitlab.com/gitlab-org/gitlab#foo"),
				"com.gitlab.gitlab-org.gitlab:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/master#foo"),
				"com.gitlab.gitlab-org.gitlab:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/master/foo#bar"),
				"com.gitlab.gitlab-org.gitlab:bar:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://gitlab.com/gitlab-org/gitlab/-/tree/somebranch#foo:SNAPSHOT"),
				"com.gitlab.gitlab-org.gitlab:foo:somebranch-SNAPSHOT");

		assertEquals(
				JitPackUtil.ensureGAV(
						"https://gitlab.com/gitlab-org/gitlab/-/commit/120262d85822e6a3d4e04f5c84d0075c60309d97#foo"),
				"com.gitlab.gitlab-org.gitlab:foo:120262d858");

	}

	@Test
	void testExtractBitbucketUrlDependencies() {
		assertEquals(JitPackUtil.ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler"),
				"org.bitbucket.ceylon:ceylon-compiler:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/master/"),
				"org.bitbucket.ceylon:ceylon-compiler:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/master/foo/"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/0.4/"),
				"org.bitbucket.ceylon:ceylon-compiler:0.4");

		assertEquals(JitPackUtil.ensureGAV(
				"https://bitbucket.org/ceylon/ceylon-compiler/commits/9a5e4667af5ae03e036dff1294b81b653be6dffc"),
				"org.bitbucket.ceylon:ceylon-compiler:9a5e4667af");

	}

	@Test
	void testExtractBitbucketUrlWithHashDependencies() {
		assertEquals(JitPackUtil.ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler#foo"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/master/#foo"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/master/foo/#bar"),
				"org.bitbucket.ceylon.ceylon-compiler:bar:master-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV("https://bitbucket.org/ceylon/ceylon-compiler/src/somebranch/#foo:SNAPSHOT"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:somebranch-SNAPSHOT");

		assertEquals(JitPackUtil.ensureGAV(
				"https://bitbucket.org/ceylon/ceylon-compiler/commits/9a5e4667af5ae03e036dff1294b81b653be6dffc#foo"),
				"org.bitbucket.ceylon.ceylon-compiler:foo:9a5e4667af");

	}
}
