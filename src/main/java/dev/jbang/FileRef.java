package dev.jbang;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileRef {

	final Script source;

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
	private Path from() {
		String p = ref != null ? ref : destination;

		if (Paths.get(p).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + p);
		}

		return Paths.get(source.getScriptResource().getOriginalResource()).resolveSibling(p);
	}

	protected Path to(Path parent) {
		if (Paths.get(destination).isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + destination);
		}

		return parent.resolve(destination);
	}

	protected static boolean isURL(String url) {
		try {
			new URL(url);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void copy(Path destroot, Path tempDir) {
		Path from = from();
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

	public String getDestination() {
		return destination;
	}

	public Script getSource() {
		return source;
	}
}
