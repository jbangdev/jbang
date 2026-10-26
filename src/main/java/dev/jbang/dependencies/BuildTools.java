package dev.jbang.dependencies;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.jbang.ExitException;
import dev.jbang.util.Util;

/**
 * Shared plumbing for {@link BuildSystem} strategies: subprocess execution,
 * wrapper-script detection, and the {@code JBANG_CLASSPATH=} marker convention
 * used by strategies whose native output cannot be parsed reliably otherwise.
 */
final class BuildTools {

	static final String CLASSPATH_MARKER = "JBANG_CLASSPATH=";

	private BuildTools() {
	}

	/**
	 * Returns a path to the local wrapper script if one is present in {@code dir},
	 * otherwise {@code fallback} (assumed to be on {@code PATH}).
	 */
	static String wrapper(Path dir, String unix, String windows, String fallback) {
		Path wrapper = dir.resolve(Util.isWindows() ? windows : unix);
		return Files.isRegularFile(wrapper) ? wrapper.toString() : fallback;
	}

	/**
	 * Runs a build tool and captures its combined stdout+stderr. Silent on success
	 * (unless verbose); on failure the captured output is surfaced so the user can
	 * see what went wrong.
	 */
	static String run(List<String> command, Path dir) throws IOException {
		Util.verboseMsg("Build classpath: " + String.join(" ", command));
		ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
		Process process = pb.start();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (InputStream is = process.getInputStream()) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = is.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
		String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ExitException(ExitException.EXIT_GENERIC_ERROR, e);
		}
		if (process.exitValue() != 0) {
			if (!output.isEmpty()) {
				Util.infoMsg(output);
			}
			throw new ExitException(process.exitValue(),
					"Could not resolve build classpath using: " + String.join(" ", command));
		}
		if (Util.isVerbose() && !output.isEmpty()) {
			Util.verboseMsg(output);
		}
		return output;
	}

	/**
	 * Finds the first line containing {@link #CLASSPATH_MARKER} and returns
	 * everything after the marker. Throws {@link ExitException} if no such line
	 * exists.
	 */
	static String markedClassPath(String output, Path buildFile) {
		for (String line : output.split("\\R")) {
			int marker = line.indexOf(CLASSPATH_MARKER);
			if (marker >= 0) {
				return line.substring(marker + CLASSPATH_MARKER.length()).trim();
			}
		}
		throw classPathError(buildFile);
	}

	static ExitException classPathError(Path buildFile) {
		return new ExitException(ExitException.EXIT_GENERIC_ERROR,
				"Build did not print " + CLASSPATH_MARKER + " for " + buildFile);
	}
}
