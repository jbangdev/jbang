package dev.jbang.source.parser;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import dev.jbang.dependencies.MavenRepo;

public class TestDirectives {

	@Test
	void testExtractDependencies() {
		Directives tr = new Directives.Extended(
				"//DEPS foo:bar, abc:DEF:123, https://github.com/jbangdev/jbang, something", null);

		List<String> deps = tr.binaryDependencies();
		assertThat(deps, hasSize(3));
		assertThat(deps, containsInAnyOrder("foo:bar", "abc:DEF:123", "https://github.com/jbangdev/jbang"));

		List<String> subs = tr.sourceDependencies();
		assertThat(subs, containsInAnyOrder("something"));
	}

	@Test
	void testExtractDependenciesSeparator() {
		Directives tr = new Directives.Extended(
				"//DEPS foo:bar, abc:DEF:123, \thttps://github.com/jbangdev/jbang \tsomething\t ", null);

		List<String> deps = tr.binaryDependencies();
		assertThat(deps, hasSize(3));
		assertThat(deps, containsInAnyOrder("foo:bar", "abc:DEF:123", "https://github.com/jbangdev/jbang"));

		List<String> subs = tr.sourceDependencies();
		assertThat(subs, containsInAnyOrder("something"));
	}

	@Test
	void testExtractDependenciesQuoted() {
		Directives tr = new Directives.Extended(
				"//DEPS abc:DEF:123, 'ch.qos.reload4j:reload4j:[1.2.18,1.2.19)', 'some thing'", null);

		List<String> deps = tr.binaryDependencies();
		assertThat(deps, hasSize(2));
		assertThat(deps, containsInAnyOrder("abc:DEF:123", "ch.qos.reload4j:reload4j:[1.2.18,1.2.19)"));

		List<String> subs = tr.sourceDependencies();
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

	@Test
	void testVarsProgressiveEvaluation() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS a=1", "//VARS b=${a}", "//VARS c=${b}",
						"//DEPS foo:bar:${c}"),
				null)
			.withInheritedProperties(new Properties());

		assertThat(tr.binaryDependencies(), containsInAnyOrder("foo:bar:1"));
	}

	@Test
	void testVarsUseBeforeDefineFails() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//DEPS foo:bar:${a}", "//VARS a=1"), null)
			.withInheritedProperties(new Properties());

		assertThrows(IllegalStateException.class, tr::binaryDependencies);
	}

	@Test
	void testVarsInheritedWins() {
		Properties inherited = new Properties();
		inherited.setProperty("a", "42");
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS a=1", "//DEPS foo:bar:${a}"), null)
			.withInheritedProperties(inherited);

		assertThat(tr.binaryDependencies(), containsInAnyOrder("foo:bar:42"));
	}

	@Test
	void testVarsReassignedLaterKeepsEarlierUses() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS v=1", "//VARS w=${v}", "//VARS v=2",
						"//DEPS foo:bar:${w}", "//DEPS foo:baz:${v}"),
				null)
			.withInheritedProperties(new Properties());

		assertThat(tr.binaryDependencies(), containsInAnyOrder("foo:bar:1", "foo:baz:2"));
	}

	@Test
	void testVarsSeveralPairsOnOneLine() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS a=1 b=2", "//DEPS foo:bar:${a}", "//DEPS foo:baz:${b}"),
				null)
			.withInheritedProperties(new Properties());

		assertThat(tr.binaryDependencies(), containsInAnyOrder("foo:bar:1", "foo:baz:2"));
	}

	@Test
	void testVarsEntryWithoutValueFails() {
		Directives tr = new Directives.Extended("//VARS novalue", null)
			.withInheritedProperties(new Properties());

		assertThrows(IllegalStateException.class, tr::binaryDependencies);
	}

	@Test
	void testScopedSourcesAreStrict() {
		Directives.Extended tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS v=1", "//SOURCES one.java", "//VARS v=2",
						"//VARS w=3", "//SOURCES two.java"),
				null)
			.withInheritedProperties(new Properties());

		List<Directives.ScopedSource> srcs = tr.scopedSources();
		assertThat(srcs, hasSize(2));
		assertThat(srcs.get(0).getValue(), equalTo("one.java"));
		assertThat(srcs.get(0).getScope().getProperty("v"), equalTo("1"));
		assertThat(srcs.get(0).getScope().getProperty("w"), nullValue());
		assertThat(srcs.get(1).getScope().getProperty("v"), equalTo("2"));
		assertThat(srcs.get(1).getScope().getProperty("w"), equalTo("3"));
	}

	@Test
	void testPropsReader() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS port=8080", "//PROPS server.port=${port} a=b",
						"//PROPS 'msg=hello world'"),
				null)
			.withInheritedProperties(new Properties());

		assertThat(tr.props().get("server.port"), equalTo("8080"));
		assertThat(tr.props().get("a"), equalTo("b"));
		assertThat(tr.props().get("msg"), equalTo("hello world"));
	}

	@Test
	void testVarsWithoutInheritedProperties() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS v=1", "//DEPS foo:bar:${v}"), null);

		assertThat(tr.binaryDependencies(), containsInAnyOrder("foo:bar:1"));
	}

	@Test
	void testVarsDefaultEvaluatedWithoutReplacer() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS version=${missing:1}", "//DEPS foo:bar:${version}"), null);

		assertThat(tr.binaryDependencies(), containsInAnyOrder("foo:bar:1"));
	}

	@Test
	void testUnresolvedReferenceKeptWithoutReplacer() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS a=1", "//DESCRIPTION ${x}"), null);

		assertThat(tr.description(), equalTo("${x}"));
	}

	@Test
	void testMixedReferencesKeepResolvedOnes() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS a=1", "//DESCRIPTION ${a} ${missing}"), null);

		assertThat(tr.description(), equalTo("1 ${missing}"));
	}

	@Test
	void testEscapedDollarIsNotResolved() {
		Directives tr = new Directives.Extended(
				String.join(System.lineSeparator(), "//VARS a=1", "//DESCRIPTION $${a} ${a}"), null);

		assertThat(tr.description(), equalTo("$${a} 1"));
	}
}
