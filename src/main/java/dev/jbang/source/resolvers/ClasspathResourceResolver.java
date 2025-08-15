package dev.jbang.source.resolvers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.jspecify.annotations.NonNull;

import dev.jbang.source.*;

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
		return getClasspathResource(resource);
	}

	private static ResourceRef getClasspathResource(String cpResource) {
		return new ClasspathResourceRef(cpResource);
	}

	public static class ClasspathResourceRef extends InputStreamResourceRef {
		protected ClasspathResourceRef(@NonNull String ref) {
			super(ref, ClasspathResourceRef::createStream);
		}

		private static InputStream createStream(String resource) {
			URL url = getResourceUrl(resource);
			if (url == null) {
				throw new ResourceNotFoundException(resource, "Resource not found on class path");
			}
			try {
				return url.openStream();
			} catch (IOException e) {
				throw new ResourceNotFoundException(resource, "Could not open input stream for resource", e);
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
	}
}
