package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.aFileWithSize;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TestArtifactInfo {

	@Test
	public void testArtifactFromExternalString() {

		ArtifactInfo info = ArtifactInfo.fromExternalString("g:a:23.4=/here/is/a/path");

		assertThat(info.asFile().getPath(), equalTo("/here/is/a/path"));
		assertThat(info.getCoordinate().getGroupId(), equalTo("g"));
		assertThat(info.getCoordinate().getArtifactId(), equalTo("a"));
		assertThat(info.getCoordinate().getVersion(), equalTo("23.4"));

	}

	@Test
	public void testDependencyCache() {

		Settings.clearDependencyCache();

		List<String> deps = Arrays.asList(
				"org.apache.commons:commons-configuration2:2.7",
				"org.apache.commons:commons-text:1.8");

		DependencyUtil dr = new DependencyUtil();
		ModularClassPath classpath = dr.resolveDependencies(deps, Collections.emptyList(), false, true);

		Settings.cacheDependencies("wonka", classpath.getArtifacts());

		assertThat(Settings.getCacheDependencyFile().toFile(), aFileWithSize(greaterThan(10L)));

		List<ArtifactInfo> wonka = Settings.findDependenciesInCache("wonka");

		assertThat(wonka, notNullValue());
		assertThat(wonka, hasSize(6));

		assertThat(wonka, contains(classpath.getArtifacts().toArray()));

	}
}
