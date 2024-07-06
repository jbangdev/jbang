package dev.jbang.dependencies;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.aFileWithSize;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;

public class TestArtifactInfo extends BaseTest {

	@Test
	public void testDependencyCache() {
		DependencyCache.clear();

		List<String> deps = Arrays.asList(
				"org.apache.commons:commons-configuration2:2.7",
				"org.apache.commons:commons-text:1.8");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				false,
				true, false);

		DependencyCache.cache("wonka", classpath.getArtifacts());

		MatcherAssert.assertThat(Settings.getCacheDependencyFile().toFile(), aFileWithSize(greaterThan(10L)));

		List<ArtifactInfo> wonka = DependencyCache.findDependenciesByHash("wonka");

		assertThat(wonka, notNullValue());
		assertThat(wonka, hasSize(4));

		assertThat(wonka, contains(classpath.getArtifacts().toArray()));
	}
}
