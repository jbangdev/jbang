package dev.jbang.dependencies;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.aFileWithSize;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.Settings;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtimes;

public class TestArtifactInfo extends BaseTest {

	private Context ctx;

	public Context createCtx(File localRepo) {
		return ctx = Runtimes.INSTANCE
										.getRuntime()
										.create(ContextOverrides.Builder.create()
																		.localRepository(localRepo.toPath())
																		.build());
	}

	@AfterEach
	public void mayCloseCtx() {
		if (ctx != null) {
			ctx.close();
			ctx = null;
		}
	}

	@Test
	public void testDependencyCache(@TempDir File lr) {
		DependencyCache.clear();

		List<String> deps = Arrays.asList(
				"org.apache.commons:commons-configuration2:2.7",
				"org.apache.commons:commons-text:1.8");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(createCtx(lr), deps, Collections.emptyList(),
				false, false);

		DependencyCache.cache("wonka", classpath.getArtifacts());

		MatcherAssert.assertThat(Settings.getCacheDependencyFile().toFile(), aFileWithSize(greaterThan(10L)));

		List<ArtifactInfo> wonka = DependencyCache.findDependenciesByHash("wonka");

		assertThat(wonka, notNullValue());
		assertThat(wonka, hasSize(4));

		assertThat(wonka, contains(classpath.getArtifacts().toArray()));
	}
}
