package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.dependencies.MavenRepo;

public class TestTagReader {

	@Test
	void testExtractDependencies() {
		List<String> deps = new TagReader("//DEPS blah, blue", null).collectDependencies();

		assertTrue(deps.contains("blah"));

		assertTrue(deps.contains("blue"));

	}

	@Test
	void textExtractRepositories() {
		List<MavenRepo> repos = new TagReader("//REPOS jcenter=https://xyz.org", null).collectRepositories();

		assertThat(repos, hasItem(new MavenRepo("jcenter", "https://xyz.org")));

		repos = new TagReader("//REPOS jcenter=https://xyz.org localMaven xyz=file://~test",
				null).collectRepositories();

		assertThat(repos, hasItem(new MavenRepo("jcenter", "https://xyz.org")));
		assertThat(repos, hasItem(new MavenRepo("localmaven", "localMaven")));
		assertThat(repos, hasItem(new MavenRepo("xyz", "file://~test")));
	}

	@Test
	void textExtractRepositoriesGrape() {
		List<MavenRepo> deps = new TagReader(
				"@GrabResolver(name=\"restlet.org\", root=\"http://maven.restlet.org\")", null)
																								.collectRepositories();

		assertThat(deps, hasItem(new MavenRepo("restlet.org", "http://maven.restlet.org")));

		deps = new TagReader("@GrabResolver(\"http://maven.restlet.org\")", null)
																					.collectRepositories();

		assertThat(deps, hasItem(new MavenRepo("http://maven.restlet.org", "http://maven.restlet.org")));

	}
}
