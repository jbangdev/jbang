package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
}
