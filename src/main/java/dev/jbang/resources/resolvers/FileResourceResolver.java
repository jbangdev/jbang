package dev.jbang.resources.resolvers;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.resources.ResourceNotFoundException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a path to a file on the local file system, will return a reference
 * to that file.
 */
public class FileResourceResolver implements ResourceResolver {

	@NonNull
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
			result = new FileResourceRef(resource, probe, this);
		}

		return result;
	}

	public static class FileResourceRef implements ResourceRef {
		@NonNull
		protected final String originalResource;
		@NonNull
		protected final Function<String, Path> obtainer;
		@NonNull
		protected final ResourceResolver resolver;

		@Nullable
		private Optional<Path> file;

		public FileResourceRef(@NonNull String resource, @NonNull Function<String, Path> obtainer,
				@Nullable ResourceResolver resolver) {
			this.originalResource = resource;
			this.obtainer = obtainer;
			this.resolver = resolver != null ? resolver : new NullResourceResolver();
		}

		public FileResourceRef(@NonNull String resource, @NonNull Path file, @Nullable ResourceResolver resolver) {
			this.originalResource = resource;
			this.obtainer = ref -> file;
			this.resolver = resolver != null ? resolver : new NullResourceResolver();
			this.file = Optional.of(file);
		}

		@NonNull
		@Override
		public String getOriginalResource() {
			return originalResource;
		}

		@Override
		public boolean exists() {
			try {
				return Files.exists(getFile());
			} catch (ResourceNotFoundException e) {
				return false;
			}
		}

		@NonNull
		@Override
		public Path getFile() {
			if (file == null) {
				file = Optional.ofNullable(obtainer.apply(getOriginalResource()));
			}
			if (!file.isPresent()) {
				throw new ResourceNotFoundException(getOriginalResource(), "Could not obtain file resource");
			}
			if (isParent()) {
				throw new ResourceNotFoundException(getOriginalResource(), "Resource is a parent");
			}
			return file.get();
		}

		@NonNull
		@Override
		public String getExtension() {
			if (file == null || file.isPresent()) {
				return Util.extension(getFile().toString());
			} else {
				return ResourceRef.super.getExtension();
			}
		}

		@Override
		public ResourceRef parent() {
			Path parentPath = Paths.get(originalResource).getParent();
			return new FileResourceRef(parentPath.toString(), parentPath, resolver);
		}

		@Override
		public boolean isParent() {
			if (file == null) {
				file = Optional.ofNullable(obtainer.apply(getOriginalResource()));
			}
			return file.isPresent() && Files.isDirectory(file.get());
		}

		@Override
		public ResourceChildren children() {
			if (!isParent()) {
				throw new ResourceNotFoundException(getOriginalResource(), "Resource is not a parent");
			}
			return new ResourceChildren() {
				@Override
				public Stream<Path> list() throws IOException {
					if (file == null) {
						file = Optional.ofNullable(obtainer.apply(getOriginalResource()));
					}
					return file.isPresent() ? Files.walk(file.get(), FileVisitOption.FOLLOW_LINKS) : Stream.empty();
				}

				@Override
				public @Nullable ResourceRef resolve(String resource, boolean trusted) {
					if (!Util.isValidPath(resource)) {
						return null;
					}
					Path baseDir = Paths.get(originalResource);
					String childRef = baseDir.resolve(resource).toString();
					return resolver.resolve(childRef, trusted);
				}

				@Override
				public @NonNull String description() {
					return "Children of " + FileResourceRef.this.description();
				}
			};
		}

		@Override
		public @Nullable ResourceRef resolve(String resource, boolean trusted) {
			if (!Util.isValidPath(resource)) {
				return null;
			}
			Path baseDir = Paths.get(originalResource);
			String sibRef = baseDir.resolveSibling(resource).toString();
			return resolver.resolve(sibRef, trusted);
		}

		@Override
		public @NonNull String description() {
			return ResourceRef.super.description() + " of " + resolver.description();
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
		public int compareTo(@NonNull ResourceRef o) {
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
