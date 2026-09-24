package dev.jbang.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import dev.jbang.ExitException;

class MillBuildSystem implements BuildSystem {

	@Override
	public boolean supports(String fileName) {
		return "build.mill".equals(fileName) || "build.mill.yaml".equals(fileName) || "build.sc".equals(fileName);
	}

	@Override
	public String resolveClassPath(Path buildFile) throws IOException {
		Path dir = buildFile.getParent();
		// `--disable-ticker` suppresses the progress bar that would otherwise be
		// interleaved with the JSON output on stdout when stderr is merged.
		List<String> command = Arrays.asList(
				BuildTools.wrapper(dir, "mill", "mill.bat", "mill"),
				"--disable-ticker", "show", "compileClasspath");
		String output = BuildTools.run(command, dir);
		// Mill mixes into stdout: JVM `sun.misc.Unsafe` warnings (with ANSI
		// escapes containing `[`), log lines like `[build.mill] [info] ...`,
		// and finally the JSON. Real JSON opens with `[` on its own line
		// (followed by a newline), which is unambiguous.
		int start = findJsonStart(output);
		int end = output.lastIndexOf(']');
		if (start < 0 || end < start) {
			throw BuildTools.classPathError(buildFile);
		}
		String json = output.substring(start, end + 1);
		try {
			List<String> raw = new Gson().fromJson(json, new TypeToken<List<String>>() {
			}.getType());
			if (raw == null) {
				throw BuildTools.classPathError(buildFile);
			}
			// Mill wraps each entry as `qref:vN:HASH:PATH` or `ref:vN:HASH:PATH`.
			// Strip the metadata prefix — we only care about the actual path.
			List<String> classPath = new java.util.ArrayList<>(raw.size());
			for (String entry : raw) {
				classPath.add(stripMillPrefix(entry));
			}
			return String.join(File.pathSeparator, classPath);
		} catch (JsonParseException e) {
			throw new ExitException(ExitException.EXIT_GENERIC_ERROR,
					"Could not read Mill compileClasspath from " + buildFile, e);
		}
	}

	/**
	 * Mill's {@code show} command tags each classpath entry as
	 * {@code qref:vN:HASH:PATH} or {@code ref:vN:HASH:PATH}. This returns just the
	 * {@code PATH} component; entries with no recognized prefix are returned as-is.
	 */
	/** Finds the position of a `[` that opens the JSON array (on its own line). */
	private static int findJsonStart(String output) {
		if (output.startsWith("[\n") || output.startsWith("[\r")) {
			return 0;
		}
		int idx = 0;
		while ((idx = output.indexOf("\n[", idx)) >= 0) {
			int after = idx + 2;
			if (after < output.length() && (output.charAt(after) == '\n' || output.charAt(after) == '\r')) {
				return idx + 1;
			}
			idx = after;
		}
		return -1;
	}

	private static String stripMillPrefix(String entry) {
		if (entry.startsWith("qref:") || entry.startsWith("ref:")) {
			int firstColon = entry.indexOf(':');
			int versionColon = entry.indexOf(':', firstColon + 1);
			int hashColon = entry.indexOf(':', versionColon + 1);
			if (hashColon > 0 && hashColon + 1 < entry.length()) {
				return entry.substring(hashColon + 1);
			}
		}
		return entry;
	}
}
