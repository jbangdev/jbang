package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.dependencies.MavenRepo;

public class TestTagReader {

	@Test
	void testExtractDependencies() {
		TagReader tr = new TagReader.Extended(
				"//DEPS foo:bar, abc:DEF:123, https://github.com/jbangdev/jbang, something", null);

		List<String> deps = tr.collectBinaryDependencies();
		assertThat(deps, containsInAnyOrder("foo:bar", "abc:DEF:123", "https://github.com/jbangdev/jbang"));

		List<String> subs = tr.collectSourceDependencies();
		assertThat(subs, containsInAnyOrder("something"));
	}

	@Test
	void testExtractDependenciesSeparator() {
		TagReader tr = new TagReader.Extended(
				"//DEPS foo:bar, abc:DEF:123, \thttps://github.com/jbangdev/jbang \tsomething\t ", null);

		List<String> deps = tr.collectBinaryDependencies();
		assertThat(deps, containsInAnyOrder("foo:bar", "abc:DEF:123", "https://github.com/jbangdev/jbang"));

		List<String> subs = tr.collectSourceDependencies();
		assertThat(subs, containsInAnyOrder("something"));
	}

	@Test
	void textExtractRepositories() {
		List<MavenRepo> repos = new TagReader.Extended("//REPOS jcenter=https://xyz.org", null).collectRepositories();

		assertThat(repos, hasItem(new MavenRepo("jcenter", "https://xyz.org")));

		repos = new TagReader.Extended("//REPOS jcenter=https://xyz.org localMaven xyz=file://~test",
				null).collectRepositories();

		assertThat(repos, hasItem(new MavenRepo("jcenter", "https://xyz.org")));
		assertThat(repos, hasItem(new MavenRepo("localmaven", "localMaven")));
		assertThat(repos, hasItem(new MavenRepo("xyz", "file://~test")));
	}

	@Test
	void textExtractRepositoriesGrape() {
		List<MavenRepo> deps = new TagReader.Extended(
				"@GrabResolver(name=\"restlet.org\", root=\"http://maven.restlet.org\")", null)
																								.collectRepositories();

		assertThat(deps, hasItem(new MavenRepo("restlet.org", "http://maven.restlet.org")));

		deps = new TagReader.Extended("@GrabResolver(\"http://maven.restlet.org\")", null)
																							.collectRepositories();

		assertThat(deps, hasItem(new MavenRepo("http://maven.restlet.org", "http://maven.restlet.org")));

	}
}
