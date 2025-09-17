package dev.jbang.catalog;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Type;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.util.Util;

public class Catalog {

	public static class SkipEmptyMapSerializer<K, V> implements JsonSerializer<Map<K, V>> {
		@Override
		public JsonElement serialize(Map<K, V> src, Type typeOfSrc, JsonSerializationContext context) {
			if (src == null || src.isEmpty()) {
				return null;
			}
			return context.serialize(src);
		}
	}

	public static class SkipEmptyListSerializer<T> implements JsonSerializer<List<T>> {
		@Override
		public JsonElement serialize(List<T> src, Type typeOfSrc, JsonSerializationContext context) {
			if (src == null || src.isEmpty()) {
				return null;
			}
			return context.serialize(src);
		}
	}

	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";
	public static final String JBANG_IMPLICIT_CATALOG_JSON = "implicit-catalog.json";

	static final Map<String, Catalog> catalogCache = new HashMap<>();

	static final String JBANG_CATALOG_REPO = "jbang-catalog";

	// HEAD at least on github gives you latest commit on default branch
	static final String DEFAULT_REF = "HEAD";

	private static final String CACHE_BUILTIN = ":::BUILTIN:::";

	@JsonAdapter(SkipEmptyMapSerializer.class)
	public Map<String, CatalogRef> catalogs = new HashMap<>();
	@JsonAdapter(SkipEmptyMapSerializer.class)
	public Map<String, Alias> aliases = new HashMap<>();
	@JsonAdapter(SkipEmptyMapSerializer.class)
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
				new CatalogRef(c.catalogRef, c.description, c.importItems, this)));
		aliases.forEach((key, a) -> this.aliases.put(key, a.withCatalog(this)));
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
		Path catFile = catalogRef.getFile();
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
		write(catalogRef.getFile(), this);
	}

	/**
	 * Load a Catalog given the name of a previously registered Catalog
	 *
	 * @param catalogName The name of a registered
	 * @return An Aliases object
	 */
	public static Catalog getByName(String catalogName) {
		CatalogRef catalogRef = CatalogRef.get(simplifyRef(catalogName));
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
				catFile = catalog.catalogRef.getFile();
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
	public static Catalog getMerged(boolean includeImported, boolean includeImplicits) {
		List<Catalog> catalogs = new ArrayList<>();
		findNearestCatalogWith(Util.getCwd(), includeImported, includeImplicits, cat -> {
			catalogs.add(0, cat);
			return null;
		});

		Catalog result = Catalog.empty();
		for (Catalog catalog : catalogs) {
			result.aliases.putAll(catalog.aliases);
			result.templates.putAll(catalog.templates);
			result.catalogs.putAll(catalog.catalogs);
		}

		return result;
	}

	private static Catalog findNearestCatalog(Path dir) {
		Path catalogFile = Util.findNearestWith(dir, Util.acceptFile(JBANG_CATALOG_JSON));
		return catalogFile != null ? get(catalogFile) : null;
	}

	static Catalog findNearestCatalogWith(Path dir, boolean includeImported, boolean includeImplicits,
			Function<Catalog, Catalog> acceptCatalog) {
		Function<Path, Path> acceptFile = Util.acceptFile(JBANG_CATALOG_JSON);
		Catalog catalog = Util.findNearestWith(dir, acceptFile.andThen(Util.notNull(p -> {
			try {
				Catalog cat = get(p);
				return acceptCatalog.apply(cat);
			} catch (Exception e) {
				Util.warnMsg("Unable to read catalog " + p + " because " + e);
				return null;
			}
		})));
		if (catalog == null && includeImported) {
			catalog = Util.findNearestWith(dir, acceptFile.andThen(Util.notNull(p -> {
				try {
					Catalog cat = get(p);
					return findImportedCatalogsWith(cat, acceptCatalog);
				} catch (Exception e) {
					Util.warnMsg("Unable to read catalog " + p + " because " + e);
					return null;
				}
			})));
		}
		if (catalog == null && includeImplicits) {
			Path file = Settings.getUserImplicitCatalogFile();
			if (Files.isRegularFile(file) && Files.isReadable(file)) {
				try {
					Catalog cat = get(file);
					catalog = acceptCatalog.apply(cat);
				} catch (Exception e) {
					Util.warnMsg("Unable to read catalog " + file + " because " + e);
					return null;
				}
			}
		}
		if (catalog == null) {
			catalog = acceptCatalog.apply(getBuiltin());
			if (catalog == null && includeImported) {
				catalog = findImportedCatalogsWith(getBuiltin(), acceptCatalog);
			}
		}
		return catalog;
	}

	static Catalog findImportedCatalogsWith(Catalog catalog, Function<Catalog, Catalog> accept) {
		for (CatalogRef cr : catalog.catalogs.values()) {
			if (cr.importItems == Boolean.TRUE) {
				try {
					Catalog cat = Catalog.getByRef(cr.catalogRef);
					Catalog result = accept.apply(cat);
					if (result != null)
						return result;
				} catch (Exception e) {
					Util.verboseMsg("Unable to read catalog " + cr.catalogRef + " because " + e);
				}
			}
		}
		return null;
	}

	public static Catalog get(Path catalogPath) {
		if (Files.isDirectory(catalogPath)) {
			catalogPath = catalogPath.resolve(Catalog.JBANG_CATALOG_JSON);
		}
		return get(ResourceRef.forFile(catalogPath));
	}

	private static Catalog get(ResourceRef ref) {
		Catalog catalog;
		Path catalogPath = ref.getFile();
		if (Util.isFresh() || !catalogCache.containsKey(catalogPath.toString())) {
			catalog = read(ref);
			catalog.catalogRef = ref;
			catalogCache.put(catalogPath.toString(), catalog);
		} else {
			catalog = catalogCache.get(catalogPath.toString());
		}
		return catalog;
	}

	// This returns the built-in Catalog that can be found in the resources
	public static Catalog getBuiltin() {
		Catalog catalog = Catalog.empty();
		if (Util.isFresh() || !catalogCache.containsKey(CACHE_BUILTIN)) {
			String res = "classpath:/" + JBANG_CATALOG_JSON;
			ResourceRef catRef = ResourceRef.forResource(res);
			if (catRef != null) {
				catalog = read(catRef);
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

	static Catalog read(ResourceRef catalogRef) {
		Util.verboseMsg(String.format("Reading catalog from %s", catalogRef.getOriginalResource()));
		Catalog catalog = Catalog.empty();
		if (catalogRef.exists()) {
			try (InputStream is = catalogRef.getInputStream()) {
				catalog = read(is);
			} catch (IOException e) {
				// Ignore errors
			}
		}
		return catalog;
	}

	private static Catalog read(InputStream is) {
		Gson parser = new Gson();
		Catalog catalog = parser.fromJson(new InputStreamReader(is), Catalog.class);
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

	public static String simplifyRef(String catalogRefString) {
		if (Util.isURL(catalogRefString)) {
			ImplicitCatalogRef ref = ImplicitCatalogRef.extract(catalogRefString);
			if (ref != null) {
				return ref.toString();
			}
		} else if (!isValidCatalogReference(catalogRefString)) {
			if (catalogRefString.endsWith("/" + JBANG_CATALOG_REPO)) {
				return catalogRefString.substring(0, catalogRefString.length() - 14);
			} else {
				return catalogRefString.replace("/" + JBANG_CATALOG_REPO + "~", "~");
			}
		}
		return catalogRefString;
	}

	public static boolean isValidName(String name) {
		return name.matches("^[a-zA-Z][-\\w]*$");
	}

	public static boolean isValidCatalogReference(String name) {
		String[] parts = name.split("@");
		if (parts.length < 2) {
			return false;
		}
		for (String p : parts) {
			if (p.isEmpty())
				return false;
		}
		for (int i = 0; i < parts.length - 1; i++) {
			if (!isValidName(parts[i]))
				return false;
		}
		return true;
	}

}
