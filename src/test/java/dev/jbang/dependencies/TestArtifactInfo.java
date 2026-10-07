package dev.jbang.dependencies;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.aFileWithSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.util.Util;

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

	@Test
	public void testDependencyCacheWithSources() throws IOException {
		DependencyCache.clear();

		Path jar1 = Files.createFile(jbangTempDir.resolve("test.jar"));
		Path src1 = Files.createFile(jbangTempDir.resolve("test-sources.jar"));
		Path jar2 = Files.createFile(jbangTempDir.resolve("nosrc.jar"));

		MavenCoordinate coord = new MavenCoordinate("org.example", "test", "1.0", null, "jar");
		ArtifactInfo ai1 = new ArtifactInfo(coord, jar1, src1, true);
		ArtifactInfo ai2 = new ArtifactInfo(new MavenCoordinate("org.example", "nosrc", "1.0", null, "jar"),
				jar2, null, true);

		DependencyCache.cache("sources-test", Arrays.asList(ai1, ai2));

		String content = Util.readFileContent(Settings.getCacheDependencyFile());
		assertThat(content, containsString("\"sources-file\":"));
		assertThat(content, containsString("test-sources.jar"));
		assertThat(content, containsString("\"sources-checked\": true"));
		assertThat(content, not(containsString("sourceFile")));
		assertThat(content, not(containsString("sourcesChecked")));

		DependencyCache.clear();
		List<ArtifactInfo> cached = DependencyCache.findDependenciesByHash("sources-test");
		assertThat(cached, notNullValue());
		assertThat(cached, hasSize(2));
		assertThat(cached.get(0).getSourceFile(), equalTo(src1));
		assertThat(cached.get(0).isSourcesChecked(), equalTo(true));
		assertThat(cached.get(1).getSourceFile(), is(nullValue()));
		assertThat(cached.get(1).isSourcesChecked(), equalTo(true));
	}
}
