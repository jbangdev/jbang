package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
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

import dev.jbang.Main;
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

	private static final Path CACHE_BUILTIN = Paths.get(":::BUILTIN:::");

	public Map<String, CatalogRef> catalogs = new HashMap<>();
	public Map<String, Alias> aliases = new HashMap<>();
	public Map<String, Template> templates = new HashMap<>();

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
		if (isClassPathRef(catalogFile.toString())) {
			return "classpath:" + ((baseRef != null) ? "/" + baseRef : "");
		}
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

	String relativize(Path cwd, String scriptRef) {
		if (!isRemoteRef(scriptRef) && !isValidCatalogReference(scriptRef)) {
			// If the scriptRef points to an existing file on the local filesystem
			// or it's obviously a path (but not an absolute path) we'll make it
			// relative to the location of the catalog we're adding the alias to.
			Path script = cwd.resolve(scriptRef).normalize();
			String baseRef = getScriptBase();
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
		return scriptRef;
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
	 * @param cwd         The folder to use as a starting point for getting the
	 *                    nearest catalog. NB: This method _specifically_ does _not_
	 *                    return a reference to the implicit catalog ever.
	 * @param catalogFile The catalog to return or null to return the nearest
	 *                    catalog
	 * @return Path to a catalog
	 */
	public static Path getCatalogFile(Path cwd, Path catalogFile) {
		if (catalogFile == null) {
			Catalog catalog = findNearestCatalog(cwd);
			if (catalog != null) {
				catalogFile = catalog.catalogFile;
			} else {
				// This is here as a backup for when the user catalog doesn't
				// exist yet, because `findNearestCatalog()` only returns
				// existing files
				catalogFile = Settings.getUserCatalogFile();
			}
		}
		return catalogFile;
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
			result.templates.putAll(cat.templates);
		}
		result.aliases.putAll(catalog.aliases);
		result.templates.putAll(catalog.templates);
		result.catalogs.putAll(catalog.catalogs);
	}

	private static Catalog findNearestCatalog(Path dir) {
		Path catalogFile = CatalogUtil.findNearestFileWith(dir, JBANG_CATALOG_JSON, p -> true);
		return catalogFile != null ? get(catalogFile) : null;
	}

	static Catalog findNearestCatalogWith(Path dir, Function<Path, Boolean> accept) {
		Path catalogFile = CatalogUtil.findNearestFileWith(dir, JBANG_CATALOG_JSON, accept);
		if (catalogFile == null) {
			Path file = Settings.getUserImplicitCatalogFile();
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(file)) {
				catalogFile = file;
			}
		}
		if (catalogFile != null) {
			return get(catalogFile);
		} else {
			return getBuiltin();
		}
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

	// This returns the built-in Catalog that can be found in the resources
	public static Catalog getBuiltin() {
		Catalog catalog = new Catalog(null, null, null);
		if (Util.isFresh() || !catalogCache.containsKey(CACHE_BUILTIN)) {
			try {
				ClassLoader cl = Thread.currentThread().getContextClassLoader();
				if (cl == null) {
					cl = Main.class.getClassLoader();
				}
				URL catalogUrl = cl.getResource(JBANG_CATALOG_JSON);
				if (catalogUrl != null) {
					catalog = read(new InputStreamReader(catalogUrl.openStream()));
					catalog.catalogFile = Paths.get("classpath:/" + JBANG_CATALOG_JSON);
					catalogCache.put(CACHE_BUILTIN, catalog);
				}
			} catch (IOException e) {
				// Ignore errors
			}
		} else {
			catalog = catalogCache.get(CACHE_BUILTIN);
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
				catalog = read(in);
			} catch (IOException e) {
				// Ignore errors
			}
		}
		return catalog;
	}

	private static Catalog read(Reader in) {
		Gson parser = new Gson();
		Catalog catalog = parser.fromJson(in, Catalog.class);
		if (catalog != null) {
			// Validate the result (Gson can't do this)
			if (catalog.catalogs == null) {
				catalog.catalogs = new HashMap<>();
			}
			if (catalog.aliases == null) {
				catalog.aliases = new HashMap<>();
			}
			if (catalog.templates == null) {
				catalog.templates = new HashMap<>();
			}
			for (String catName : catalog.catalogs.keySet()) {
				CatalogRef cat = catalog.catalogs.get(catName);
				check(cat.catalogRef != null, "Missing required attribute 'catalogs.catalogRef'");
			}
			for (String aliasName : catalog.aliases.keySet()) {
				Alias alias = catalog.aliases.get(aliasName);
				alias.catalog = catalog;
				check(alias.scriptRef != null, "Missing required attribute 'aliases.script-ref'");
			}
			for (String tplName : catalog.templates.keySet()) {
				Template tpl = catalog.templates.get(tplName);
				tpl.catalog = catalog;
				check(tpl.fileRefs != null, "Missing required attribute 'templates.file-refs'");
				check(!tpl.fileRefs.isEmpty(), "Attribute 'aliases.script-ref' has no elements");
			}
		} else {
			catalog = new Catalog(null, null, null);
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

	static boolean isClassPathRef(String ref) {
		return ref.startsWith("classpath:");
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

}
