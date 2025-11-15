package dev.jbang.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestSearchUtil {

	@TempDir
	Path tempDir;

	private FsBuilder mvnRepo;

	@BeforeEach
	void setUp() {
		mvnRepo = FsBuilder.under(tempDir.resolve("repo"));
	}

	@Test
	void returnsLatestVersionPerArtifact() throws IOException {
		// org.example:demo with multiple versions
		mvnRepo.artifact("org.example", "demo",
				"1.0.0",
				"1.1.0-SNAPSHOT",
				"1.1.0");

		Set<Artifact> result = SearchUtil.localMavenArtifacts(mvnRepo.root());

		assertThat(result).hasSize(1);

		Artifact demo = result.iterator().next();
		assertThat(demo.getGroupId()).isEqualTo("org.example");
		assertThat(demo.getArtifactId()).isEqualTo("demo");
		// GenericVersionScheme should rank 1.1.0 as latest over 1.1.0-SNAPSHOT
		assertThat(demo.getVersion()).isEqualTo("1.1.0");
	}

	@Test
	void findsMultipleArtifactsAcrossGroups() throws IOException {

		mvnRepo
			.artifact("org.example", "demo", "1.0.0")
			.artifact("com.acme.tools", "cli", "0.9.0", "1.0.0");

		Set<Artifact> result = SearchUtil.localMavenArtifacts(mvnRepo.root());

		assertThat(result)
			.extracting(Artifact::toString) // or tuple(groupId, artifactId, version)
			.containsExactlyInAnyOrder(
					"org.example:demo::1.0.0",
					"com.acme.tools:cli::1.0.0");
	}

	@Test
	void returnsEmptySetIfRepoDoesNotExist() {
		Set<Artifact> result = SearchUtil.localMavenArtifacts(mvnRepo.root().resolve("does-not-exist"));

		assertThat(result).isEmpty();
	}
}
