package dev.jbang;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

public class AliasUtil {
	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";
	public static final String JBANG_IMPLICIT_CATALOG_JSON = "implicit-catalog.json";

	private static final String GITHUB_URL = "https://github.com/";
	private static final String GITLAB_URL = "https://gitlab.com/";
	private static final String BITBUCKET_URL = "https://bitbucket.org/";

	private static final String JBANG_CATALOG_REPO = "jbang-catalog";

	private static final String DEFAULT_REF = "HEAD"; // HEAD at least on github gives you latest commit on default
														// branch

	static Map<Path, Catalog> catalogCache = new HashMap<>();

	public static class Alias {
		@SerializedName(value = "script-ref", alternate = { "scriptRef" })
		public final String scriptRef;
		public final String description;
		public final List<String> arguments;
		public final Map<String, String> properties;
		public transient Catalog catalog;

		public Alias(String scriptRef, String description, List<String> arguments, Map<String, String> properties,
				Catalog catalog) {
			this.scriptRef = scriptRef;
			this.description = description;
			this.arguments = arguments;
			this.properties = properties;
			this.catalog = catalog;
		}

		/**
		 * This method returns the scriptRef of the Alias with all contextual modifiers
		 * like baseRefs and current working directories applied.
		 */
		public String resolve(Path cwd) {
			if (cwd == null) {
				cwd = Util.getCwd();
			}
			String baseRef = catalog.getScriptBase();
			String ref = scriptRef;
			if (!isAbsoluteRef(ref)) {
				ref = baseRef + "/" + ref;
			}
			if (!isRemoteRef(ref)) {
				Path script = Paths.get(ref).normalize();
				if (cwd.getRoot().equals(script.getRoot())) {
					script = cwd.relativize(script);
				} else {
					script = script.toAbsolutePath();
				}
				ref = script.toString();
			}
			return ref;
		}
	}

	public static class Catalog {
		public Map<String, CatalogRef> catalogs = new HashMap<>();
		public final Map<String, Alias> aliases = new HashMap<>();
		@SerializedName(value = "base-ref", alternate = { "baseRef" })
		public final String baseRef;
		public final String description;
		public transient Path catalogFile;

		public Catalog(String baseRef, String description, Path catalogFile) {
			this.baseRef = baseRef;
			this.description = description;
			this.catalogFile = catalogFile;
		}

		public Catalog(String baseRef, String description, Path catalogFile, Map<String, Alias> aliases) {
			this.baseRef = baseRef;
			this.description = description;
			this.catalogFile = catalogFile;
			aliases.entrySet().forEach(e -> {
				Alias a = e.getValue();
				this.aliases.put(e.getKey(), new Alias(a.scriptRef, a.description, a.arguments, a.properties, this));
			});
		}

		/**
		 * Returns in all cases the absolute base reference that can be used to resolve
		 * an Alias' script location. The result will either be a URL or an absolute
		 * path.
		 * 
		 * @return A string to be used as the base for Alias script locations
		 */
		public String getScriptBase() {
			Path result;
			if (baseRef != null) {
				if (!isRemoteRef(baseRef)) {
					Path base = Paths.get(baseRef);
					if (!base.isAbsolute()) {
						result = catalogFile.getParent().resolve(base);
					} else {
						result = Paths.get(baseRef);
					}
				} else {
					if (baseRef.endsWith("/")) {
						return baseRef.substring(0, baseRef.length() - 1);
					} else {
						return baseRef;
					}
				}
			} else {
				result = catalogFile.getParent();
			}
			return result.normalize().toString();
		}
	}

	public static class CatalogRef {
		@SerializedName(value = "catalog-ref", alternate = { "catalogRef" })
		public final String catalogRef;
		public final String description;

