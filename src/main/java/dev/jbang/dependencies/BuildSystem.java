package dev.jbang.dependencies;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Strategy for resolving a compile classpath from a build tool's build file
 * (e.g. {@code pom.xml}, {@code build.gradle}). Implementations invoke the
 * matching tool and return its compile classpath as a
 * {@link java.io.File#pathSeparator}-joined string.
 *
 * Caching, filename dispatch, and CLI/directive plumbing live in
 * {@link BuildSystemClassPaths}; strategies stay focused on the tool-specific
 * command and output parsing.
 */
interface BuildSystem {

	/** Returns true if this strategy handles a build file with the given name. */
	boolean supports(String fileName);

	/**
	 * Runs the build tool for {@code buildFile} and returns the resolved compile
	 * classpath as a single string joined by {@link java.io.File#pathSeparator}.
	 */
	String resolveClassPath(Path buildFile) throws IOException;
}
