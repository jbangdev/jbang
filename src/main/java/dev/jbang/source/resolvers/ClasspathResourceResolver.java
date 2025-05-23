package dev.jbang.source.resolvers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.DirectResourceRef;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a classpath URL, will look up the referenced resource and, if it's
 * a file on the local file system return a direct reference to that file or if
 * it's a JAR resource it will create a copy of it in the cache and return a
 * reference to that.
 */
public class ClasspathResourceResolver implements ResourceResolver {
	@Nonnull
	@Override
	public String description() {
		return "Classpath resolver";
	}

	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;
		if (resource.startsWith("classpath:/")) {
			result = getClasspathResource(resource);
		}
		return result;
	}

	private static ResourceRef getClasspathResource(String cpResource) {
		String ref = cpResource.substring(11);
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = DirectResourceRef.class.getClassLoader();
		}
		URL url = cl.getResource(ref);
		if (url == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Resource not found on class path: " + ref);
		}
		return new ClasspathResourceRef(cpResource, url);
	}

	public static class ClasspathResourceRef extends DirectResourceRef {
		@Nonnull
		private URL url;

		protected ClasspathResourceRef(@Nonnull String ref, @Nonnull URL url) {
			super(ref, null);
			this.url = url;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Nullable
		@Override
		public Path getFile() {
			return null;
		}

		@Nonnull
		@Override
		public InputStream getInputStream() throws IOException {
			return url.openStream();
		}
	}
}