		CatalogRef(String catalogRef, String description) {
			this.catalogRef = catalogRef;
			this.description = description;
		}
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
			Catalog catalog = a2.catalog != null ? a2.catalog : a1.catalog;
			return new Alias(a2.scriptRef, null, args, props, catalog);
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
		Catalog catalog = getMergedCatalog(cwd, false);
		return catalog.aliases.getOrDefault(aliasName, null);
	}

	/**
	 * Returns the given Alias from the given registered Catalog
	 * 
	 * @param catalogName The name of a registered Catalog
	 * @param aliasName   The name of an Alias
	 * @return An Alias object
	 */
	private static Alias getCatalogAlias(String catalogName, String aliasName) {
		Catalog catalog = getCatalogByName(null, catalogName, false);
		Alias alias = catalog.aliases.get(aliasName);
		if (alias == null) {
			throw new ExitException(EXIT_INVALID_INPUT, "No alias found with name '" + aliasName + "'");
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
	public static Catalog getCatalogByName(Path cwd, String catalogName, boolean updateCache) {
		CatalogRef catalogRef = getCatalogRef(cwd, catalogName);
		if (catalogRef != null) {
			return getCatalogByRef(catalogRef.catalogRef, updateCache);
		} else {
			throw new ExitException(EXIT_INVALID_INPUT, "Unknown catalog '" + catalogName + "'");
		}
	}

	private static CatalogRef getCatalogRef(Path cwd, String catalogName) {
		Catalog catalog = getMergedCatalog(cwd, true);
		CatalogRef catalogRef = catalog.catalogs.get(catalogName);
		if (catalogRef == null) {
			Util.verboseMsg("Local catalog '" + catalogName + "' not found, trying implicit catalogs...");
			Optional<String> url = getImplicitCatalogUrl(catalogName);
			if (url.isPresent()) {
				Catalog implicitCatalog = AliasUtil.getCatalogByRef(url.get(), false);
				catalogRef = addCatalog(cwd, Settings.getUserImplicitCatalogFile(), catalogName, url.get(),
						implicitCatalog.description);
			}
		}
		return catalogRef;
	}

	private static Optional<String> getImplicitCatalogUrl(String catalogName) {
		ImplicitCatalogRef icr = ImplicitCatalogRef.parse(catalogName);
		Optional<String> url = chain(
				() -> tryDownload(icr.url(GITHUB_URL, "/blob/")),
				() -> icr.isPossibleCommit() ? tryDownload(icr.url(GITHUB_URL, "/blob/")) : Optional.empty(),
				() -> tryDownload(icr.url(GITLAB_URL, "/-/blob/")),
				() -> icr.isPossibleCommit() ? tryDownload(icr.url(GITLAB_URL, "/-/blob/")) : Optional.empty(),
				() -> tryDownload(icr.url(BITBUCKET_URL, "/src/")),
				() -> icr.isPossibleCommit() ? tryDownload(icr.url(BITBUCKET_URL, "/src/")) : Optional.empty())
																												.findFirst();
		return url;
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
				throw new ExitException(EXIT_INVALID_INPUT, "Invalid catalog name '" + name + "'");
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
				ref = DEFAULT_REF;
			}
			return new ImplicitCatalogRef(org, repo, ref, path);
		}
	}

