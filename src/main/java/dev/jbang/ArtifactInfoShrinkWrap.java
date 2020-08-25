package dev.jbang;

import java.io.File;

import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

public class ArtifactInfoShrinkWrap implements ArtifactInfo {

	private final MavenResolvedArtifact it;

	public ArtifactInfoShrinkWrap(MavenResolvedArtifact it) {
		this.it = it;
	}

	@Override
	public MavenCoordinate getCoordinate() {
		return it.getCoordinate();
	}

	@Override
	public File asFile() {
		return it.asFile();
	}

}
