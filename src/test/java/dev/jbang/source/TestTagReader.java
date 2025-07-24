package dev.jbang.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.dependencies.MavenRepo;

public class TestTagReader {

	@Test
	void testExtractDependencies() {
		TagReader tr = new TagReader.Extended(
				"//DEPS foo:bar, abc:DEF:123, https://github.com/jbangdev/jbang, something", null);

		List<String> deps = tr.collectBinaryDependencies();
		assertThat(deps).containsExactlyInAnyOrder("foo:bar", "abc:DEF:123", "https://github.com/jbangdev/jbang");

		List<String> subs = tr.collectSourceDependencies();
		assertThat(subs).containsExactlyInAnyOrder("something");
	}

	@Test
	void testExtractDependenciesSeparator() {
		TagReader tr = new TagReader.Extended(
				"//DEPS foo:bar, abc:DEF:123, \thttps://github.com/jbangdev/jbang \tsomething\t ", null);

		List<String> deps = tr.collectBinaryDependencies();
		assertThat(deps).containsExactlyInAnyOrder("foo:bar", "abc:DEF:123", "https://github.com/jbangdev/jbang");

		List<String> subs = tr.collectSourceDependencies();
		assertThat(subs).containsExactlyInAnyOrder("something");
	}

	@Test
	void textExtractRepositories() {
		List<MavenRepo> repos = new TagReader.Extended("//REPOS jcenter=https://xyz.org", null).collectRepositories();

		assertThat(repos).contains(new MavenRepo("jcenter", "https://xyz.org"));

		repos = new TagReader.Extended("//REPOS jcenter=https://xyz.org localMaven xyz=file://~test",
				null)
			.collectRepositories();

		assertThat(repos).contains(new MavenRepo("jcenter", "https://xyz.org"));
		assertThat(repos).contains(new MavenRepo("localmaven", "localMaven"));
		assertThat(repos).contains(new MavenRepo("xyz", "file://~test"));
	}

	@Test
	void textExtractRepositoriesGrape() {
		List<MavenRepo> deps = new TagReader.Extended(
				"@GrabResolver(name=\"restlet.org\", root=\"http://maven.restlet.org\")", null)
			.collectRepositories();

		assertThat(deps).contains(new MavenRepo("restlet.org", "http://maven.restlet.org"));

		deps = new TagReader.Extended("@GrabResolver(\"http://maven.restlet.org\")", null)
			.collectRepositories();

		assertThat(deps).contains(new MavenRepo("http://maven.restlet.org", "http://maven.restlet.org"));

	}
}