	private static Optional<String> tryDownload(String url) {
		try {
			getCatalogByRef(url, false);
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
	public static Path getCatalogFile(Path cwd, Path catalog) {
		if (catalog == null) {
			catalog = findNearestLocalCatalog(cwd);
			if (catalog == null) {
				catalog = Settings.getUserCatalogFile();
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
	public static Path addNearestAlias(Path cwd, String name, String scriptRef, String description,
			List<String> arguments,
			Map<String, String> properties) {
		Path catalogFile = getCatalogFile(cwd, null);
		addAlias(cwd, catalogFile, name, scriptRef, description, arguments, properties);
		return catalogFile;
	}

	/**
	 * Adds a new alias to the given catalog
	 * 
	 * @param catalogFile Path to catalog file
	 * @param name        The name of the new alias
	 */
	public static Alias addAlias(Path cwd, Path catalogFile, String name, String scriptRef, String description,
			List<String> arguments,
			Map<String, String> properties) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = getCatalog(catalogFile, true);
		if (!isRemoteRef(scriptRef) && !isValidCatalogReference(scriptRef)) {
			// If the scriptRef points to an existing file on the local filesystem
			// or it's obviously a path (but not an absolute path) we'll make it
			// relative to the location of the catalog we're adding the alias to.
			Path script = cwd.resolve(scriptRef).normalize();
			String baseRef = catalog.getScriptBase();
			if (!isAbsoluteRef(scriptRef)
					&& !isRemoteRef(baseRef)
					&& (!isValidName(scriptRef) || Files.isRegularFile(script))) {
				Path base = Paths.get(baseRef);
				if (base.getRoot().equals(script.getRoot())) {
					scriptRef = base.relativize(script.toAbsolutePath()).normalize().toString();
				} else {
					scriptRef = script.toAbsolutePath().normalize().toString();
				}
			}
			if (!isRemoteRef(baseRef)
					&& !isValidName(scriptRef)
					&& !Files.isRegularFile(script)) {
				throw new IllegalArgumentException("Source file not found: " + scriptRef);
			}
		}
		Alias alias = new Alias(scriptRef, description, arguments, properties, catalog);
		catalog.aliases.put(name, alias);
		try {
			writeCatalog(catalogFile, catalog);
			return alias;
		} catch (IOException ex) {
			Util.warnMsg("Unable to add alias: " + ex.getMessage());
			return null;
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
			catalog = Settings.getUserCatalogFile();
		}
		removeAlias(catalog, name);
	}

	/**
	 * Remove alias from specified catalog file
	 * 
	 * @param catalogFile Path to catalog file
	 * @param name        Name of alias to remove
	 */
	public static void removeAlias(Path catalogFile, String name) {
		Catalog catalog = getCatalog(catalogFile, true);
		if (catalog.aliases.containsKey(name)) {
			catalog.aliases.remove(name);
			try {
				writeCatalog(catalogFile, catalog);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
		}
	}

	/**
	 * Load a Catalog's aliases given a file path or URL
	 *
	 * @param catalogRef  File path, full URL or implicit Catalog reference to a
	 *                    Catalog.
	 * @param updateCache Set to true to ignore cached values
	 * @return A Catalog object
	 */
	public static CatalogRef getCatalogRefByRefOrImplicit(String catalogRef, boolean updateCache) {
		if (isAbsoluteRef(catalogRef) || Files.isRegularFile(Paths.get(catalogRef))) {
			Catalog cat = getCatalogByRef(catalogRef, updateCache);
			return new CatalogRef(catalogRef, cat.description);
		} else {
			Optional<String> url = getImplicitCatalogUrl(catalogRef);
			if (!url.isPresent()) {
				throw new ExitException(EXIT_UNEXPECTED_STATE,
						"Unable to locate catalog: " + catalogRef);
			}
			Catalog cat = AliasUtil.getCatalogByRef(url.get(), false);
			return new CatalogRef(url.get(), cat.description);
		}
	}

	/**
	 * Load a Catalog's aliases given a file path or URL
	 * 
	 * @param catalogRef  File path or URL to a Catalog JSON file. If this does not
	 *                    end in .json then jbang-catalog.json will be appended to
	 *                    the end.
	 * @param updateCache Set to true to ignore cached values
	 * @return A Catalog object
	 */
	public static Catalog getCatalogByRef(String catalogRef, boolean updateCache) {
		if (!catalogRef.endsWith(".json")) {
			if (!catalogRef.endsWith("/")) {
				catalogRef += "/";
			}
			catalogRef += JBANG_CATALOG_JSON;
		}
		Path catalogPath = null;
		try {
			catalogPath = Util.obtainFile(catalogRef, updateCache);
			Util.verboseMsg(String.format("Obtained catalog from %s", catalogRef));
			Catalog catalog = getCatalog(catalogPath, updateCache);
			int p = catalogRef.lastIndexOf('/');
			if (p > 0) {
				String baseRef = catalog.baseRef;
				String catalogBaseRef = catalogRef.substring(0, p);
				if (baseRef != null) {
					if (!baseRef.startsWith("/") && !baseRef.contains(":")) {
						baseRef = catalogBaseRef + "/" + baseRef;
					}
				} else {
					baseRef = catalogBaseRef;
				}
				catalog = new Catalog(baseRef, catalog.description, catalog.catalogFile, catalog.aliases);
			}
			return catalog;
		} catch (IOException | JsonParseException ex) {
			throw new ExitException(EXIT_UNEXPECTED_STATE,
					"Unable to download catalog: " + catalogRef + " via " + catalogPath, ex);
		}
	}

	/**
	 * Returns a Catalog containing all the aliases from local catalog files merged
	 * into one. This follows the system where aliases that are "nearest" have
	 * priority.
	 * 
	 * @param cwd              The current working directory
	 * @param includeImplicits Determines if the implicit catalogs should be merged
	 *                         or not
	 * @return a Catalog object
	 */
	public static Catalog getMergedCatalog(Path cwd, boolean includeImplicits) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		Catalog result = new Catalog(null, null, null);
		if (includeImplicits) {
			mergeCatalog(Settings.getUserImplicitCatalogFile(), result);
		}
		mergeCatalog(Settings.getUserCatalogFile(), result);
		Util.mergeLocalFiles(cwd, Paths.get(JBANG_CATALOG_JSON), result, AliasUtil::mergeCatalog);
		return result;
	}

	private static void mergeCatalog(Path catalogFile, Catalog result) {
		Catalog catalog = getCatalog(catalogFile, false);
		for (CatalogRef ref : catalog.catalogs.values()) {
			Catalog cat = getCatalogByRef(ref.catalogRef, false);
			result.aliases.putAll(cat.aliases);
		}
		result.aliases.putAll(catalog.aliases);
		result.catalogs.putAll(catalog.catalogs);
	}

	private static Path findNearestLocalCatalog(Path dir) {
		return Util.findNearestFileWith(dir, JBANG_CATALOG_JSON, p -> true);
	}

	public static Path findNearestLocalCatalogWithAlias(Path dir, String aliasName) {
		return Util.findNearestFileWith(dir, JBANG_CATALOG_JSON, catalogFile -> {
			Catalog catalog = getCatalog(catalogFile, false);
			return catalog.aliases.containsKey(aliasName);
		});
	}

	public static Path findNearestLocalCatalogWithCatalog(Path dir, String catalogName) {
		return Util.findNearestFileWith(dir, JBANG_CATALOG_JSON, catalogFile -> {
			Catalog catalog = getCatalog(catalogFile, false);
			return catalog.catalogs.containsKey(catalogName);
		});
	}

	public static Catalog getCatalog(Path catalogPath, boolean updateCache) {
		Catalog catalog;
		if (updateCache || !catalogCache.containsKey(catalogPath)) {
			catalog = readCatalog(catalogPath);
			catalog.catalogFile = catalogPath.toAbsolutePath();
			catalogCache.put(catalogPath, catalog);
		} else {
			catalog = catalogCache.get(catalogPath);
		}
		return catalog;
	}

	static Catalog readCatalog(Path catalogPath) {
		Util.verboseMsg(String.format("Reading aliases from %s", catalogPath));
		Catalog catalog = new Catalog(null, null, null);
		if (Files.isRegularFile(catalogPath)) {
			try (Reader in = Files.newBufferedReader(catalogPath)) {
				Gson parser = new Gson();
				Catalog as = parser.fromJson(in, Catalog.class);
				if (as != null) {
					catalog = as;
					// Validate the result (Gson can't do this)
					if (catalog.catalogs == null) {
						catalog.catalogs = new HashMap<>();
					}
					for (String catName : catalog.catalogs.keySet()) {
						CatalogRef cat = catalog.catalogs.get(catName);
						check(cat.catalogRef != null, "Missing required attribute 'catalogs.catalogRef'");
					}
					check(catalog.aliases != null, "Missing required attribute 'aliases' in " + catalogPath);
					for (String aliasName : catalog.aliases.keySet()) {
						Alias alias = catalog.aliases.get(aliasName);
						alias.catalog = catalog;
						check(alias.scriptRef != null, "Missing required attribute 'aliases.script-ref'");
					}
				}
			} catch (IOException e) {
				// Ignore errors
			}
		}
		return catalog;
	}

	static void writeCatalog(Path catalogPath, Catalog catalog) throws IOException {
		try (Writer out = Files.newBufferedWriter(catalogPath)) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(catalog, out);
		}
	}

	static void check(boolean ok, String message) {
		if (!ok) {
			throw new JsonParseException(message);
		}
	}

	/**
	 * Adds a new alias to the nearest catalog
	 *
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name The name of the new alias
	 */
	public static Path addNearestCatalog(Path cwd, String name, String catalogRef, String description) {
		Path catalogFile = getCatalogFile(cwd, null);
		addCatalog(cwd, catalogFile, name, catalogRef, description);
		return catalogFile;
	}

	public static CatalogRef addCatalog(Path cwd, Path catalogFile, String name, String catalogRef,
			String description) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = getCatalog(catalogFile, true);
		try {
			Path cat = Paths.get(catalogRef);
			if (!cat.isAbsolute() && Files.isRegularFile(cat)) {
				catalogRef = cat.toAbsolutePath().toString();
			}
			if (!isAbsoluteRef(catalogRef)) {
				Optional<String> url = getImplicitCatalogUrl(catalogRef);
				if (url.isPresent()) {
					catalogRef = url.get();
				}
			}
		} catch (InvalidPathException ex) {
			// Ignore
		}
		CatalogRef ref = new CatalogRef(catalogRef, description);
		catalog.catalogs.put(name, ref);
		try {
			AliasUtil.writeCatalog(catalogFile, catalog);
			return ref;
		} catch (IOException ex) {
			Util.warnMsg("Unable to add catalog: " + ex.getMessage());
			return null;
		}
	}

	/**
	 * Finds the nearest catalog file that contains a catalog with the given name
	 * and removes it
	 *
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name Name of catalog to remove
	 */
	public static void removeNearestCatalog(Path cwd, String name) {
		Path catalog = findNearestLocalCatalogWithCatalog(cwd, name);
		if (catalog == null) {
			catalog = Settings.getUserCatalogFile();
		}
		removeCatalog(catalog, name);
	}

	public static void removeCatalog(Path catalogFile, String name) {
		Catalog catalog = getCatalog(catalogFile, true);
		if (catalog.catalogs.containsKey(name)) {
			catalog.catalogs.remove(name);
			try {
				AliasUtil.writeCatalog(catalogFile, catalog);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove catalog: " + ex.getMessage());
			}
		}
	}

	public static boolean isValidName(String name) {
		return name.matches("^[a-zA-Z][-\\w]*$");
	}

	public static boolean isValidCatalogReference(String name) {
		String[] parts = name.split("@");
		if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
			return false;
		}
		return isValidName(parts[0]);
	}

	private static boolean isAbsoluteRef(String ref) {
		return isRemoteRef(ref) || Paths.get(ref).isAbsolute();
	}

	private static boolean isRemoteRef(String ref) {
		return ref.startsWith("http:") || ref.startsWith("https:") || DependencyUtil.looksLikeAGav(ref);
	}

	@SafeVarargs
	public static <T> Stream<T> chain(Supplier<Optional<T>>... suppliers) {
		return Arrays	.stream(suppliers)
						.map(Supplier::get)
						.flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty));
	}

}
