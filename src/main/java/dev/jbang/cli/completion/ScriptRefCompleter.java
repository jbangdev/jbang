package dev.jbang.cli.completion;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import dev.jbang.catalog.Catalog;
import dev.jbang.dependencies.ArtifactResolver;
import dev.jbang.source.Source;
import dev.jbang.util.Util;

/**
 * Provides tab-completion for the {@code scriptOrFile} argument used by
 * commands such as {@code run}, {@code build}, {@code edit}, etc.
 * <p>
 * Completes:
 * <ul>
 * <li>Local files (directories + JBang-supported extensions)</li>
 * <li>Alias names from the merged catalog</li>
 * <li>Catalog alias browsing ({@code @catalogName} lists aliases in that
 * catalog, selecting one produces {@code alias@catalogName})</li>
 * <li>Maven GAV coordinates from the local repository ({@code ~/.m2})</li>
 * </ul>
 */
public class ScriptRefCompleter implements OptionCompleter<CompleterInvocation> {

	/** Extensions recognised by JBang (derived from {@link Source.Type}). */
	private static final Set<String> SCRIPT_EXTENSIONS;

	static {
		SCRIPT_EXTENSIONS = new HashSet<>(Source.Type.extensions());
		// Also accept files without extension (scripts with shebangs)
	}

	@Override
	public void complete(CompleterInvocation inv) {
		String partial = inv.getGivenCompleteValue();
		if (partial == null) {
			partial = "";
		}

		Set<String> candidates = new TreeSet<>(); // sorted for stable output

		if (partial.startsWith("@")) {
			completeCatalogAliases(candidates, partial);
		} else if (looksLikeGav(partial)) {
			completeGav(candidates, partial);
		} else {
			completeFiles(candidates, partial);
			completeAliases(candidates, partial);
		}

		inv.addAllCompleterValues(candidates);
		if (candidates.size() == 1) {
			String single = candidates.iterator().next();
			// Don't append a space after directory, GAV separator, or
			// group prefix — the user will keep navigating
			if (single.endsWith("/") || single.endsWith("\\")
					|| single.endsWith(":") || single.endsWith(".")) {
				inv.setAppendSpace(false);
			}
		}
		// When browsing a catalog (@name), ignore the typed prefix so aesh
		// replaces the whole token instead of trying to append to it.
		if (partial.startsWith("@") && !candidates.isEmpty()) {
			inv.setIgnoreStartsWith(true);
			inv.setOffset(0);
		}
	}

	// ---- File completion -----------------------------------------------

