package dev.jbang.source;

import java.nio.file.Path;
import java.util.Objects;

import javax.annotation.Nullable;

public class DirectResourceRef implements ResourceRef {
	@Nullable
	private final String originalResource;
	@Nullable
	private final Path file;

	protected DirectResourceRef(@Nullable String ref, @Nullable Path file) {
		this.originalResource = ref;
		this.file = file;
	}

	@Nullable
	@Override
	public String getOriginalResource() {
		return originalResource;
	}

	@Nullable
	@Override
	public Path getFile() {
		return file;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DirectResourceRef that = (DirectResourceRef) o;
		return Objects.equals(originalResource, that.originalResource) &&
				Objects.equals(file, that.file);
	}

	@Override
	public int hashCode() {
		return Objects.hash(originalResource, file);
	}

	@Override
	public int compareTo(ResourceRef o) {
		if (o == null) {
			return 1;
		}
		return toString().compareTo(o.toString());
	}

	@Override
	public String toString() {
		if (originalResource != null && file != null) {
			if (originalResource.equals(file.toString())) {
				return originalResource;
			} else {
				return originalResource + " (cached as: " + file + ")";
			}
		} else {
			String res = "";
			if (originalResource != null) {
				res += originalResource;
			}
			if (file != null) {
				res += file;
			}
			return res;
		}
	}

}
