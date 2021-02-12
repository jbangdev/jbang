package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.util.Util;

public class AliasUtil {
	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";
	public static final String JBANG_IMPLICIT_CATALOG_JSON = "implicit-catalog.json";
	public static final String JBANG_DOT_DIR = ".jbang";

	private static final String GITHUB_URL = "https://github.com/";
	private static final String GITLAB_URL = "https://gitlab.com/";
	private static final String BITBUCKET_URL = "https://bitbucket.org/";

	private static final String JBANG_CATALOG_REPO = "jbang-catalog";

	private static final String DEFAULT_REF = "HEAD"; // HEAD at least on github gives you latest commit on default
														// branch

	static final Map<Path, Catalog> catalogCache = new HashMap<>();

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
			return new Alias(a2.scriptRef, a2.description, args, props, catalog);
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
		Path catalogFile = findNearestCatalogWithAlias(cwd, aliasName);
		if (catalogFile != null) {
			Catalog catalog = getCatalog(catalogFile);
			return catalog.aliases.getOrDefault(aliasName, null);
		}
		return null;
	}

	/**
	 * Returns the given Alias from the given registered Catalog
	 * 
	 * @param catalogName The name of a registered Catalog
	 * @param aliasName   The name of an Alias
	 * @return An Alias object
	 */
	private static Alias getCatalogAlias(String catalogName, String aliasName) {
		Catalog catalog = getCatalogByName(null, catalogName);
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
	 * @return An Aliases object
	 */
	public static Catalog getCatalogByName(Path cwd, String catalogName) {
		CatalogRef catalogRef = getCatalogRef(cwd, catalogName);
		if (catalogRef != null) {
			return getCatalogByRef(catalogRef.catalogRef);
		} else {
			throw new ExitException(EXIT_INVALID_INPUT, "Unknown catalog '" + catalogName + "'");
		}
	}

	private static CatalogRef getCatalogRef(Path cwd, String catalogName) {
		CatalogRef catalogRef = null;
		Path catalogFile = findNearestCatalogWithCatalogRef(cwd, catalogName);
		if (catalogFile != null) {
			Catalog catalog = getCatalog(catalogFile);
			catalogRef = catalog.catalogs.get(catalogName);
		}
		if (catalogRef == null) {
			Util.verboseMsg("Local catalog '" + catalogName + "' not found, trying implicit catalogs...");
			Optional<String> url = getImplicitCatalogUrl(catalogName);
			if (url.isPresent()) {
				Catalog implicitCatalog = AliasUtil.getCatalogByRef(url.get());
				catalogRef = addCatalogRef(cwd, Settings.getUserImplicitCatalogFile(), catalogName, url.get(),
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
			getCatalogByRef(url);
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
	 *                catalog. NB: This method _specifically_ does _not_ return a
	 *                reference to the implicit catalog ever.
	 * @param catalog The catalog to return or null to return the nearest catalog
	 * @return Path to a catalog
	 */
	public static Path getCatalogFile(Path cwd, Path catalog) {
		if (catalog == null) {
			catalog = findNearestCatalog(cwd);
			if (catalog == null) {
				// This is here as a backup for when the user catalog doesn't
				// exist yet, because `findNearestCatalog()` only returns
				// existing files
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
		Catalog catalog = getCatalog(catalogFile);
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
		Path catalog = findNearestCatalogWithAlias(cwd, name);
		if (catalog != null) {
			removeAlias(catalog, name);
		}
	}

	/**
	 * Remove alias from specified catalog file
	 * 
	 * @param catalogFile Path to catalog file
	 * @param name        Name of alias to remove
	 */
	public static void removeAlias(Path catalogFile, String name) {
		Catalog catalog = getCatalog(catalogFile);
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
	 * @param catalogRef File path, full URL or implicit Catalog reference to a
	 *                   Catalog.
	 * @return A Catalog object
	 */
	public static CatalogRef getCatalogRefByRefOrImplicit(String catalogRef) {
		if (isAbsoluteRef(catalogRef) || Files.isRegularFile(Paths.get(catalogRef))) {
			Catalog cat = getCatalogByRef(catalogRef);
			return new CatalogRef(catalogRef, cat.description);
		} else {
			Optional<String> url = getImplicitCatalogUrl(catalogRef);
			if (!url.isPresent()) {
				throw new ExitException(EXIT_UNEXPECTED_STATE,
						"Unable to locate catalog: " + catalogRef);
			}
			Catalog cat = AliasUtil.getCatalogByRef(url.get());
			return new CatalogRef(url.get(), cat.description);
		}
	}

	/**
	 * Load a Catalog's aliases given a file path or URL
	 * 
	 * @param catalogRef File path or URL to a Catalog JSON file. If this does not
	 *                   end in .json then jbang-catalog.json will be appended to
	 *                   the end.
	 * @return A Catalog object
	 */
	public static Catalog getCatalogByRef(String catalogRef) {
		if (!catalogRef.endsWith(".json")) {
			if (!catalogRef.endsWith("/")) {
				catalogRef += "/";
			}
			catalogRef += JBANG_CATALOG_JSON;
		}
		Path catalogPath = null;
		try {
			catalogPath = Util.obtainFile(catalogRef);
			Util.verboseMsg(String.format("Obtained catalog from %s", catalogRef));
			Catalog catalog = getCatalog(catalogPath);
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
		List<Catalog> catalogs = new ArrayList<>();
		findNearestCatalogWith(cwd, p -> {
			catalogs.add(getCatalog(p));
			return false;
		});

		Catalog result = new Catalog(null, null, null);
		Collections.reverse(catalogs);
		for (Catalog catalog : catalogs) {
			if (!includeImplicits && catalog.catalogFile.equals(Settings.getUserImplicitCatalogFile())) {
				continue;
			}
			mergeCatalog(catalog, result);
		}

		return result;
	}

	private static void mergeCatalog(Catalog catalog, Catalog result) {
		for (CatalogRef ref : catalog.catalogs.values()) {
			Catalog cat = getCatalogByRef(ref.catalogRef);
			result.aliases.putAll(cat.aliases);
		}
		result.aliases.putAll(catalog.aliases);
		result.catalogs.putAll(catalog.catalogs);
	}

	private static Path findNearestCatalog(Path dir) {
		return findNearestFileWith(dir, JBANG_CATALOG_JSON, p -> true);
	}

	private static Path findNearestCatalogWithAlias(Path dir, String aliasName) {
		return findNearestCatalogWith(dir, catalogFile -> {
			Catalog catalog = getCatalog(catalogFile);
			return catalog.aliases.containsKey(aliasName);
		});
	}

	private static Path findNearestCatalogWithCatalogRef(Path dir, String catalogName) {
		return findNearestCatalogWith(dir, catalogFile -> {
			Catalog catalog = getCatalog(catalogFile);
			return catalog.catalogs.containsKey(catalogName);
		});
	}

	private static Path findNearestCatalogWith(Path dir, Function<Path, Boolean> accept) {
		Path result = findNearestFileWith(dir, JBANG_CATALOG_JSON, accept);
		if (result == null) {
			Path file = Settings.getUserImplicitCatalogFile();
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				result = file;
			}
		}
		return result;
	}

	public static void clearCache() {
		catalogCache.clear();
	}

	public static Catalog getCatalog(Path catalogPath) {
		Catalog catalog;
		if (Util.isFresh() || !catalogCache.containsKey(catalogPath)) {
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
	 * Adds a new catalog ref to the nearest catalog file
	 *
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name The name of the new alias
	 */
	public static Path addNearestCatalogRef(Path cwd, String name, String catalogRef, String description) {
		Path catalogFile = getCatalogFile(cwd, null);
		addCatalogRef(cwd, catalogFile, name, catalogRef, description);
		return catalogFile;
	}

	public static CatalogRef addCatalogRef(Path cwd, Path catalogFile, String name, String catalogRef,
			String description) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		catalogFile = cwd.resolve(catalogFile);
		Catalog catalog = getCatalog(catalogFile);
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
	 * Finds the nearest catalog file that contains a catalog ref with the given
	 * name and removes it
	 *
	 * @param cwd  The folder to use as a starting point for getting the nearest
	 *             catalog
	 * @param name Name of catalog ref to remove
	 */
	public static void removeNearestCatalogRef(Path cwd, String name) {
		Path catalog = findNearestCatalogWithCatalogRef(cwd, name);
		if (catalog != null) {
			removeCatalogRef(catalog, name);
		}
	}

	public static void removeCatalogRef(Path catalogFile, String name) {
		Catalog catalog = getCatalog(catalogFile);
		if (catalog.catalogs.containsKey(name)) {
			catalog.catalogs.remove(name);
			try {
				AliasUtil.writeCatalog(catalogFile, catalog);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove catalog: " + ex.getMessage());
			}
		}
	}

	private static Path findNearestFileWith(Path dir, String fileName, Function<Path, Boolean> accept) {
		Path result = findNearestLocalFileWith(dir, fileName, accept);
		if (result == null) {
			Path file = Settings.getConfigDir().resolve(fileName);
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				result = file;
			}
		}
		return result;
	}

	private static Path findNearestLocalFileWith(Path dir, String fileName, Function<Path, Boolean> accept) {
		if (dir == null) {
			dir = Util.getCwd();
		}
		while (dir != null) {
			Path file = dir.resolve(fileName);
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				return file;
			}
			file = dir.resolve(JBANG_DOT_DIR).resolve(fileName);
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				return file;
			}
			dir = dir.getParent();
		}
		return null;
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

	static boolean isAbsoluteRef(String ref) {
		return isRemoteRef(ref) || Paths.get(ref).isAbsolute();
	}

	static boolean isRemoteRef(String ref) {
		return ref.startsWith("http:") || ref.startsWith("https:") || DependencyUtil.looksLikeAGav(ref);
	}

	@SafeVarargs
	public static <T> Stream<T> chain(Supplier<Optional<T>>... suppliers) {
		return Arrays	.stream(suppliers)
						.map(Supplier::get)
						.flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty));
	}

}
