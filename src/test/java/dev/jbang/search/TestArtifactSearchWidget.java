package dev.jbang.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;

class TestArtifactSearchWidget {

	@Test
	void shouldDeferLoadingLocalArtifacts() {
		AtomicBoolean loaderCalled = new AtomicBoolean();

		new ArtifactSearchWidget(() -> {
			loaderCalled.set(true);
			return new HashSet<>();
		});

		assertThat(loaderCalled).isFalse();
	}

	@Test
	void shouldExcludeParentArtifactsWhenLoading() {
		Artifact application = new DefaultArtifact("org.example:application:1.0");
		Artifact parent = new DefaultArtifact("org.example:application-parent:1.0");
		Set<Artifact> artifacts = new HashSet<>();
		artifacts.add(application);
		artifacts.add(parent);
		ArtifactSearchWidget widget = new ArtifactSearchWidget(() -> artifacts);

		Set<Artifact> loaded = widget.loadLocalArtifacts();

		assertThat(loaded).containsExactly(application);
	}
}
