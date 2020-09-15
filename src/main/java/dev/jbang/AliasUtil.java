package dev.jbang;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import picocli.CommandLine;

public class AliasUtil {
	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";
	public static final String JBANG_DOT_DIR = ".jbang";

	private static final String GITHUB_URL = "https://github.com/";
	private static final String GITLAB_URL = "https://gitlab.com/";
	private static final String BITBUCKET_URL = "https://bitbucket.org/";

	private static final String JBANG_CATALOG_REPO = "jbang-catalog";

	static Map<Path, Aliases> catalogCache = new HashMap<>();

	public static class Alias {
		@SerializedName(value = "script-ref", alternate = { "scriptRef" })
		public final String scriptRef;
		public final String description;
		public final List<String> arguments;
		public final Map<String, String> properties;
		public transient Aliases aliases;

		public Alias(String scriptRef, String description, List<String> arguments, Map<String, String> properties,
				Aliases aliases) {
			this.scriptRef = scriptRef;
			this.description = description;
			this.arguments = arguments;
			this.properties = properties;
			this.aliases = aliases;
		}

		/**
		 * This method returns the scriptRef of the Alias with all contextual modifiers
		 * like baseRefs and current working directories applied.
		 */
		public String resolve(Path cwd) {
			if (cwd == null) {
				cwd = getCwd();
			}
			String baseRef = aliases.baseRef;
			String ref = scriptRef;
			if (baseRef != null && !isAbsoluteRef(ref)) {
				if (!baseRef.endsWith("/")) {
					baseRef += "/";
				}
				if (ref.startsWith("./")) {
					baseRef += ref.substring(2);
				} else {
					baseRef += ref;
				}
				ref = baseRef;
			}
			if (!isAbsoluteRef(ref)) {
				Path script = aliases.catalogFile.getParent().resolve(ref);
				if (script.startsWith(cwd) || cwd.startsWith(script)
						|| !cwd.relativize(script).startsWith("../../..")) {
					script = cwd.relativize(script);
				}
				ref = script.toString();
			}
			return ref;
		}
	}

	public static class Aliases {
		public final Map<String, Alias> aliases = new HashMap<>();
		@SerializedName(value = "base-ref", alternate = { "baseRef" })
		public final String baseRef;
		public final String description;
		public transient Path catalogFile;

		public Aliases(String baseRef, String description, Path catalogFile) {
			this.baseRef = baseRef;
			this.description = description;
			this.catalogFile = catalogFile;
		}

		public Aliases(String baseRef, String description, Path catalogFile, Map<String, Alias> aliases) {
			this.baseRef = baseRef;
			this.description = description;
			this.catalogFile = catalogFile;
			this.aliases.putAll(aliases);
		}
	}

	public static class Catalog {
		@SerializedName(value = "catalog-ref", alternate = { "catalogRef" })
		public final String catalogRef;
		public final String description;

		Catalog(String catalogRef, String description) {
			this.catalogRef = catalogRef;
			this.description = description;
		}
	}

	public static class CatalogInfo {
		Map<String, Catalog> catalogs = new HashMap<>();
	}

	/**
	 * Returns an Alias object for the given name
	 *
	 * @param cwd       The current working directory (leave null to auto detect)
	 * @param aliasName The name of an Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Alias getAlias(Path cwd, String aliasName) {
		return getAlias(cwd, aliasName, null, null);
	}

	/**
	 * Returns an Alias object for the given name with the given arguments and
	 * properties applied to it. Or null if no alias with that name could be found.
	 *
	 * @param aliasName  The name of an Alias
	 * @param arguments  Optional arguments to apply to the Alias
	 * @param properties Optional properties to apply to the Alias
	 * @return An Alias object or null if no alias was found
	 */
	public static Alias getAlias(Path cwd, String aliasName, List<String> arguments, Map<String, String> properties) {
		HashSet<String> names = new HashSet<>();
		Alias alias = new Alias(null, null, arguments, properties, null);
		Alias result = mergeAliases(cwd, alias, aliasName, names);
		return result.scriptRef != null ? result : null;
	}

