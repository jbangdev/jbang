package dev.jbang.source;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.cli.ExitException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.util.Util;

/**
 * This class models a source-target relationship where the source is a
 * ResourceRef and the target is a simple Path. This is used for things like the
 * //FILES target=source directives in scripts and templates.
 */
public class RefTarget {
	@NonNull
	protected final ResourceRef source;
	@Nullable
	protected final Path target;

	protected RefTarget(@NonNull ResourceRef source, @Nullable Path target) {
		assert (source != null);
		this.source = source;
		this.target = target;
	}

	@NonNull
	public ResourceRef getSource() {
		return source;
	}

	@Nullable
	public Path getTarget() {
		return target;
	}

	public Path to(Path parent) {
		Path p = target != null ? target : source.getFile().getFileName();
		return parent.resolve(p);
	}

	public void copy(Path destroot) {
		Path from = source.getFile();
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

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		RefTarget refTarget = (RefTarget) o;
		return source.equals(refTarget.source) && Objects.equals(target, refTarget.target);
	}

	@Override
	public int hashCode() {
		return Objects.hash(source, target);
	}

	@Override
	public String toString() {
		return "RefTarget{" +
				"source=" + source +
				", target=" + target +
				'}';
	}

	public static RefTarget create(String ref, Path dest, ResourceResolver siblingResolver) {
		return new RefTarget(siblingResolver.resolve(ref), dest);
	}

	public static RefTarget create(ResourceRef ref, Path dest) {
		return new RefTarget(ref, dest);
	}
}
