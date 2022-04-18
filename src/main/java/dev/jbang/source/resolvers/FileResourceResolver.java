package dev.jbang.source.resolvers;

import java.io.File;
import java.nio.file.InvalidPathException;

import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a path to a file on the local file system, will return a reference
 * to that file.
 */
public class FileResourceResolver implements ResourceResolver {

	@Override
	public String description() {
		return "File Resource resolver";
	}

	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;

		File probe = null;
		try {
			probe = Util.getCwd().resolve(resource).normalize().toFile();
		} catch (InvalidPathException e) {
			// Ignore
		}

		if (probe != null && probe.canRead()) {
			result = ResourceRef.forNamedFile(resource, probe);
		}

		return result;
	}
}
