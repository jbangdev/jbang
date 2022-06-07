package dev.jbang.source;

import java.nio.file.Path;
import java.util.Objects;

import javax.annotation.Nullable;

import dev.jbang.util.Util;

public class ResourceRef implements Comparable<ResourceRef> {
	// original requested resource
	@Nullable
	private final String originalResource;
	// cache folder it is stored inside
	@Nullable
	private final Path file;

	public static final ResourceRef nullRef = new ResourceRef(null, null);

	protected ResourceRef(@Nullable String ref, @Nullable Path file) {
		this.originalResource = ref;
		this.file = file;
	}

	public boolean isURL() {
		return originalResource != null && Util.isURL(originalResource);
	}

	public boolean isClasspath() {
		return originalResource != null && Util.isClassPathRef(originalResource);
	}

	public boolean isStdin() {
		return originalResource != null && isStdin(originalResource);
	}

	@Nullable
	public Path getFile() {
		return file;
	}

	@Nullable
	public String getOriginalResource() {
		return originalResource;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ResourceRef that = (ResourceRef) o;
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
			if (originalResource.equals(file)) {
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

	public static ResourceRef forFile(Path file) {
		return new ResourceRef(file.toString(), file);
	}

	public static ResourceRef forNamedFile(String resource, Path file) {
		return new ResourceRef(resource, file);
	}

	public static ResourceRef forCachedResource(String resource, Path cachedResource) {
		return new ResourceRef(resource, cachedResource);
	}

	public static ResourceRef forResource(String resource) {
		return ResourceResolver.forResources().resolve(resource);
	}

	public static boolean isStdin(String scriptResource) {
		return scriptResource.equals("-") || scriptResource.equals("/dev/stdin");
	}
}
