package dev.jbang.source.resolvers;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

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

		Path probe = null;
		try {
			probe = Util.getCwd().resolve(resource).normalize();
		} catch (InvalidPathException e) {
			// Ignore
		}

		if (probe != null && Files.isReadable(probe)) {
			result = ResourceRef.forNamedFile(resource, probe);
		}

		return result;
	}
}
