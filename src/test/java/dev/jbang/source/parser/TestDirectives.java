package dev.jbang.source.parser;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.stream.Collectors;

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
	void testExtractConfOptions() {
		Directives tr = new Directives.Extended(
				"//DEPS ch.qos.logback:logback-classic:1.4.14\n" +
						"//CONF logback.file.name=app.log\n" +
						"//CONF logback.file.maxSize=1MB\n" +
						"//CONF spring.http.client.connect-timeout=5s\n" +
						"//CONF spring.http.client.read-timeout=10s",
				null);

		List<String> deps = tr.binaryDependencies();
		assertThat(deps, hasItem("ch.qos.logback:logback-classic:1.4.14"));

		List<KeyValue> confs = tr.confOptions();
		assertThat(confs, hasSize(4));
		List<String> pairs = confs.stream()
			.map(kv -> kv.getKey() + "=" + kv.getValue())
			.collect(Collectors.toList());
		assertThat(pairs, containsInAnyOrder(
				"logback.file.name=app.log",
				"logback.file.maxSize=1MB",
				"spring.http.client.connect-timeout=5s",
				"spring.http.client.read-timeout=10s"));

		List<String> runtime = tr.runtimeOptions();
		assertThat(runtime, containsInAnyOrder(
				"-Dlogback.file.name=app.log",
				"-Dlogback.file.maxSize=1MB",
				"-Dspring.http.client.connect-timeout=5s",
				"-Dspring.http.client.read-timeout=10s"));
	}

	@Test
	void testDuplicateConfKeyPreservesOrderForLastWins() {
		Directives tr = new Directives.Extended(
				"//CONF timeout=30\n//CONF timeout=5000", null);

		List<KeyValue> confs = tr.confOptions();
		assertThat(confs, hasSize(2));

		// both entries survive (no dedup) but must stay in file order so the
		// later -D flag is the one the JVM actually applies last
		List<String> runtime = tr.runtimeOptions();
		assertThat(runtime, contains("-Dtimeout=30", "-Dtimeout=5000"));
	}
}
