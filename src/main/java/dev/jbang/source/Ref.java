package dev.jbang.source;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import dev.jbang.Main;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public abstract class Ref {
	protected final String base;
	protected final String ref;
	protected final String destination;

	protected Ref(String base, String ref, String destination) {
		assert (ref != null);
		if (Paths.get(ref).isAbsolute()) {
			throw new IllegalStateException("Only relative paths allowed in //FILES. Found absolute path: " + ref);
		}
		if (destination != null && Paths.get(destination).isAbsolute()) {
			throw new IllegalStateException(
					"Only relative paths allowed in //FILES. Found absolute path: " + destination);
		}
		this.base = base;
		this.ref = ref;
		this.destination = destination;
	}

	public String getRef() {
		return ref;
	}

	public String getDestination() {
		return destination;
	}

	public Path to(Path parent) {
		return parent.resolve(ref);
	}

	public abstract void copy(Path destroot);

	static boolean isClassPathRef(String ref) {
		return ref.startsWith("classpath:");
	}

	static boolean isURL(String url) {
		try {
			new URL(url);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static Ref fromReference(String base, String fileReference) {
		String[] split = fileReference.split(" // ")[0].split("=");
		String ref;
		String dest = null;

		if (split.length == 1) {
			ref = split[0];
		} else if (split.length == 2) {
			ref = split[0];
			dest = split[1];
		} else {
			throw new IllegalStateException("Invalid file reference: " + fileReference);
		}

		if (Ref.isClassPathRef(fileReference) || Ref.isClassPathRef(base)) {
			if (isClassPathRef(ref)) {
				ref = ref.substring(11);
			}
			return new ClassPathRef(base, ref, dest);
		} else if (Ref.isURL(fileReference) || Ref.isURL(base)) {
			return new URLRef(base, ref, dest);
		} else {
			return new FileRef(base, ref, dest);
		}
	}
}

class FileRef extends Ref {

	protected FileRef(String base, String ref, String destination) {
		super(base, ref, destination);
	}

	protected String from() {
		String p = destination != null ? destination : ref;
		return Paths.get(base).resolveSibling(p).toString();
	}

	@Override
	public void copy(Path destroot) {
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
}

class URLRef extends FileRef {

	protected URLRef(String base, String destination, String ref) {
		super(base, destination, ref);
	}

	@Override
	protected String from() {
		String p = destination != null ? destination : ref;
		try {
			return new URI(base).resolve(p).toString();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Could not resolve URI", e);
		}
	}

	@Override
	public void copy(Path destroot) {
		String from = from();
		Path to = to(destroot);
		Util.verboseMsg("Copying " + from + " to " + to);
		try {
			if (!to.toFile().getParentFile().exists()) {
				to.toFile().getParentFile().mkdirs();
			}
			Path dest = Util.downloadAndCacheFile(Util.swizzleURL(from));
			Files.copy(dest, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ioe) {
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Could not copy " + from + " to " + to, ioe);
		}
	}
}

class ClassPathRef extends Ref {

	protected ClassPathRef(String base, String ref, String destination) {
		super(base, ref, destination);
	}

	public Path to(Path parent) {
		String r = ref;
		return parent.resolve(r);
	}

	@Override
	public void copy(Path destroot) {
		Path to = to(destroot);
		Util.verboseMsg("Copying " + ref + " to " + to);
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = Main.class.getClassLoader();
		}
		URL url = cl.getResource(ref);
		if (url == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Resource not found on class path: " + ref);
		}
		try (InputStream is = url.openStream()) {
			Files.copy(is, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR,
					"Resource could not be copied from class path: " + ref, e);
		}
	}
}
