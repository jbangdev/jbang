package dev.jbang.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import dev.jbang.ExitException;

class SbtBuildSystem implements BuildSystem {

	@Override
	public boolean supports(String fileName) {
		return "build.sbt".equals(fileName);
	}

	@Override
	public String resolveClassPath(Path buildFile) throws IOException {
		Path dir = buildFile.getParent();
		// `export` writes the resolved classpath as a single line to stdout;
		// `--error` silences info logging and `--batch` disables the interactive
		// shell so the process exits after evaluating the command.
		List<String> command = Arrays.asList(
				BuildTools.wrapper(dir, "sbt", "sbt.bat", "sbt"),
				"--error", "--batch", "export Compile / dependencyClasspath");
		String output = BuildTools.run(command, dir);
		// The last non-blank line should be the classpath. Validate that it looks
		// like one (contains a path separator or at least a jar reference)
		// rather than an error or log tail.
		String candidate = null;
		for (String line : output.split("\\R")) {
			String trimmed = line.trim();
			if (!trimmed.isEmpty()) {
				candidate = trimmed;
			}
		}
		if (candidate == null
				|| (!candidate.contains(File.pathSeparator) && !candidate.endsWith(".jar"))) {
			throw new ExitException(ExitException.EXIT_GENERIC_ERROR,
					"sbt did not print a compile classpath for " + buildFile + ". Last line was: " + candidate);
		}
		return candidate;
	}
}
