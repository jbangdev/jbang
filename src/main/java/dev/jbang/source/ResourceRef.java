package dev.jbang.source;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Objects;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public class ResourceRef implements Comparable<ResourceRef> {
	// original requested resource
	private final String originalResource;
	// cache folder it is stored inside
	private final File file;

	public static final ResourceRef nullRef = new ResourceRef(null, null);

	private ResourceRef(String ref, File file) {
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

	public File getFile() {
		return file;
	}

	public String getOriginalResource() {
		return originalResource;
	}

	public ResourceRef asSibling(String siblingResource) {
		String sr;
		try {
			if (Util.isURL(siblingResource)) {
				sr = new URI(siblingResource).toString();
			} else if (isURL()) {
				sr = new URI(Util.swizzleURL(originalResource)).resolve(siblingResource).toString();
			} else if (Util.isClassPathRef(siblingResource)) {
				sr = siblingResource;
			} else if (isClasspath()) {
				sr = Paths.get(originalResource.substring(11)).resolveSibling(siblingResource).toString();
				sr = "classpath:" + sr;
			} else {
				sr = Paths.get(originalResource).resolveSibling(siblingResource).toString();
			}
			ResourceRef result = forResource(sr);
			if (result == null) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not find " + siblingResource);
			}
			return result;
		} catch (URISyntaxException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, e);
		}
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
			if (originalResource.equals(file.getPath())) {
				return originalResource;
			} else {
				return originalResource + " (cached as: " + file.getPath() + ")";
			}
		} else {
			String res = "";
			if (originalResource != null) {
				res += originalResource;
			}
			if (file != null) {
				res += file.getPath();
			}
			return res;
		}
	}

	public static ResourceRef forFile(File file) {
		return new ResourceRef(null, file);
	}

	public static ResourceRef forNamedFile(String resource, File file) {
		return new ResourceRef(resource, file);
	}

	public static ResourceRef forCachedResource(String resource, File cachedResource) {
		return new ResourceRef(resource, cachedResource);
	}

	public static ResourceRef forResource(String resource) {
		return ResourceResolver.forResources().resolve(resource);
	}

	public static boolean isStdin(String scriptResource) {
		return scriptResource.equals("-") || scriptResource.equals("/dev/stdin");
	}
}
