package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.util.Util;

public class Catalog {
	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";
	public static final String JBANG_IMPLICIT_CATALOG_JSON = "implicit-catalog.json";

	static final Map<Path, Catalog> catalogCache = new HashMap<>();

	static final String JBANG_CATALOG_REPO = "jbang-catalog";

	// HEAD at least on github gives you latest commit on default branch
	static final String DEFAULT_REF = "HEAD";

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
		aliases.forEach((key, a) -> this.aliases.put(key,
				new Alias(a.scriptRef, a.description, a.arguments, a.properties, this)));
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

	/**
	 * Load a Catalog's aliases given the name of a previously registered Catalog
	 *
	 * @param catalogName The name of a registered
	 * @return An Aliases object
	 */
	public static Catalog getByName(Path cwd, String catalogName) {
		CatalogRef catalogRef = CatalogRef.get(cwd, catalogName);
		if (catalogRef != null) {
			return getByRef(catalogRef.catalogRef);
		} else {
			throw new ExitException(EXIT_INVALID_INPUT, "Unknown catalog '" + catalogName + "'");
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
	 * Load a Catalog's aliases given a file path or URL
	 *
	 * @param catalogRef File path or URL to a Catalog JSON file. If this does not
	 *                   end in .json then jbang-catalog.json will be appended to
	 *                   the end.
	 * @return A Catalog object
	 */
	public static Catalog getByRef(String catalogRef) {
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
			Catalog catalog = get(catalogPath);
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
	public static Catalog getMerged(Path cwd, boolean includeImplicits) {
		List<Catalog> catalogs = new ArrayList<>();
		findNearestCatalogWith(cwd, p -> {
			catalogs.add(get(p));
			return false;
		});

		Catalog result = new Catalog(null, null, null);
		Collections.reverse(catalogs);
		for (Catalog catalog : catalogs) {
			if (!includeImplicits && catalog.catalogFile.equals(Settings.getUserImplicitCatalogFile())) {
				continue;
			}
			merge(catalog, result);
		}

		return result;
	}

	private static void merge(Catalog catalog, Catalog result) {
		for (CatalogRef ref : catalog.catalogs.values()) {
			Catalog cat = getByRef(ref.catalogRef);
			result.aliases.putAll(cat.aliases);
		}
		result.aliases.putAll(catalog.aliases);
		result.catalogs.putAll(catalog.catalogs);
	}

	private static Path findNearestCatalog(Path dir) {
		return AliasUtil.findNearestFileWith(dir, JBANG_CATALOG_JSON, p -> true);
	}

	static Path findNearestCatalogWith(Path dir, Function<Path, Boolean> accept) {
		Path result = AliasUtil.findNearestFileWith(dir, JBANG_CATALOG_JSON, accept);
		if (result == null) {
			Path file = Settings.getUserImplicitCatalogFile();
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				result = file;
			}
		}
		return result;
	}

	public static Catalog get(Path catalogPath) {
		Catalog catalog;
		if (Util.isFresh() || !catalogCache.containsKey(catalogPath)) {
			catalog = read(catalogPath);
			catalog.catalogFile = catalogPath.toAbsolutePath();
			catalogCache.put(catalogPath, catalog);
		} else {
			catalog = catalogCache.get(catalogPath);
		}
		return catalog;
	}

	public static void clearCache() {
		catalogCache.clear();
	}

	static Catalog read(Path catalogPath) {
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

	static void write(Path catalogPath, Catalog catalog) throws IOException {
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

	static boolean isAbsoluteRef(String ref) {
		return isRemoteRef(ref) || Paths.get(ref).isAbsolute();
	}

	static boolean isRemoteRef(String ref) {
		return ref.startsWith("http:") || ref.startsWith("https:") || DependencyUtil.looksLikeAGav(ref);
	}

}
