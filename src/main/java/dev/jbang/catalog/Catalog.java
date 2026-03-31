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
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

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

	// Custom deserializers to avoid final field mutation warnings in Java 26+

	public static class CatalogRefDeserializer implements JsonDeserializer<CatalogRef> {
		@Override
		public CatalogRef deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();

			String catalogRef = obj.has("catalog-ref") ? obj.get("catalog-ref").getAsString()
					: obj.has("catalogRef") ? obj.get("catalogRef").getAsString()
							: null;
			String description = obj.has("description") ? obj.get("description").getAsString() : null;
			Boolean importItems = obj.has("import") ? obj.get("import").getAsBoolean() : null;

			// Catalog will be set manually after deserialization (it's transient)
			return new CatalogRef(catalogRef, description, importItems, null);
		}
	}

	public static class TemplateDeserializer implements JsonDeserializer<Template> {
		@Override
		public Template deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();

			Map<String, String> fileRefs = obj.has("file-refs")
					? context.deserialize(obj.get("file-refs"), new TypeToken<Map<String, String>>() {
					}.getType())
					: null;
			String description = obj.has("description") ? obj.get("description").getAsString() : null;

			Map<String, TemplateProperty> properties = null;
			if (obj.has("properties")) {
				properties = new HashMap<>();
				JsonObject propsObj = obj.getAsJsonObject("properties");
				for (String key : propsObj.keySet()) {
					properties.put(key, context.deserialize(propsObj.get(key), TemplateProperty.class));
				}
			}

			// Catalog will be set manually after deserialization (it's transient)
			return new Template(fileRefs, description, properties, null);
		}
	}

	public static class JavaAgentDeserializer implements JsonDeserializer<Alias.JavaAgent> {
		@Override
		public Alias.JavaAgent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();

			String agentRef = obj.has("agent-ref") ? obj.get("agent-ref").getAsString() : null;
			String options = obj.has("options") ? obj.get("options").getAsString() : "";

			return new Alias.JavaAgent(agentRef, options);
		}
	}

	public static class AliasDeserializer implements JsonDeserializer<Alias> {
		private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
		}.getType();
		private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {
		}.getType();
		private static final Type JAVA_AGENT_LIST_TYPE = new TypeToken<List<Alias.JavaAgent>>() {
		}.getType();

		@Override
		public Alias deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();

			String scriptRef = obj.has("script-ref") ? obj.get("script-ref").getAsString()
					: obj.has("scriptRef") ? obj.get("scriptRef").getAsString()
							: null;
			String description = obj.has("description") ? obj.get("description").getAsString() : null;

			List<String> arguments = obj.has("arguments")
					? context.deserialize(obj.get("arguments"), STRING_LIST_TYPE)
					: null;

			List<String> runtimeOptions = obj.has("runtime-options")
					? context.deserialize(obj.get("runtime-options"), STRING_LIST_TYPE)
					: obj.has("java-options") ? context.deserialize(obj.get("java-options"), STRING_LIST_TYPE)
							: null;

			List<String> sources = obj.has("sources") ? context.deserialize(obj.get("sources"), STRING_LIST_TYPE)
					: null;

			List<String> resources = obj.has("files") ? context.deserialize(obj.get("files"), STRING_LIST_TYPE)
					: null;

			List<String> dependencies = obj.has("dependencies")
					? context.deserialize(obj.get("dependencies"), STRING_LIST_TYPE)
					: null;

			List<String> repositories = obj.has("repositories")
					? context.deserialize(obj.get("repositories"), STRING_LIST_TYPE)
					: null;

			List<String> classpaths = obj.has("classpaths")
					? context.deserialize(obj.get("classpaths"), STRING_LIST_TYPE)
					: null;

			Map<String, String> properties = obj.has("properties")
					? context.deserialize(obj.get("properties"), STRING_MAP_TYPE)
					: null;

			String javaVersion = obj.has("java") ? obj.get("java").getAsString() : null;
			String mainClass = obj.has("main") ? obj.get("main").getAsString() : null;
			String moduleName = obj.has("module") ? obj.get("module").getAsString() : null;

			List<String> compileOptions = obj.has("compile-options")
					? context.deserialize(obj.get("compile-options"), STRING_LIST_TYPE)
					: null;

			Boolean nativeImage = obj.has("native-image") ? obj.get("native-image").getAsBoolean() : null;

			List<String> nativeOptions = obj.has("native-options")
					? context.deserialize(obj.get("native-options"), STRING_LIST_TYPE)
					: null;

			String forceType = obj.has("source-type") ? obj.get("source-type").getAsString() : null;
			Boolean integrations = obj.has("integrations") ? obj.get("integrations").getAsBoolean() : null;
			String jfr = obj.has("jfr") ? obj.get("jfr").getAsString() : null;

			Map<String, String> debug = obj.has("debug") ? context.deserialize(obj.get("debug"), STRING_MAP_TYPE)
					: null;

			Boolean cds = obj.has("cds") ? obj.get("cds").getAsBoolean() : null;
			Boolean interactive = obj.has("interactive") ? obj.get("interactive").getAsBoolean() : null;
			Boolean enablePreview = obj.has("enable-preview") ? obj.get("enable-preview").getAsBoolean() : null;
			Boolean enableAssertions = obj.has("enable-assertions") ? obj.get("enable-assertions").getAsBoolean()
					: null;
			Boolean enableSystemAssertions = obj.has("enable-system-assertions")
					? obj.get("enable-system-assertions").getAsBoolean()
					: null;

			Map<String, String> manifestOptions = obj.has("manifest-options")
					? context.deserialize(obj.get("manifest-options"), STRING_MAP_TYPE)
					: null;

			List<Alias.JavaAgent> javaAgents = obj.has("java-agents")
					? context.deserialize(obj.get("java-agents"), JAVA_AGENT_LIST_TYPE)
					: null;

			List<String> docs = obj.has("docs") ? context.deserialize(obj.get("docs"), STRING_LIST_TYPE) : null;

			// Catalog will be set manually after deserialization (it's transient)
			return new Alias(scriptRef, description, arguments, runtimeOptions, sources, resources,
					dependencies, repositories, classpaths, properties, javaVersion, mainClass,
					moduleName, compileOptions, nativeImage, nativeOptions, forceType, integrations,
					jfr, debug, cds, interactive, enablePreview, enableAssertions, enableSystemAssertions,
					manifestOptions, javaAgents, docs, null);
		}
	}

	public static class CatalogDeserializer implements JsonDeserializer<Catalog> {
		@Override
		public Catalog deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();

			String baseRef = obj.has("base-ref") ? obj.get("base-ref").getAsString()
					: obj.has("baseRef") ? obj.get("baseRef").getAsString()
							: null;
			String description = obj.has("description") ? obj.get("description").getAsString() : null;

			Map<String, CatalogRef> catalogs = new HashMap<>();
			if (obj.has("catalogs")) {
				JsonObject catalogsObj = obj.getAsJsonObject("catalogs");
				for (String key : catalogsObj.keySet()) {
					catalogs.put(key, context.deserialize(catalogsObj.get(key), CatalogRef.class));
				}
			}

			Map<String, Alias> aliases = new HashMap<>();
			if (obj.has("aliases")) {
				JsonObject aliasesObj = obj.getAsJsonObject("aliases");
				for (String key : aliasesObj.keySet()) {
					aliases.put(key, context.deserialize(aliasesObj.get(key), Alias.class));
				}
			}

			Map<String, Template> templates = new HashMap<>();
			if (obj.has("templates")) {
				JsonObject templatesObj = obj.getAsJsonObject("templates");
				for (String key : templatesObj.keySet()) {
					templates.put(key, context.deserialize(templatesObj.get(key), Template.class));
				}
			}

			// catalogRef is transient and will be set after deserialization
			return new Catalog(baseRef, description, null, catalogs, aliases, templates);
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
		Gson parser = new GsonBuilder()
			.registerTypeAdapter(Catalog.class, new CatalogDeserializer())
			.registerTypeAdapter(CatalogRef.class, new CatalogRefDeserializer())
			.registerTypeAdapter(Alias.class, new AliasDeserializer())
			.registerTypeAdapter(Alias.JavaAgent.class, new JavaAgentDeserializer())
			.registerTypeAdapter(Template.class, new TemplateDeserializer())
			.create();
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
