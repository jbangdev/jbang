package dev.jbang.dependencies;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import dev.jbang.util.ModuleUtil;

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

	public boolean isModule() {
		return isModule(file);
	}

	public static boolean isModule(Path file) {
		return ModuleUtil.isModule(file);
	}

	public String getModuleName() {
		return getModuleName(file);
	}

	public static String getModuleName(Path file) {
		return ModuleUtil.getModuleName(file);
	}

	public boolean isUpToDate() {
		// This overly complex test is because some older Java versions seem to return
		// file timestamps with the last three digits set to 0. If we run Jbang on the
		// same script with different JDKs we get continuous Maven resolves because it
		// stores the result with slightly different timestamps. In this way we allow
		// timestamps to be slightly "off" and we'll still assume the artifact to be
		// up-to-date.
		long ts = file.toFile().lastModified();
		return Files.isReadable(file) && (timestamp == ts || (ts % 1000 == 0 && timestamp / 1000 == ts / 1000));
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
