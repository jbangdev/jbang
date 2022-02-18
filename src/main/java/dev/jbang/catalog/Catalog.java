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
import dev.jbang.source.ResourceRef;
import dev.jbang.util.Util;

public class Catalog {
	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";
	public static final String JBANG_IMPLICIT_CATALOG_JSON = "implicit-catalog.json";

	static final Map<String, Catalog> catalogCache = new HashMap<>();

	static final String JBANG_CATALOG_REPO = "jbang-catalog";

	// HEAD at least on github gives you latest commit on default branch
	static final String DEFAULT_REF = "HEAD";

	private static final String CACHE_BUILTIN = ":::BUILTIN:::";

	public Map<String, CatalogRef> catalogs = new HashMap<>();
	public Map<String, Alias> aliases = new HashMap<>();
	public Map<String, Template> templates = new HashMap<>();

	@SerializedName(value = "base-ref", alternate = { "baseRef" })
	public final String baseRef;
	public final String description;
	public transient ResourceRef catalogRef;

	public Catalog(String baseRef, String description, ResourceRef catalogRef, Map<String, CatalogRef> catalogs,
			Map<String, Alias> aliases, Map<String, Template> templates) {
		this.baseRef = baseRef;
		this.description = description;
		this.catalogRef = catalogRef;
		catalogs.forEach((key, c) -> this.catalogs.put(key,
				new CatalogRef(c.catalogRef, c.description, this)));
		aliases.forEach((key, a) -> this.aliases.put(key,
				new Alias(a.scriptRef, a.description, a.arguments, a.javaOptions, a.sources, a.dependencies,
						a.repositories, a.classpaths, a.properties, a.javaVersion, a.mainClass, this)));
		templates.forEach((key, t) -> this.templates.put(key,
				new Template(t.fileRefs, t.description, t.properties, this)));
	}

