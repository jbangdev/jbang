package dev.jbang.source.parser;

import static dev.jbang.dependencies.DependencyUtil.filterGavDeps;
import static dev.jbang.dependencies.DependencyUtil.filterJarDeps;
import static dev.jbang.dependencies.DependencyUtil.filterSourceDeps;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.dependencies.MavenRepo;

public class TestDirectives {

	@Test
	void testExtractDependencies() {
		Directives tr = new Directives.Extended(
				"//DEPS foo:bar, abc:DEF:123, https://github.com/jbangdev/jbang, local.jar, something", null);

		List<String> deps1 = filterGavDeps(tr.dependencies());
		assertThat(deps1, hasSize(3));
		assertThat(deps1, containsInAnyOrder("foo:bar", "abc:DEF:123", "https://github.com/jbangdev/jbang"));

		List<String> deps2 = filterJarDeps(tr.dependencies());
		assertThat(deps2, hasSize(1));
		assertThat(deps2, containsInAnyOrder("local.jar"));

		List<String> subs = filterSourceDeps(tr.dependencies());
		assertThat(subs, containsInAnyOrder("something"));
	}

	@Test
	void testExtractDependenciesSeparator() {
		Directives tr = new Directives.Extended(
				"//DEPS foo:bar, abc:DEF:123, \thttps://github.com/jbangdev/jbang \tsomething\t ", null);

		List<String> deps = filterGavDeps(tr.dependencies());
		assertThat(deps, hasSize(3));
		assertThat(deps, containsInAnyOrder("foo:bar", "abc:DEF:123", "https://github.com/jbangdev/jbang"));

		List<String> subs = filterSourceDeps(tr.dependencies());
		assertThat(subs, containsInAnyOrder("something"));
	}

	@Test
	void testExtractDependenciesQuoted() {
		Directives tr = new Directives.Extended(
				"//DEPS abc:DEF:123, 'ch.qos.reload4j:reload4j:[1.2.18,1.2.19)', 'some thing'", null);

		List<String> deps = filterGavDeps(tr.dependencies());
		assertThat(deps, hasSize(2));
		assertThat(deps, containsInAnyOrder("abc:DEF:123", "ch.qos.reload4j:reload4j:[1.2.18,1.2.19)"));

		List<String> subs = filterSourceDeps(tr.dependencies());
		assertThat(subs, containsInAnyOrder("some thing"));
	}

	@Test
	void textExtractRepositories() {
		List<MavenRepo> repos = new Directives.Extended("//REPOS jcenter=https://xyz.org", null).repositories();

		assertThat(repos, hasItem(new MavenRepo("jcenter", "https://xyz.org")));

		repos = new Directives.Extended("//REPOS jcenter=https://xyz.org localMaven xyz=file://~test",
				null)
			.repositories();

		assertThat(repos, hasItem(new MavenRepo("jcenter", "https://xyz.org")));
		assertThat(repos, hasItem(new MavenRepo("localmaven", "localMaven")));
		assertThat(repos, hasItem(new MavenRepo("xyz", "file://~test")));
	}

	@Test
	void textExtractRepositoriesGrape() {
		List<MavenRepo> deps = new Directives.Extended(
				"@GrabResolver(name=\"restlet.org\", root=\"http://maven.restlet.org\")", null)
			.repositories();

		assertThat(deps, hasItem(new MavenRepo("restlet.org", "http://maven.restlet.org")));

		deps = new Directives.Extended("@GrabResolver(\"http://maven.restlet.org\")", null)
			.repositories();

		assertThat(deps, hasItem(new MavenRepo("http://maven.restlet.org", "http://maven.restlet.org")));

	}
}