	private static Alias mergeAliases(Path cwd, Alias a1, String name, HashSet<String> names) {
		if (names.contains(name)) {
			throw new RuntimeException("Encountered alias loop on '" + name + "'");
		}
		String[] parts = name.split("@");
		if (parts.length > 2 || parts[0].isEmpty()) {
			throw new RuntimeException("Invalid alias name '" + name + "'");
		}
		Alias a2;
		if (parts.length == 1) {
			a2 = getLocalAlias(cwd, name);
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid alias name '" + name + "'");
			}
			a2 = getCatalogAlias(parts[1], parts[0]);
		}
		if (a2 != null) {
			names.add(name);
			a2 = mergeAliases(cwd, a2, a2.scriptRef, names);
			List<String> args = a1.arguments != null && !a1.arguments.isEmpty() ? a1.arguments : a2.arguments;
			Map<String, String> props = a1.properties != null && !a1.properties.isEmpty() ? a1.properties
					: a2.properties;
			Aliases aliases = a2.aliases != null ? a2.aliases : a1.aliases;
			return new Alias(a2.scriptRef, null, args, props, aliases);
		} else {
			return a1;
		}
	}

	/**
	 * Returns the given Alias from the local file system
	 *
	 * @param cwd       The current working directory (leave null to auto detect)
	 * @param aliasName The name of an Alias
	 * @return An Alias object
	 */
	private static Alias getLocalAlias(Path cwd, String aliasName) {
		Aliases aliases = getAllAliasesFromLocalCatalogs(cwd);
		return aliases.aliases.getOrDefault(aliasName, null);
	}

	/**
	 * Returns the given Alias from the given registered Catalog
	 * 
	 * @param catalogName The name of a registered Catalog
	 * @param aliasName   The name of an Alias
	 * @return An Alias object
	 */
	private static Alias getCatalogAlias(String catalogName, String aliasName) {
		Aliases aliases = getCatalogAliasesByName(catalogName, false);
		Alias alias = aliases.aliases.get(aliasName);
		if (alias == null) {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "No alias found with name '" + aliasName + "'");
		}
		return alias;
	}

	/**
	 * Load a Catalog's aliases given the name of a previously registered Catalog
	 * 
	 * @param catalogName The name of a registered
	 * @param updateCache Set to true to ignore cached values
	 * @return An Aliases object
	 */
	public static Aliases getCatalogAliasesByName(String catalogName, boolean updateCache) {
		Catalog catalog = getCatalog(catalogName);
		if (catalog != null) {
			return getCatalogAliasesByRef(catalog.catalogRef, updateCache);
		} else {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Unknown catalog '" + catalogName + "'");
		}
	}

	private static Catalog getCatalog(String catalogName) {
		Catalog catalog = Settings.getCatalogs().get(catalogName);
		if (catalog == null) {
			Util.verboseMsg("Local catalog '" + catalogName + "' not found, trying implicit catalogs...");
			ImplicitCatalogRef icr = ImplicitCatalogRef.parse(catalogName);
			Optional<String> url;
			url = chain(
					() -> tryDownload(icr.url(GITHUB_URL, "/blob/")),
					() -> icr.isPossibleCommit() ? tryDownload(icr.url(GITHUB_URL, "/blob/")) : Optional.empty(),
					() -> tryDownload(icr.url(GITLAB_URL, "/-/blob/")),
					() -> icr.isPossibleCommit() ? tryDownload(icr.url(GITLAB_URL, "/-/blob/")) : Optional.empty(),
					() -> tryDownload(icr.url(BITBUCKET_URL, "/src/")),
					() -> icr.isPossibleCommit() ? tryDownload(icr.url(BITBUCKET_URL, "/src/")) : Optional.empty())
																													.findFirst();
			if (url.isPresent()) {
				Aliases aliases = AliasUtil.getCatalogAliasesByRef(url.get(), false);
				catalog = Settings.addCatalog(catalogName, url.get(), aliases.description);
			}
		}
		return catalog;
	}

	private static class ImplicitCatalogRef {
		final String org;
		final String repo;
		final String ref; // Branch or Commit
		final String path;

		private ImplicitCatalogRef(String org, String repo, String ref, String path) {
			this.org = org;
			this.repo = repo;
			this.ref = ref;
			this.path = path;
		}

		public boolean isPossibleCommit() {
			return ref.matches("[0-9a-f]{5,40}");
		}

		public String url(String host, String infix) {
			return host + org + "/" + repo + infix + ref + "/" + path + JBANG_CATALOG_JSON;
		}

		public static ImplicitCatalogRef parse(String name) {
			String[] parts = name.split("~", 2);
			String path;
			if (parts.length == 2) {
				path = parts[1] + "/";
			} else {
				path = "";
			}
			String[] names = parts[0].split("/");
			if (names.length > 3) {
				throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Invalid catalog name '" + name + "'");
			}
			String org = names[0];
			String repo;
			if (names.length >= 2 && !names[1].isEmpty()) {
				repo = names[1];
			} else {
				repo = JBANG_CATALOG_REPO;
			}
			String ref;
			if (names.length == 3 && !names[2].isEmpty()) {
				ref = names[2];
			} else {
				ref = "master";
			}
			return new ImplicitCatalogRef(org, repo, ref, path);
		}
	}

	private static Optional<String> tryDownload(String url) {
		try {
			getCatalogAliasesByRef(url, false);
			Util.verboseMsg("Catalog found at " + url);
			return Optional.of(url);
		} catch (Exception ex) {
			Util.verboseMsg("No catalog found at " + url, ex);
			return Optional.empty();
		}
	}

	/**
	 * Will either return the given catalog or search for the nearest catalog
	 * starting from cwd.
	 * 
	 * @param cwd     The folder to use as a starting point for getting the nearest
	 *                catalog
	 * @param catalog The catalog to return or null to return the nearest catalog
	 * @return Path to a catalog
	 */
	public static Path getCatalog(Path cwd, Path catalog) {
		if (catalog == null) {
			catalog = findNearestLocalCatalog(cwd);
			if (catalog == null) {
				catalog = Settings.getAliasesFile();
			}
		}
		return catalog;
	}

	/**
	 * Adds a new alias to the nearest catalog
	 * 
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name The name of the new alias
	 */
	public static void addNearestAlias(Path cwd, String name, String scriptRef, String description,
			List<String> arguments,
			Map<String, String> properties) {
		Path catalog = getCatalog(cwd, null);
		addAlias(catalog, name, scriptRef, description, arguments, properties);
	}

	/**
	 * Adds a new alias to the given catalog
	 * 
	 * @param catalog Path to catalog file
	 * @param name    The name of the new alias
	 */
	public static void addAlias(Path catalog, String name, String scriptRef, String description, List<String> arguments,
			Map<String, String> properties) {
		Aliases aliases = getAliasesFromCatalogFile(catalog, true);
		aliases.aliases.put(name, new Alias(scriptRef, description, arguments, properties, aliases));
		try {
			writeAliasesToCatalogFile(catalog, aliases);
		} catch (IOException ex) {
			Util.warnMsg("Unable to add alias: " + ex.getMessage());
		}
	}

	/**
	 * Finds the nearest catalog file that contains an alias with the given name and
	 * removes it
	 * 
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name Name of alias to remove
	 */
	public static void removeNearestAlias(Path cwd, String name) {
		Path catalog = findNearestLocalCatalogWithAlias(cwd, name);
		if (catalog == null) {
			catalog = Settings.getAliasesFile();
		}
		removeAlias(catalog, name);
	}

	/**
	 * Remove alias from specified catalog file
	 * 
	 * @param catalog Path to catalog file
	 * @param name    Name of alias to remove
	 */
	public static void removeAlias(Path catalog, String name) {
		Aliases aliases = getAliasesFromCatalogFile(catalog, true);
		if (aliases.aliases.containsKey(name)) {
			aliases.aliases.remove(name);
			try {
				writeAliasesToCatalogFile(catalog, aliases);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
		}
	}

	/**
	 * Load a Catalog's aliases given a file path or URL
	 * 
	 * @param catalogRef  File path or URL to a Catalog JSON file. If this does not
	 *                    end in .json then jbang-catalog.json will be appended to
	 *                    the end.
	 * @param updateCache Set to true to ignore cached values
	 * @return An Aliases object
	 */
	public static Aliases getCatalogAliasesByRef(String catalogRef, boolean updateCache) {
		if (!catalogRef.endsWith(".json")) {
			if (!catalogRef.endsWith("/")) {
				catalogRef += "/";
			}
			catalogRef += JBANG_CATALOG_JSON;
		}
		Path catalogPath = null;
		try {
			catalogPath = Util.obtainFile(catalogRef, updateCache);
			Aliases aliases = getAliasesFromCatalogFile(catalogPath, updateCache);
			int p = catalogRef.lastIndexOf('/');
			if (p > 0) {
				String baseRef = aliases.baseRef;
				String catalogBaseRef = catalogRef.substring(0, p);
				if (baseRef != null) {
					if (!baseRef.startsWith("/") && !baseRef.contains(":")) {
						baseRef = catalogBaseRef + "/" + baseRef;
					}
				} else {
					baseRef = catalogBaseRef;
				}
				aliases = new Aliases(baseRef, aliases.description, aliases.catalogFile, aliases.aliases);
			}
			return aliases;
		} catch (IOException | JsonParseException ex) {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE,
					"Unable to download catalog: " + catalogRef + " via " + catalogPath, ex);
		}
	}

	public static Aliases getAllAliasesFromLocalCatalogs(Path cwd) {
		if (cwd == null) {
			cwd = getCwd();
		}
		Aliases result = new Aliases(null, null, null);
		result.aliases.putAll(getAliasesFromCatalogFile(Settings.getAliasesFile(), false).aliases);
		allAliasesFromLocalCatalogs(cwd, result);
		return result;
	}

	private static void allAliasesFromLocalCatalogs(Path dir, Aliases result) {
		if (dir.getParent() != null) {
			allAliasesFromLocalCatalogs(dir.getParent(), result);
		}
		Path catalog = dir.resolve(JBANG_DOT_DIR).resolve(JBANG_CATALOG_JSON);
		if (Files.isRegularFile(catalog) && Files.isReadable(catalog)) {
			result.aliases.putAll(getAliasesFromCatalogFile(catalog, false).aliases);
		}
		catalog = dir.resolve(JBANG_CATALOG_JSON);
		if (Files.isRegularFile(catalog) && Files.isReadable(catalog)) {
			result.aliases.putAll(getAliasesFromCatalogFile(catalog, false).aliases);
		}
	}

	private static Path findNearestLocalCatalog(Path dir) {
		if (dir == null) {
			dir = getCwd();
		}
		while (dir != null) {
			Path catalog = dir.resolve(JBANG_CATALOG_JSON);
			if (Files.isRegularFile(catalog) && Files.isReadable(catalog)) {
				return catalog;
			}
			catalog = dir.resolve(JBANG_DOT_DIR).resolve(JBANG_CATALOG_JSON);
			if (Files.isRegularFile(catalog) && Files.isReadable(catalog)) {
				return catalog;
			}
			dir = dir.getParent();
		}
		return null;
	}

	public static Path findNearestLocalCatalogWithAlias(Path dir, String aliasName) {
		if (dir == null) {
			dir = getCwd();
		}
		while (dir != null) {
			Path catalog = dir.resolve(JBANG_CATALOG_JSON);
			if (Files.isRegularFile(catalog) && Files.isReadable(catalog)) {
				Aliases aliases = getAliasesFromCatalogFile(catalog, false);
				if (aliases.aliases.containsKey(aliasName)) {
					return catalog;
				}
			}
			catalog = dir.resolve(JBANG_DOT_DIR).resolve(JBANG_CATALOG_JSON);
			if (Files.isRegularFile(catalog) && Files.isReadable(catalog)) {
				Aliases aliases = getAliasesFromCatalogFile(catalog, false);
				if (aliases.aliases.containsKey(aliasName)) {
					return catalog;
				}
			}
			dir = dir.getParent();
		}
		return null;
	}

	public static Aliases getAliasesFromCatalogFile(Path catalogPath, boolean updateCache) {
		Aliases aliases;
		if (updateCache || !catalogCache.containsKey(catalogPath)) {
			aliases = readAliasesFromCatalogFile(catalogPath);
			aliases.catalogFile = catalogPath;
			catalogCache.put(catalogPath, aliases);
		} else {
			aliases = catalogCache.get(catalogPath);
		}
		return aliases;
	}

	static private Path scriptParent(Path script) {
		Path parent = script.getParent();
		if (parent.getFileName().toString().equals(JBANG_DOT_DIR)) {
			parent = parent.getParent();
		}
		return parent;
	}

	static Aliases readAliasesFromCatalogFile(Path catalogPath) {
		Aliases aliases = new Aliases(null, null, null);
		if (Files.isRegularFile(catalogPath)) {
			try (Reader in = Files.newBufferedReader(catalogPath)) {
				Gson parser = new Gson();
				Aliases as = parser.fromJson(in, Aliases.class);
				if (as != null) {
					aliases = as;
					// Validate the result (Gson can't do this)
					check(aliases.aliases != null, "Missing required attribute 'aliases'");
					for (String aliasName : aliases.aliases.keySet()) {
						Alias alias = aliases.aliases.get(aliasName);
						alias.aliases = aliases;
						check(alias.scriptRef != null, "Missing required attribute 'aliases.script-ref'");
					}
				}
			} catch (IOException e) {
				// Ignore errors
			}
		}
		return aliases;
	}

	static void writeAliasesToCatalogFile(Path catalogPath, Aliases aliases) throws IOException {
		try (Writer out = Files.newBufferedWriter(catalogPath)) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(aliases, out);
		}
	}

	static void check(boolean ok, String message) {
		if (!ok) {
			throw new JsonParseException(message);
		}
	}

	static CatalogInfo readCatalogInfo(Path catalogsPath) {
		CatalogInfo info;
		if (Files.isRegularFile(catalogsPath)) {
			try (Reader in = Files.newBufferedReader(catalogsPath)) {
				Gson parser = new Gson();
				info = parser.fromJson(in, CatalogInfo.class);
				if (info != null) {
					// Validate the result (Gson can't do this)
					check(info.catalogs != null, "Missing required attribute 'catalogs'");
					for (String catName : info.catalogs.keySet()) {
						Catalog cat = info.catalogs.get(catName);
						check(cat.catalogRef != null, "Missing required attribute 'catalogs.catalogRef'");
					}
				} else {
					info = new CatalogInfo();
				}
			} catch (IOException e) {
				info = new CatalogInfo();
			}
		} else {
			info = new CatalogInfo();
		}
		return info;
	}

	static void writeCatalogInfo(Path catalogPath) throws IOException {
		try (Writer out = Files.newBufferedWriter(catalogPath)) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(Settings.getCatalogInfo(), out);
		}
	}

	private static boolean isAbsoluteRef(String ref) {
		return ref.startsWith("/") || ref.contains(":");
	}

	@SafeVarargs
	public static <T> Stream<T> chain(Supplier<Optional<T>>... suppliers) {
		return Arrays	.stream(suppliers)
						.map(Supplier::get)
						.flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty));
	}

	private static Path getCwd() {
		return Paths.get("").toAbsolutePath();
	}
}