	public static Catalog empty() {
		return new Catalog(null, null, null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
	}

	/**
	 * Returns in all cases the absolute base reference that can be used to resolve
	 * an Alias' script location. The result will either be a URL or an absolute
	 * path.
	 *
	 * @return A string to be used as the base for Alias script locations
	 */
	public String getScriptBase() {
		if (catalogRef.isClasspath()) {
			return "classpath:" + ((baseRef != null) ? "/" + baseRef : "");
		}
		Path result;
		Path catFile = catalogRef.getFile().toPath();
		if (baseRef != null) {
			if (!Util.isRemoteRef(baseRef)) {
				Path base = Paths.get(baseRef);
				if (!base.isAbsolute()) {
					result = catFile.getParent().resolve(base);
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
			result = catFile.getParent();
		}
		return result.normalize().toString();
	}

	String relativize(String scriptRef) {
		if (!Util.isRemoteRef(scriptRef) && !isValidCatalogReference(scriptRef)) {
			// If the scriptRef points to an existing file on the local filesystem
			// or it's obviously a path (but not an absolute path) we'll make it
			// relative to the location of the catalog we're adding the alias to.
			Path cwd = Util.getCwd();
			Path script = cwd.resolve(scriptRef).normalize();
			if (script.startsWith(cwd.normalize())) {
				scriptRef = cwd.relativize(script).toString();
			}
			String baseRef = getScriptBase();
			if (!Util.isAbsoluteRef(scriptRef)
					&& !Util.isRemoteRef(baseRef)
					&& (!isValidName(scriptRef) || Files.isRegularFile(script))) {
				Path base = Paths.get(baseRef);
				if (base.getRoot().equals(script.getRoot())) {
					scriptRef = base.relativize(script.toAbsolutePath()).normalize().toString();
				} else {
					scriptRef = script.toAbsolutePath().normalize().toString();
				}
			}
			if (!Util.isRemoteRef(baseRef)
					&& !isValidName(scriptRef)
					&& !Files.isRegularFile(script)) {
				throw new IllegalArgumentException("Source file not found: " + scriptRef);
			}
		}
		return scriptRef;
	}

	void write() throws IOException {
		write(catalogRef.getFile().toPath(), this);
	}

	/**
	 * Load a Catalog given the name of a previously registered Catalog
	 *
	 * @param catalogName The name of a registered
	 * @return An Aliases object
	 */
	public static Catalog getByName(String catalogName) {
		CatalogRef catalogRef = CatalogRef.get(simplifyName(catalogName));
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
	 * @param catFile The catalog to return or null to return the nearest catalog
	 * @return Path to a catalog
	 */
	public static Path getCatalogFile(Path catFile) {
		if (catFile == null) {
			Catalog catalog = findNearestCatalog(Util.getCwd());
			if (catalog != null && !catalog.catalogRef.isClasspath()) {
				catFile = catalog.catalogRef.getFile().toPath();
			} else {
				// This is here as a backup for when the user catalog doesn't
				// exist yet, because `findNearestCatalog()` only returns
				// existing files
				catFile = Settings.getUserCatalogFile();
			}
		}
		return catFile;
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
			Catalog catalog = get(ResourceRef.forResource(catalogRef));
			if (catalog == null) {
				throw new ExitException(EXIT_UNEXPECTED_STATE,
						"Unable to download catalog: " + catalogRef);
			}
			Util.verboseMsg(String.format("Obtained catalog from %s", catalogRef));
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
				catalog = new Catalog(baseRef, catalog.description, catalog.catalogRef, catalog.catalogs,
						catalog.aliases, catalog.templates);
			}
			return catalog;
		} catch (JsonParseException ex) {
			throw new ExitException(EXIT_UNEXPECTED_STATE,
					"Unable to download catalog: " + catalogRef + " via " + catalogPath, ex);
		}
	}

	/**
	 * Returns a Catalog containing all the aliases from local catalog files merged
	 * into one. This follows the system where aliases that are "nearest" have
	 * priority.
	 *
	 * @param includeImplicits Determines if the implicit catalogs should be merged
	 *                         or not
	 * @return a Catalog object
	 */
	public static Catalog getMerged(boolean includeImplicits) {
		List<Catalog> catalogs = new ArrayList<>();
		findNearestCatalogWith(Util.getCwd(), cat -> {
			catalogs.add(0, cat);
			return false;
		});

		Catalog result = Catalog.empty();
		for (Catalog catalog : catalogs) {
			if (!includeImplicits
					&& catalog.catalogRef.getFile().toPath().equals(Settings.getUserImplicitCatalogFile())) {
				continue;
			}
			merge(catalog, result);
		}

		return result;
	}

	private static void merge(Catalog catalog, Catalog result) {
		// Merge the aliases and templates of the catalog refs
		// into the current catalog
		for (CatalogRef ref : catalog.catalogs.values()) {
			try {
				Catalog cat = getByRef(ref.catalogRef);
				result.aliases.putAll(cat.aliases);
				result.templates.putAll(cat.templates);
			} catch (Exception ex) {
				Util.warnMsg(
						"Unable to read catalog " + ref.catalogRef + " (referenced from " + catalog.catalogRef + ")");
			}
		}
		result.aliases.putAll(catalog.aliases);
		result.templates.putAll(catalog.templates);
		result.catalogs.putAll(catalog.catalogs);
	}

	private static Catalog findNearestCatalog(Path dir) {
		Path catalogFile = Util.findNearestFileWith(dir, JBANG_CATALOG_JSON, p -> true);
		return catalogFile != null ? get(catalogFile) : null;
	}

	static Catalog findNearestCatalogWith(Path dir, Function<Catalog, Boolean> accept) {
		Path catalogFile = Util.findNearestFileWith(dir, JBANG_CATALOG_JSON, cat -> accept.apply(get(cat)));
		if (catalogFile == null) {
			Path file = Settings.getUserImplicitCatalogFile();
			if (Files.isRegularFile(file) && Files.isReadable(file) && accept.apply(get(file))) {
				catalogFile = file;
			}
		}
		if (catalogFile != null) {
			return get(catalogFile);
		} else if (accept.apply(getBuiltin())) {
			return getBuiltin();
		}
		return Catalog.empty();
	}

	public static Catalog get(Path catalogPath) {
		return get(ResourceRef.forNamedFile(catalogPath.toString(), catalogPath.toFile()));
	}

	private static Catalog get(ResourceRef ref) {
		Catalog catalog;
		Path catalogPath = ref.getFile().toPath();
		if (Util.isFresh() || !catalogCache.containsKey(catalogPath.toString())) {
			if (Files.isDirectory(catalogPath)) {
				catalogPath = catalogPath.resolve(Catalog.JBANG_CATALOG_JSON);
			}
			catalog = read(catalogPath);
			catalog.catalogRef = ref;
			catalogCache.put(catalogPath.toString(), catalog);
		} else {
			catalog = catalogCache.get(catalogPath.toString());
		}
		return catalog;
	}

	// Returns the implicit name for a Catalog if that Catalog was found in
	// the list of implicit catalogs
	public static String findImplicitName(Catalog catalog) {
		Path file = Settings.getUserImplicitCatalogFile();
		if (Files.isRegularFile(file) && Files.isReadable(file)) {
			Catalog implicit = get(file);
			return implicit.catalogs.entrySet()
									.stream()
									.filter(e -> catalog.catalogRef	.getOriginalResource()
																	.equals(e.getValue().catalogRef))
									.map(Map.Entry::getKey)
									.findAny()
									.orElse(null);
		}
		return null;
	}

	// This returns the built-in Catalog that can be found in the resources
	public static Catalog getBuiltin() {
		Catalog catalog = Catalog.empty();
		if (Util.isFresh() || !catalogCache.containsKey(CACHE_BUILTIN)) {
			String res = "classpath:/" + JBANG_CATALOG_JSON;
			ResourceRef catRef = ResourceRef.forResource(res);
			if (catRef != null) {
				Path catPath = catRef.getFile().toPath();
				catalog = read(catPath);
				catalog.catalogRef = catRef;
				catalogCache.put(CACHE_BUILTIN, catalog);
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
		Util.verboseMsg(String.format("Reading catalog from %s", catalogPath));
		Catalog catalog = Catalog.empty();
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
				cat.catalog = catalog;
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
				check(!tpl.fileRefs.isEmpty(), "Attribute 'templates.file-refs' has no elements");
			}
		} else {
			catalog = Catalog.empty();
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

	public static String simplifyName(String catalog) {
		if (catalog.endsWith("/" + JBANG_CATALOG_REPO)) {
			return catalog.substring(0, catalog.length() - 14);
		} else {
			return catalog.replace("/" + JBANG_CATALOG_REPO + "~", "~");
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

}
