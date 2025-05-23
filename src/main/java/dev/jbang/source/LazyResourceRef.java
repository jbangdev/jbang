package dev.jbang.source;

import java.nio.file.Path;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.util.Util;

public class LazyResourceRef implements ResourceRef {
	@Nonnull
	private final ResourceResolver resolver;
	@Nonnull
	private final String originalResource;
	@Nullable
	private ResourceRef resolvedRef;

	public LazyResourceRef(@Nonnull ResourceResolver resolver, @Nonnull String resource) {
		this.resolver = resolver;
		this.originalResource = resource;
	}

	@Nonnull
	@Override
	public String getOriginalResource() {
		return originalResource;
	}

	@Nullable
	@Override
	public Path getFile() {
		if (resolvedRef == null) {
			resolvedRef = resolver.resolve(getOriginalResource());
			if (resolvedRef == null) {
				throw new LazyResolveException();
			}
		}
		return resolvedRef.getFile();
	}

	@Nonnull
	@Override
	public String getExtension() {
		if (resolvedRef != null) {
			return resolvedRef.getExtension();
		} else {
			return Util.extension(getOriginalResource());
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ResourceRef))
			return false;
		ResourceRef that = (ResourceRef) o;
		if (resolvedRef != null) {
			return resolvedRef.equals(that);
		} else {
			return Objects.equals(getOriginalResource(), that.getOriginalResource());
		}
	}

	@Override
	public int hashCode() {
		if (resolvedRef != null) {
			return resolvedRef.hashCode();
		} else {
			return Objects.hash(getOriginalResource());
		}
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
		if (resolvedRef != null) {
			return resolvedRef.toString();
		} else {
			return getOriginalResource() + " (unresolved)";
		}
	}

	public class LazyResolveException extends RuntimeException {
		public LazyResolveException() {
			super("Unable to lazily resolve resource: " + originalResource);
		}
	}
}
