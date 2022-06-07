package dev.jbang.dependencies;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

/**
 * class describing artifact coordinates and its resolved physical location.
 */
public class ArtifactInfo {

	private final MavenCoordinate coordinate;
	private final Path file;
	private final long timestamp;

	ArtifactInfo(MavenCoordinate coordinate, Path file) {
		this.coordinate = coordinate;
		this.file = file;
		this.timestamp = Files.exists(file) ? file.toFile().lastModified() : 0;
	}

	ArtifactInfo(MavenCoordinate coordinate, Path file, long cachedTimestamp) {
		this.coordinate = coordinate;
		this.file = file;
		this.timestamp = cachedTimestamp;
	}

	public MavenCoordinate getCoordinate() {
		return coordinate;
	}

	public Path getFile() {
		return file;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public boolean isUpToDate() {
		return Files.isReadable(file) && timestamp == file.toFile().lastModified();
	}

	public String toString() {
		String path = getFile().toAbsolutePath().toString();
		return getCoordinate() == null ? "<null>" : getCoordinate().toCanonicalForm() + "=" + path;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ArtifactInfo that = (ArtifactInfo) o;
		return Objects.equals(coordinate, that.coordinate) &&
				Objects.equals(file, that.file);
	}

	@Override
	public int hashCode() {
		return Objects.hash(coordinate, file);
	}
}
