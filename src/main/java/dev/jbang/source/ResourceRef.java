package dev.jbang.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.source.resolvers.FileResourceResolver;
import dev.jbang.util.Util;

/**
 * Represents a reference to a resource, which can be identified by an original
 * resource string or a resolved file path. Resource references may point to
 * files, URLs, classpath entries, or piped-in contents (stdin). Regardless of
 * what they point to, their main goal is to provide access to the resource
 * contents without having to know how those contents are obtained.
 *
 * Instances of this interface can be created directly by using one of the
 * factory methods like {@code forFile} and {@code forResolvedResource} but
 * often they are the result of a call to {@code
 * Resolver.resolve}.
 */
public interface ResourceRef extends Comparable<ResourceRef> {
	ResourceRef nullRef = new UnresolvableResourceRef(null);

	@Nullable
	String getOriginalResource();

	/**
	 * Determines whether the original resource is a valid URL.
	 * 
	 * @return true if the original resource is not null and is a valid URL,
	 *         otherwise false
	 */
	default boolean isURL() {
		return getOriginalResource() != null && Util.isURL(getOriginalResource());
	}

	/**
	 * Determines whether the original resource is a valid classpath reference.
	 * 
	 * @return true if the original resource is not null and is a valid classpath
	 *         reference, otherwise false
	 */
	default boolean isClasspath() {
		return getOriginalResource() != null && Util.isClassPathRef(getOriginalResource());
	}

	/**
	 * Determines whether the original resource represents contents piped in from
	 * stdin.
	 * 
	 * @return true if the original resource is not null and is stdin, otherwise
	 *         false
	 */
	default boolean isStdin() {
		return getOriginalResource() != null && isStdin(getOriginalResource());
	}

	static boolean isStdin(@Nonnull String scriptResource) {
		return scriptResource.equals("-") || scriptResource.equals("/dev/stdin");
	}

	/**
	 * Returns a file representation for this resource reference. If an original
	 * resource was provided, this method will return a resolved file representation
	 * of that resource. Depending on the resource reference, the process of
	 * resolving might have been done when the reference was created, or it might be
	 * done when calling this method. In which case some kind of RuntimeException
	 * might be thrown if the resolving process fails.
	 * 
	 * @return A file reference
	 */
	@Nonnull
	default Path getFile() {
		throw new IllegalStateException("Getting contents from resource not supported: " + getOriginalResource());
	}

	/**
	 * Checks whether the resource represented by this reference exists as a regular
	 * file.
	 * 
	 * @return true if the file exists and is a regular file, false otherwise
	 */
	boolean exists();

	/**
	 * Retrieves an InputStream for the resource represented by this reference. If a
	 * file representation of the resource exists, the InputStream is opened for
	 * that file. If no file is associated, this method returns null.
	 *
	 * @return an InputStream for the resource, or null if no file is associated
	 */
	default InputStream getInputStream() {
		try {
			return Files.newInputStream(getFile());
		} catch (IOException e) {
			throw new ResourceNotFoundException(getOriginalResource(), "Could not open input stream for resource", e);
		}
	}

	/**
	 * Retrieves the file extension of the resource represented by this reference.
	 * 
	 * @return the file extension of the resource, or an empty string if no
	 *         extension is found
	 */
	@Nonnull
	default String getExtension() {
		if (getOriginalResource() != null) {
			return Util.extension(getOriginalResource());
		} else {
			return "";
		}
	}

	/**
	 * Creates a new {@code ResourceRef} instance for the given file path.
	 *
	 * @param file the file path used to create the resource reference
	 * @return a {@code ResourceRef} instance representing the specified file
	 */
	static ResourceRef forFile(@Nonnull Path file) {
		return new FileResourceResolver.FileResourceRef(file.toString(), file);
	}

	/**
	 * Creates a new {@code ResourceRef} instance for a resolved resource. It takes
	 * the original resource string, which can be things like file paths, urls,
	 * classpath references, etc and associates it with the given file reference.
	 *
	 * @param resource         the original resource as a string
	 * @param resolvedResource the resolved file path of the resource
	 * @return a {@code ResourceRef} instance representing the specified resolved
	 *         resource
	 */
	static ResourceRef forResolvedResource(@Nonnull String resource, @Nonnull Path resolvedResource) {
		return new FileResourceResolver.FileResourceRef(resource, resolvedResource);
	}

	/**
	 * Creates a new {@code ResourceRef} instance for a resolved resource. It takes
	 * the original resource string, which can be things like file paths, urls,
	 * classpath references, etc and associates it with the given file reference.
	 *
	 * @param resource the original resource as a string
	 * @param obtainer a function that obtains a file for the given resource string
	 * @return a {@code ResourceRef} instance representing the specified resolved
	 *         resource
	 */
	static ResourceRef forLazyFileResource(@Nonnull String resource, @Nonnull Function<String, Path> obtainer) {
		return new FileResourceResolver.FileResourceRef(resource, obtainer);
	}

	/**
	 * Creates a new {@code ResourceRef} instance that cannot be resolved. This is
	 * useful for cases where the resource string is known but cannot be resolved to
	 * a file or input stream.
	 *
	 * @param resource the resource string
	 * @return a {@code ResourceRef} instance
	 */
	static ResourceRef forUnresolvable(@Nonnull String resource) {
		return new UnresolvableResourceRef(resource);
	}

	/**
	 * Creates a new {@code ResourceRef} instance for the given resource string. It
	 * does this by using a default resource resolver to resolve the resource string
	 * to a file.
	 *
	 * @param resource the resource string used to create the resource reference
	 * @return a {@code ResourceRef} instance representing the specified resource
	 *         string
	 */
	static ResourceRef forResource(@Nonnull String resource) {
		return ResourceResolver.forResources().resolve(resource);
	}

	class UnresolvableResourceRef implements ResourceRef {
		private final String resource;

		public UnresolvableResourceRef(@Nullable String resource) {
			this.resource = resource;
		}

		@Nullable
		@Override
		public String getOriginalResource() {
			return resource;
		}

		@Override
		public boolean exists() {
			return false;
		}

		@Override
		public int compareTo(ResourceRef o) {
			return 0;
		}
	}

	class WrappedResourceRef implements ResourceRef {
		@Nonnull
		private final ResourceRef wrappedRef;

		public WrappedResourceRef(@Nonnull ResourceRef wrappedRef) {
			this.wrappedRef = wrappedRef;
		}

		@Nullable
		@Override
		public String getOriginalResource() {
			return wrappedRef.getOriginalResource();
		}

		@Override
		public boolean exists() {
			return wrappedRef.exists();
		}

		@Nonnull
		@Override
		public Path getFile() {
			return wrappedRef.getFile();
		}

		@Override
		public int compareTo(ResourceRef o) {
			return wrappedRef.compareTo(o);
		}

		@Override
		public boolean equals(Object obj) {
			return wrappedRef.equals(obj);
		}
	}
}