	private void completeFiles(Set<String> candidates, String partial) {
		Path cwd = Util.getCwd();
		Path base;
		String prefix;

		int lastSep = Math.max(partial.lastIndexOf('/'), partial.lastIndexOf('\\'));
		if (lastSep >= 0) {
			prefix = partial.substring(0, lastSep + 1);
			base = cwd.resolve(prefix).normalize();
		} else {
			prefix = "";
			base = cwd;
		}

		if (!Files.isDirectory(base)) {
			return;
		}

		String namePrefix = partial.substring(prefix.length());

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
			for (Path entry : stream) {
				String name = entry.getFileName().toString();
				if (name.startsWith(".")) {
					continue; // skip hidden files
				}
				if (!name.toLowerCase().startsWith(namePrefix.toLowerCase())) {
					continue;
				}
				if (Files.isDirectory(entry)) {
					candidates.add(prefix + name + "/");
				} else if (isScriptFile(name)) {
					candidates.add(prefix + name);
				}
			}
		} catch (IOException e) {
			// best-effort — ignore
		}
	}

	private boolean isScriptFile(String name) {
		int dot = name.lastIndexOf('.');
		if (dot < 0) {
			return false; // skip extension-less files in listings
		}
		String ext = name.substring(dot + 1).toLowerCase();
		return SCRIPT_EXTENSIONS.contains(ext);
	}

	// ---- Alias completion ----------------------------------------------

	private void completeAliases(Set<String> candidates, String partial) {
		try {
			Catalog merged = Catalog.getMerged(true, true);
			for (String name : merged.aliases.keySet()) {
				if (name.startsWith(partial)) {
					candidates.add(name);
				}
			}
		} catch (Exception e) {
			// best-effort — catalog may be unavailable
		}
	}

	// ---- Catalog alias browsing ----------------------------------------

	/**
	 * When the user types {@code @catalogName}, list all aliases in that catalog.
	 * Candidates are emitted as {@code alias@catalogName} so that selecting one
	 * replaces the {@code @catalogName} token with the fully-qualified reference.
	 */
	private void completeCatalogAliases(Set<String> candidates, String partial) {
		String catalogName = partial.substring(1); // strip leading '@'
		if (catalogName.isEmpty()) {
			// Just "@" — list available catalog names as "@name"
			try {
				Catalog merged = Catalog.getMerged(true, true);
				for (String name : merged.catalogs.keySet()) {
					candidates.add("@" + name);
				}
			} catch (Exception e) {
				// best-effort
			}
			return;
		}

		// Try to load the catalog by exact name
		try {
			Catalog catalog = Catalog.getByName(catalogName);
			// List all aliases in this catalog as alias@catalogName
			for (String alias : catalog.aliases.keySet()) {
				candidates.add(alias + "@" + catalogName);
			}
		} catch (Exception e) {
			// Not a valid catalog — ignore
		}

		// Also prefix-match catalog names (handles sub-catalogs like
		// @jbanghub/h2 when user types @jbanghub, and partial matches
		// when the exact name doesn't resolve)
		try {
			Catalog merged = Catalog.getMerged(true, true);
			for (String name : merged.catalogs.keySet()) {
				if (name.startsWith(catalogName) && !name.equals(catalogName)) {
					candidates.add("@" + name);
				}
			}
		} catch (Exception ex) {
			// best-effort
		}
	}

	// ---- Maven GAV completion -------------------------------------------

	/**
	 * Heuristic to detect if the partial input looks like a Maven GAV coordinate.
	 * We trigger GAV completion when the input contains a colon or looks like a
	 * dotted package prefix (at least two dot-separated segments with no path
	 * separators).
	 */
	static boolean looksLikeGav(String partial) {
		if (partial.contains(":")) {
			return true;
		}
		// At least two dot-separated segments (e.g. "com.google") with no path
		// separators — that likely means a groupId prefix, not a filename.
		if (partial.contains("/") || partial.contains("\\")) {
			return false;
		}
		long dots = partial.chars().filter(c -> c == '.').count();
		return dots >= 2;
	}

	/**
	 * Complete Maven GAV coordinates from the local repository.
	 * <ul>
	 * <li>{@code com.goo} → groupId prefixes</li>
	 * <li>{@code com.google.guava:} → artifactIds in that groupId</li>
	 * <li>{@code com.google.guava:guava:} → versions</li>
	 * </ul>
	 */
	private void completeGav(Set<String> candidates, String partial) {
		Path repoDir = getLocalMavenRepo();
		if (repoDir == null || !Files.isDirectory(repoDir)) {
			return;
		}

		String[] parts = partial.split(":", -1);
		if (parts.length == 1) {
			// No colon yet — complete groupId
			completeGroupId(candidates, repoDir, parts[0]);
		} else if (parts.length == 2) {
			// groupId:partial — complete artifactId
			completeArtifactId(candidates, repoDir, parts[0], parts[1]);
		} else if (parts.length == 3) {
			// groupId:artifactId:partial — complete version
			completeVersion(candidates, repoDir, parts[0], parts[1], parts[2]);
		}
	}

	private Path getLocalMavenRepo() {
		try {
			return ArtifactResolver.getLocalMavenRepo();
		} catch (Exception e) {
			// Fallback to default location
			Path home = Paths.get(System.getProperty("user.home"));
			return home.resolve(".m2").resolve("repository");
		}
	}

	/**
	 * Complete groupId by walking the repository directory tree. Dots in the
	 * groupId map to directory separators.
	 */
	private void completeGroupId(Set<String> candidates, Path repoDir, String partial) {
		// Split partial into resolved path + leaf prefix
		// e.g. "com.google.gu" → dir=com/google, prefix="gu"
		int lastDot = partial.lastIndexOf('.');
		String parentGroup;
		String leafPrefix;
		Path parentDir;
		if (lastDot >= 0) {
			parentGroup = partial.substring(0, lastDot);
			leafPrefix = partial.substring(lastDot + 1);
			parentDir = repoDir.resolve(parentGroup.replace('.', '/'));
		} else {
			parentGroup = "";
			leafPrefix = partial;
			parentDir = repoDir;
		}

		if (!Files.isDirectory(parentDir)) {
			return;
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDir)) {
			for (Path entry : stream) {
				if (!Files.isDirectory(entry)) {
					continue;
				}
				String name = entry.getFileName().toString();
				if (name.startsWith(".")) {
					continue;
				}
				if (!name.toLowerCase().startsWith(leafPrefix.toLowerCase())) {
					continue;
				}
				String groupSoFar = parentGroup.isEmpty() ? name : parentGroup + "." + name;
				if (hasArtifactChildren(entry)) {
					// This groupId has artifacts — offer it with trailing colon
					candidates.add(groupSoFar + ":");
				}
				if (hasSubGroups(entry)) {
					// Has deeper groupId segments — offer with trailing dot
					candidates.add(groupSoFar + ".");
				}
			}
		} catch (IOException e) {
			// best-effort
		}
	}

	/**
	 * Complete artifactId within a known groupId.
	 */
	private void completeArtifactId(Set<String> candidates, Path repoDir,
			String groupId, String partial) {
		Path groupDir = repoDir.resolve(groupId.replace('.', '/'));
		if (!Files.isDirectory(groupDir)) {
			return;
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(groupDir)) {
			for (Path entry : stream) {
				if (!Files.isDirectory(entry)) {
					continue;
				}
				String name = entry.getFileName().toString();
				if (name.startsWith(".")) {
					continue;
				}
				if (!name.toLowerCase().startsWith(partial.toLowerCase())) {
					continue;
				}
				// Only offer if this looks like an artifactId (has version subdirs)
				if (isArtifactDir(entry)) {
					candidates.add(groupId + ":" + name + ":");
				}
			}
		} catch (IOException e) {
			// best-effort
		}
	}

	/**
	 * Complete version for a known groupId:artifactId.
	 */
	private void completeVersion(Set<String> candidates, Path repoDir,
			String groupId, String artifactId, String partial) {
		Path artifactDir = repoDir.resolve(groupId.replace('.', '/')).resolve(artifactId);
		if (!Files.isDirectory(artifactDir)) {
			return;
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(artifactDir)) {
			for (Path entry : stream) {
				if (!Files.isDirectory(entry)) {
					continue;
				}
				String name = entry.getFileName().toString();
				if (name.startsWith(".")) {
					continue;
				}
				if (!name.startsWith(partial)) {
					continue;
				}
				// Verify it's a real version dir (contains a .pom)
				if (isVersionDir(entry)) {
					candidates.add(groupId + ":" + artifactId + ":" + name);
				}
			}
		} catch (IOException e) {
			// best-effort
		}
	}

	/**
	 * Returns true if the directory contains at least one subdirectory that is an
	 * artifact directory (i.e. looks like an artifactId, not a deeper groupId
	 * segment).
	 */
	private boolean hasArtifactChildren(Path dir) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path child : stream) {
				if (Files.isDirectory(child) && isArtifactDir(child)) {
					return true;
				}
			}
		} catch (IOException e) {
			// ignore
		}
		return false;
	}

	/**
	 * Returns true if the directory contains at least one subdirectory that is not
	 * an artifact directory (i.e. is a deeper groupId segment).
	 */
	private boolean hasSubGroups(Path dir) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path child : stream) {
				if (Files.isDirectory(child) && !isArtifactDir(child)) {
					return true;
				}
			}
		} catch (IOException e) {
			// ignore
		}
		return false;
	}

	/**
	 * An artifact directory contains version subdirectories which in turn contain
	 * {@code .pom} files.
	 */
	private boolean isArtifactDir(Path dir) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path child : stream) {
				if (Files.isDirectory(child) && isVersionDir(child)) {
					return true;
				}
			}
		} catch (IOException e) {
			// ignore
		}
		return false;
	}

	/**
	 * A version directory contains at least one {@code .pom} file.
	 */
	private boolean isVersionDir(Path dir) {
		try (Stream<Path> files = Files.list(dir)) {
			return files.anyMatch(f -> f.toString().endsWith(".pom"));
		} catch (IOException e) {
			return false;
		}
	}
}
