package dev.jbang;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FileRef {

	private final Script source;
	String ref;

	String destination;

	public FileRef(Script source, String destination, String ref) {
		assert (destination != null);
		this.source = source;
		this.ref = ref;
		this.destination = destination;
	}

	/**
	 *
	 */
	public Path from() {
		String p = ref != null ? ref : destination;

		if (Paths.get(p).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + p);
		}

		return Paths.get(source.getOriginalFile()).resolveSibling(p);
	}

	public Path to(Path parent) {
		if (Paths.get(destination).isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + destination);
		}

		return parent.resolve(destination);
	}
}
