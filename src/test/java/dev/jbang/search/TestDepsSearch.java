package dev.jbang.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.jbang.BaseTest;

public class TestDepsSearch extends BaseTest {
	@ParameterizedTest
	@EnumSource(ArtifactSearch.Backends.class)
	void testSearchSingleTerm(ArtifactSearch.Backends backend) throws IOException {
		ArtifactSearch s = ArtifactSearch.getBackend(backend);
		ArtifactSearch.SearchResult res = s.findArtifacts("httpclient", 10);
		assertThat(res.count).isGreaterThan(1);
		assertThat(res.artifacts).isNotEmpty();
		res = s.findNextArtifacts(res);
		assertThat(res.count).isGreaterThan(1);
		assertThat(res.artifacts).isNotEmpty();
	}

	@ParameterizedTest
	@EnumSource(ArtifactSearch.Backends.class)
	void testSearchDoubleTerm(ArtifactSearch.Backends backend) throws IOException {
		ArtifactSearch s = ArtifactSearch.getBackend(backend);
		ArtifactSearch.SearchResult res = s.findArtifacts("apache:httpclient", 10);
		assertThat(res.count).isGreaterThan(1);
		assertThat(res.artifacts).isNotEmpty();
		res = s.findNextArtifacts(res);
		assertThat(res.count).isGreaterThan(1);
		assertThat(res.artifacts).isNotEmpty();
	}

	@ParameterizedTest
	@EnumSource(ArtifactSearch.Backends.class)
	void testSearchTripleTerm(ArtifactSearch.Backends backend) throws IOException {
		ArtifactSearch s = ArtifactSearch.getBackend(backend);
		ArtifactSearch.SearchResult res = s.findArtifacts("org.apache.httpcomponents:httpclient:", 10);
		assertThat(res.count).isGreaterThan(1);
		assertThat(res.artifacts).isNotEmpty();
		assertThat(res.artifacts).allMatch(a -> "org.apache.httpcomponents".equals(a.getGroupId()));
		assertThat(res.artifacts).allMatch(a -> "httpclient".equals(a.getArtifactId()));
		res = s.findNextArtifacts(res);
		assertThat(res.count).isGreaterThan(1);
		assertThat(res.artifacts).isNotEmpty();
		assertThat(res.artifacts).allMatch(a -> "org.apache.httpcomponents".equals(a.getGroupId()));
		assertThat(res.artifacts).allMatch(a -> "httpclient".equals(a.getArtifactId()));
	}

	@ParameterizedTest
	@EnumSource(ArtifactSearch.Backends.class)
	void testFullclassName(ArtifactSearch.Backends backend) throws IOException {

		ArtifactSearch s = ArtifactSearch.getBackend(backend);
		ArtifactSearch.SearchResult res = s.findArtifacts("fc:dev.jbang.Main", 10);
		assertThat(res.count).isGreaterThan(1);
		assertThat(res.artifacts).isNotEmpty();

		Map<String, List<Artifact>> artifactsByGav = new HashMap<>();
		collectByGav(res, artifactsByGav);

		res = s.findNextArtifacts(res);

		collectByGav(res, artifactsByGav);

		assertThat(res.count).isGreaterThan(1);
		assertThat(artifactsByGav).containsKeys("dev.jbang:jbang-cli");

		assertThat(res.artifacts).isNotEmpty();

	}

	private void collectByGav(ArtifactSearch.SearchResult res, Map<String, List<Artifact>> artifactsByGav) {
		for (Artifact artifact : res.artifacts) {
			artifactsByGav.computeIfAbsent(
					artifact.getGroupId() + ":" + artifact.getArtifactId(),
					k -> {
						List<Artifact> l = new ArrayList<Artifact>();
						l.add(artifact);
						return l;
					});
		}
	}
}
