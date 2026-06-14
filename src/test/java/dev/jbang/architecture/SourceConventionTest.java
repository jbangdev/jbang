package dev.jbang.architecture;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Source-level convention checks that cannot be enforced by ArchUnit (which
 * operates on compiled bytecode).
 *
 * <p>
 * For structural/bytecode-level rules, see {@link ArchitectureTest}.
 */
class SourceConventionTest {

	private static final Path SOURCE_ROOT = Paths.get("src/main/java");

	// Pattern matches: new ExitException(0, or new ExitException( 2, or new
	// ExitException(-1,
	// Does NOT match: new ExitException(EXIT_OK, or new ExitException(status,
	private static final Pattern RAW_EXIT_CODE = Pattern.compile("new\\s+ExitException\\(\\s*-?\\d");

	// Matches "import foo.bar.*;" but NOT "import static foo.bar.*;"
	private static final Pattern WILDCARD_IMPORT = Pattern.compile("^import\\s+(?!static\\s)\\S+\\.\\*\\s*;");

	/**
	 * Non-static wildcard imports obscure dependencies and make it hard to see
	 * which classes a file actually uses. Static wildcard imports (e.g.,
	 * {@code import static org.junit.Assert.*}) are acceptable as they import
	 * methods/constants, not types.
	 */
	@Disabled("Too many existing violations — fix incrementally")
	@Test
	void no_wildcard_imports() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
			files.filter(p -> p.toString().endsWith(".java"))
				.forEach(
						path -> {
							try {
								List<String> lines = Files.readAllLines(path);
								for (int i = 0; i < lines.size(); i++) {
									if (WILDCARD_IMPORT.matcher(lines.get(i)).find()) {
										violations.add(
												path
														+ ":"
														+ (i + 1)
														+ ": "
														+ lines.get(i).trim());
									}
								}
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						});
		}
		if (!violations.isEmpty()) {
			fail(
					"Wildcard imports found (use explicit imports instead):\n"
							+ String.join("\n", violations));
		}
	}

	/**
	 * {@link dev.jbang.cli.ExitException} must be constructed with named constants
	 * ({@code EXIT_OK}, {@code EXIT_INVALID_INPUT}, etc.) rather than raw integer
	 * literals.
	 * <p>
	 * Raw integers like {@code new ExitException(2, ...)} are opaque — reviewers
	 * can't tell at a glance what exit code 2 means, and renumbering becomes a
	 * shotgun surgery. Named constants from {@link dev.jbang.cli.BaseCommand} make
	 * the intent explicit.
	 */
	@Test
	void exit_exception_must_use_named_constants() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
			files.filter(p -> p.toString().endsWith(".java"))
				.forEach(path -> {
					try {
						List<String> lines = Files.readAllLines(path);
						for (int i = 0; i < lines.size(); i++) {
							if (RAW_EXIT_CODE.matcher(lines.get(i)).find()) {
								violations.add(path + ":" + (i + 1) + ": " + lines.get(i).trim());
							}
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
		}
		if (!violations.isEmpty()) {
			fail("ExitException with raw integer exit code (use EXIT_* constants instead):\n"
					+ String.join("\n", violations));
		}
	}
}
