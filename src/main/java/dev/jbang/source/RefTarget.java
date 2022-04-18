package dev.jbang.source;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import dev.jbang.cli.ExitException;
import dev.jbang.cli.ResourceNotFoundException;
import dev.jbang.util.Util;

/**
 * This class models a source-target relationship where the source is a
 * ResourceRef and the target is a simple Path. This is used for things like the
 * //FILES target=source directives in scripts and templates.
 */
public class RefTarget {
	protected final ResourceRef source;
	protected final Path target;

	protected RefTarget(ResourceRef source, Path target) {
		assert (source != null);
		this.source = source;
		this.target = target;
	}

	public ResourceRef getSource() {
		return source;
	}

	public Path getTarget() {
		return target;
	}

	public Path to(Path parent) {
		Path p = target != null ? target : source.getFile().toPath().getFileName();
		return parent.resolve(p);
	}

	public void copy(Path destroot) {
		Path from = source.getFile().toPath();
		Path to = to(destroot);
		Util.verboseMsg("Copying " + from + " to " + to);
		try {
			if (!to.toFile().getParentFile().exists()) {
				to.toFile().getParentFile().mkdirs();
			}
			Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ioe) {
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Could not copy " + from + " to " + to, ioe);
		}
	}

	public static RefTarget create(String fileReference, ResourceResolver siblingResolver) {
		String[] split = fileReference.split(" // ")[0].split("=", 2);
		String ref;
		String dest = null;

		if (split.length == 1) {
			ref = split[0];
		} else {
			dest = split[0];
			ref = split[1];
		}

		Path p = dest != null ? Paths.get(dest) : null;

		if (Paths.get(ref).isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + ref);
		}
		if (p != null && p.isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + dest);
		}

		try {
			return create(ref, p, siblingResolver);
		} catch (ResourceNotFoundException rnfe) {
			throw new ExitException(EXIT_INVALID_INPUT, String.format("Could not find '%s' when resolving '%s' in %s",
					rnfe.getResourceDescription(),
					fileReference,
					siblingResolver.description()),
					rnfe);
		}
	}

	public static RefTarget create(String ref, Path dest, ResourceResolver siblingResolver) {
		return new RefTarget(siblingResolver.resolve(ref), dest);
	}
}
