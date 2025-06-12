package dev.jbang.source.resolvers;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.source.ResourceNotFoundException;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a path to a file on the local file system, will return a reference
 * to that file.
 */
public class FileResourceResolver implements ResourceResolver {

	@Nonnull
	@Override
	public String description() {
		return "File Resource resolver";
	}

	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;

		Path probe = null;
		try {
			probe = Util.getCwd().resolve(resource).normalize();
		} catch (InvalidPathException e) {
			// Ignore
		}

		if (probe != null && Files.isReadable(probe)) {
			result = ResourceRef.forResolvedResource(resource, probe);
		}

		return result;
	}

	public static class FileResourceRef implements ResourceRef {
		@Nonnull
		private final String originalResource;
		@Nonnull
		private final Function<String, Path> obtainer;
		@Nullable
		private Optional<Path> file;

		public FileResourceRef(@Nonnull String resource, @Nonnull Function<String, Path> obtainer) {
			this.originalResource = resource;
			this.obtainer = obtainer;
		}

		public FileResourceRef(@Nonnull String resource, @Nonnull Path file) {
			this.originalResource = resource;
			this.obtainer = ref -> file;
			this.file = Optional.of(file);
		}

		@Nonnull
		@Override
		public String getOriginalResource() {
			return originalResource;
		}

		@Override
		public boolean exists() {
			try {
				return Files.isRegularFile(getFile());
			} catch (ResourceNotFoundException e) {
				return false;
			}
		}

		@Nonnull
		@Override
		public Path getFile() {
			if (file == null) {
				file = Optional.ofNullable(obtainer.apply(getOriginalResource()));
			}
			if (!file.isPresent()) {
				throw new ResourceNotFoundException(getOriginalResource(), "Could not obtain file resource");
			}
			return file.get();
		}

		@Nonnull
		@Override
		public String getExtension() {
			if (file == null || file.isPresent()) {
				return Util.extension(getFile().toString());
			} else {
				return ResourceRef.super.getExtension();
			}
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ResourceRef))
				return false;
			ResourceRef that = (ResourceRef) o;
			return Objects.equals(getOriginalResource(), that.getOriginalResource());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getOriginalResource());
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
			if (file != null) {
				if (!file.isPresent() || getOriginalResource().equals(file.get().toString())) {
					return getOriginalResource();
				} else {
					return getOriginalResource() + " (" + file.get() + ")";
				}
			} else {
				return getOriginalResource() + " (unresolved)";
			}
		}
	}
}
