package dev.jbang.resources.resolvers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;

import org.jspecify.annotations.NonNull;

import dev.jbang.resources.InputStreamResourceRef;
import dev.jbang.resources.ResourceNotFoundException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a classpath URL, will look up the referenced resource and, if it's
 * a file on the local file system return a direct reference to that file or if
 * it's a JAR resource it will create a copy of it in the cache and return a
 * reference to that.
 */
public class ClasspathResourceResolver implements ResourceResolver {
	@NonNull
	@Override
	public String description() {
		return "Classpath resolver";
	}

	@Override
	public ResourceRef resolve(String resource) {
		if (!resource.startsWith("classpath:/")) {
			return null;
		}
		return getClasspathResource(resource, this);
	}

	private static ResourceRef getClasspathResource(String cpResource, ResourceResolver resolver) {
		return new ClasspathResourceRef(cpResource, resolver);
	}

	public static class ClasspathResourceRef extends InputStreamResourceRef {
		protected ClasspathResourceRef(@NonNull String ref, ResourceResolver resolver) {
			super(ref, ClasspathResourceRef::createStream, resolver);
		}

		private static InputStream createStream(String resource) {
			URL url = getResourceUrl(resource);
			if (url == null) {
				throw new ResourceNotFoundException(resource, "Resource " + resource + " not found on class path");
			}
			try {
				return url.openStream();
			} catch (IOException e) {
				throw new ResourceNotFoundException(resource, "Could not open input stream for resource " + resource,
						e);
			}
		}

		private static URL getResourceUrl(String resource) {
			String ref = resource.substring(11);
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			if (cl == null) {
				cl = ClasspathResourceRef.class.getClassLoader();
			}
			return cl.getResource(ref);
		}

		@Override
		public boolean exists() {
			return getResourceUrl(originalResource) != null;
		}

		@Override
		public ResourceRef resolve(String resource, boolean trusted) {
			if (!Util.isValidPath(resource)) {
				return null;
			}
			String sibRef = "classpath:" + Paths.get(originalResource.substring(11)).resolveSibling(resource);
			return super.resolve(sibRef, trusted);
		}
	}
}
