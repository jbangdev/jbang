package dev.jbang.dependencies;

import java.io.File;
import java.util.Objects;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

/**
 * class describing artifact coordinates and its resolved physical location.
 */
public class ArtifactInfo {

	private final MavenCoordinate coordinate;
	private final File file;
	private final long timestamp;

	ArtifactInfo(MavenCoordinate coordinate, File file) {
		this.coordinate = coordinate;
		this.file = file;
		this.timestamp = file.exists() ? file.lastModified() : 0;
	}

	ArtifactInfo(MavenCoordinate coordinate, File file, long cachedTimestamp) {
		this.coordinate = coordinate;
		this.file = file;
		this.timestamp = cachedTimestamp;
	}

	public MavenCoordinate getCoordinate() {
		return coordinate;
	}

	public File getFile() {
		return file;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public boolean isUpToDate() {
		return file.canRead() && timestamp == file.lastModified();
	}

	public String toString() {
		String path = getFile().getAbsolutePath();
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
