package dev.jbang;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileRef {
	final String base;
	final String ref;
	final String destination;

	public FileRef(String base, String ref, String destination) {
		assert (ref != null);
		this.base = base;
		this.ref = ref;
		this.destination = destination;
	}

	private String from() {
		String p = destination != null ? destination : ref;

		if (Paths.get(p).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + p);
		}

		return Paths.get(base).resolveSibling(p).toString();
	}

	protected Path to(Path parent) {
		if (Paths.get(ref).isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + ref);
		}

		return parent.resolve(ref);
	}

	protected static boolean isURL(String url) {
		try {
			new URL(url);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void copy(Path destroot, boolean updateCache) {
		Path from = Paths.get(from());
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

	public String getRef() {
		return ref;
	}
}
