package dev.jbang.cli.completion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

import dev.jbang.Settings;
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

	/**
	 * Maximum age in milliseconds for a previous completion to count as double-tab.
	 */
	private static final long DOUBLE_TAB_THRESHOLD_MS = 5000;

	/** File name used to record the last completion invocation. */
	private static final String LAST_COMPLETE_FILE = ".lastcomplete";

	private static String described(String candidate, String description) {
		return candidate + "\t" + description;
	}

	@Override
	public void complete(CompleterInvocation inv) {
		String partial = inv.getGivenCompleteValue();
		if (partial == null) {
			partial = "";
		}

		boolean doubleTap = isDoubleTap(partial);
		recordCompletion(partial);

		Set<String> candidates = new TreeSet<>(); // sorted for stable output

		boolean specialMode = false;
		if (partial.startsWith("@")) {
			completeCatalogAliases(candidates, partial);
			specialMode = true;
		} else if (GitHubCompletionProvider.canComplete(partial)) {
			GitHubCompletionProvider.complete(candidates, partial, SCRIPT_EXTENSIONS);
			specialMode = true;
		} else if (looksLikeGav(partial)) {
			completeGav(candidates, partial);
			completeGavRemote(candidates, partial);
			specialMode = true;
		} else {
			completeFiles(candidates, partial);
			completeAliases(candidates, partial);
			addNavigationHints(candidates, partial);
			// When no files or aliases matched and the partial looks like it
			// could be a groupId prefix, search Maven Central for matching
			// artifacts. Only hint-only candidates (starting with @ or https://
			// or ending with : for the Maven colon hint) don't count.
			if (!partial.isEmpty() && looksLikeGroupIdPrefix(partial) && !hasRealCandidates(candidates)) {
				completeGavRemote(candidates, partial);
			}
		}

		// In specialized modes (GAV, catalog, GitHub), if no results were found,
		// return the partial itself to suppress the shell's file-completion
		// fallback which would offer unrelated local files.
		if (specialMode && candidates.isEmpty() && !partial.isEmpty()) {
			candidates.add(partial);
		}

		inv.addAllCompleterValues(candidates);
		// When there is exactly one candidate, aesh escapes spaces for direct
		// insertion which mangles embedded tab descriptions. Strip the
		// description so the value is clean.
		if (candidates.size() == 1) {
			String single = candidates.iterator().next();
			int tab = single.indexOf('\t');
			String value;
			if (tab >= 0) {
				value = single.substring(0, tab);
				inv.clearCompleterValues();
				inv.addCompleterValue(value);
			} else {
				value = single;
			}
			// Don't append a space after directory, GAV separator, or
			// group prefix — the user will keep navigating
			if (value.endsWith("/") || value.endsWith("\\")
					|| value.endsWith(":") || value.endsWith(".")) {
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

	// ---- Navigation hints ----------------------------------------------

	/** URL prefixes that jbang can complete further. */
	private static final String[] URL_HINTS = {
			"https://github.com/",
			"https://gist.github.com/",
			"https://gitlab.com/",
			"https://bitbucket.org/",
	};

	/**
	 * When the input is empty or a short prefix, add hint candidates that teach the
	 * user about special completion modes. These are real, insertable values — not
	 * decorative text.
	 */
	private void addNavigationHints(Set<String> candidates, String partial) {
		if (partial.isEmpty()) {
			candidates.add(described("@", "Browse catalogs"));
		}
		// Offer URL hints that match the partial prefix
		String lowerPartial = partial.toLowerCase();
		for (String url : URL_HINTS) {
			if (url.toLowerCase().startsWith(lowerPartial) && !url.equals(partial)) {
				candidates.add(described(url, "URL"));
			}
		}
		// When input contains a dot but hasn't triggered GAV mode yet (< 2 dots),
		// hint that appending ':' switches to Maven artifact completion.
		if (!partial.isEmpty() && partial.contains(".") && !partial.contains("/")
				&& !partial.contains(":") && !looksLikeGav(partial)) {
			candidates.add(described(partial + ":", "Maven artifact lookup"));
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
					candidates.add(described(prefix + name + "/", "Directory"));
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
					dev.jbang.catalog.Alias alias = merged.aliases.get(name);
					String desc = (alias != null && alias.description != null) ? alias.description : "Alias";
					candidates.add(described(name, desc));
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
					candidates.add(described("@" + name, "Catalog"));
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
				candidates.add(described(alias + "@" + catalogName, "Alias in " + catalogName));
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
					candidates.add(described("@" + name, "Catalog"));
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
	/**
	 * Returns true if the partial could plausibly be the start of a Maven groupId
	 * (letters, digits, dots, hyphens — no path separators, colons, or URL
	 * schemes).
	 */
	private static boolean looksLikeGroupIdPrefix(String partial) {
		if (partial.contains("/") || partial.contains("\\") || partial.contains(":")) {
			return false;
		}
		// Must look like a Java package prefix: letters, digits, dots, hyphens
		for (int i = 0; i < partial.length(); i++) {
			char c = partial.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '.' && c != '-' && c != '_') {
				return false;
			}
		}
		return partial.length() >= 2;
	}

	/**
	 * Returns true if the candidate set contains at least one "real" candidate (not
	 * just navigation hints like @, https://, or the Maven colon hint).
	 */
	private static boolean hasRealCandidates(Set<String> candidates) {
		for (String c : candidates) {
			int tab = c.indexOf('\t');
			String value = tab >= 0 ? c.substring(0, tab) : c;
			// Skip navigation hints
			if (value.equals("@") || value.startsWith("https://") || value.endsWith(":")) {
				continue;
			}
			return true;
		}
		return false;
	}

	static boolean looksLikeGav(String partial) {
		if (partial.contains(":")) {
			// URLs like https://... are not GAV coordinates
			if (partial.contains("://")) {
				return false;
			}
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
					candidates.add(described(groupSoFar + ":", "Maven artifacts"));
				}
				if (hasSubGroups(entry)) {
					// Has deeper groupId segments — offer with trailing dot
					candidates.add(described(groupSoFar + ".", "Maven group"));
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
					candidates.add(described(groupId + ":" + name + ":", "Maven artifact"));
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
					candidates.add(described(groupId + ":" + artifactId + ":" + name, "Version"));
				}
			}
		} catch (IOException e) {
			// best-effort
		}
	}

	// ---- Remote Maven Central completion --------------------------------

	/** Timeout for Maven Central search during completion (shorter than normal). */
	private static final int SEARCH_TIMEOUT_MS = 3000;

	/**
	 * Search Maven Central for GAV candidates when the local repository has no
	 * matches. Only called on double-tap to avoid latency on casual TAB.
	 */
	private void completeGavRemote(Set<String> candidates, String partial) {
		try {
			String[] parts = partial.split(":", -1);
			// Build the Solr query based on what the user has typed
			String solrQuery;
			if (parts.length >= 3) {
				// groupId:artifactId:versionPrefix — exact g+a, list versions
				solrQuery = "g:" + parts[0] + " AND a:" + parts[1];
			} else if (parts.length == 2) {
				// groupId:artifactPrefix — exact g, partial a
				solrQuery = "g:" + parts[0] + (parts[1].isEmpty() ? "" : " AND a:" + parts[1] + "*");
			} else {
				// groupId prefix only
				solrQuery = "g:" + partial + "*";
			}
			String searchUrl = "https://search.maven.org/solrsearch/select?rows=20&q="
					+ java.net.URLEncoder.encode(solrQuery, "UTF-8");
			if (parts.length >= 3) {
				searchUrl += "&core=gav";
			}

			java.net.URL url = new java.net.URL(searchUrl);
			java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(SEARCH_TIMEOUT_MS);
			conn.setReadTimeout(SEARCH_TIMEOUT_MS);
			conn.setRequestProperty("User-Agent", "jbang");
			if (conn.getResponseCode() != 200) {
				conn.disconnect();
				return;
			}
			String body;
			try (java.io.BufferedReader rdr = new java.io.BufferedReader(
					new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = rdr.readLine()) != null) {
					sb.append(line);
				}
				body = sb.toString();
			}
			conn.disconnect();

			// Parse the Solr response
			com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
			com.google.gson.JsonArray docs = json.getAsJsonObject("response").getAsJsonArray("docs");

			// Collect existing candidate values (before tab) to avoid duplicates
			Set<String> existing = new HashSet<>();
			for (String c : candidates) {
				int tab = c.indexOf('\t');
				existing.add(tab >= 0 ? c.substring(0, tab) : c);
			}

			for (int i = 0; i < docs.size(); i++) {
				com.google.gson.JsonObject doc = docs.get(i).getAsJsonObject();
				String g = doc.has("g") ? doc.get("g").getAsString() : null;
				String a = doc.has("a") ? doc.get("a").getAsString() : null;
				String v = doc.has("v") ? doc.get("v").getAsString() : null;
				if (g == null)
					continue;

				String value;
				if (parts.length >= 3 && a != null && v != null) {
					value = g + ":" + a + ":" + v;
				} else if (parts.length == 2 && a != null) {
					value = g + ":" + a + ":";
				} else {
					// GroupId prefix — offer groupId: as candidate
					value = g + ":";
				}
				if (!existing.contains(value)) {
					candidates.add(described(value, "Maven Central"));
				}
			}
		} catch (Exception e) {
			// best-effort — network errors are expected
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

	// ---- Double-tab detection ------------------------------------------

	/**
	 * Checks whether the current completion is a "double-tap" — a second TAB press
	 * on the same partial input within {@link #DOUBLE_TAB_THRESHOLD_MS}. This
	 * allows heavier/slower completions (e.g. remote lookups) to be gated behind an
	 * explicit second press.
	 */
	static boolean isDoubleTap(String partial) {
		try {
			Path file = getLastCompleteFile();
			if (!Files.exists(file)) {
				return false;
			}
			long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
			if (age > DOUBLE_TAB_THRESHOLD_MS) {
				return false;
			}
			String previous = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
			return previous.equals(partial);
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Records the current completion invocation so a subsequent TAB can be detected
	 * as a double-tap.
	 */
	private static void recordCompletion(String partial) {
		try {
			Path file = getLastCompleteFile();
			Files.createDirectories(file.getParent());
			Files.write(file, partial.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			// best-effort — ignore
		}
	}

	/** Exposed for testing. */
	static void recordCompletionForTest(String partial) {
		recordCompletion(partial);
	}

	private static Path getLastCompleteFile() {
		return Settings.getCacheDir(false).resolve(LAST_COMPLETE_FILE);
	}
}
