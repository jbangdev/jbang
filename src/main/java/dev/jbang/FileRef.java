package dev.jbang;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FileRef {

	private final Script source;
	String ref;

	String destination;

	public FileRef(Script source, String ref, String destination) {
		this.source = source;
		this.ref = ref;
		this.destination = destination;
	}

	/**
	 *
	 */
	public Path from() {
		if (Paths.get(ref).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + ref);
		}

		return Paths.get(source.getOriginalFile()).resolveSibling(ref);
	}

	public Path to(Path parent) {
		String p = destination != null ? destination : ref;
		if (Paths.get(p).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + p);
		}

		return destination != null ? parent.resolve(destination) : parent.resolve(ref);
	}
}
