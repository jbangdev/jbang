package dev.jbang;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import picocli.CommandLine;

public class URLRef extends FileRef {

	public URLRef(Script source, String destination, String ref) {
		super(source, destination, ref);
	}

	URI from() {
		String p = ref != null ? ref : destination;

		if (Paths.get(p).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + p);
		}

		try {
			return new URI(source.getOriginalFile()).resolve(p);
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Could not resolve URI", e);
		}
	}

	public void copy(Path destroot, Path tempDir) {
		URI from = from();
		Path to = to(destroot);
		Util.verboseMsg("Copying " + from + " to " + to);
		try {
			if (!to.toFile().getParentFile().exists()) {
				to.toFile().getParentFile().mkdirs();
			}
			Path dest = Util.downloadFile(from.toString(), tempDir.toFile());
			Files.copy(dest, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ioe) {
			throw new ExitException(CommandLine.ExitCode.USAGE, "Could not copy " + from + " to " + to, ioe);
		}
	}
}
