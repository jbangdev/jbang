package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import io.quarkus.qute.Template;

public class Settings {
	static Map<Path, Aliases> catalogCache = new HashMap<>();
	static CatalogInfo catalogInfo = null;

	public static final String JBANG_REPO = "JBANG_REPO";
	public static final String JBANG_DIR = "JBANG_DIR";
	public static final String JBANG_CACHE_DIR = "JBANG_CACHE_DIR";

	public static final String ALIASES_JSON = "aliases.json";
	public static final String CATALOGS_JSON = "catalogs.json";
	public static final String TRUSTED_SOURCES_JSON = "trusted-sources.json";
	public static final String DEPENDENCY_CACHE_TXT = "dependency_cache.txt";

	final public static String CP_SEPARATOR = File.pathSeparator;

	private static TrustedSources trustedSources;

	public static File getLocalMavenRepo() {
		return new File(System.getenv().getOrDefault(JBANG_REPO, System.getProperty("user.home") + "/.m2/repository"))
																														.getAbsoluteFile();
	}

	public static Path getCacheDependencyFile() {
		return getCacheDir(true).resolve(DEPENDENCY_CACHE_TXT);
	}

	public static Path getConfigDir(boolean init) {
		Path dir;
		String jd = System.getenv(JBANG_DIR);
		if (jd != null) {
			dir = Paths.get(jd);
		} else {
			dir = Paths.get(System.getProperty("user.home")).resolve(".jbang");
		}

		if (init)
			setupJbangDir(dir);

		return dir;
	}

	public static Path getConfigDir() {
		return getConfigDir(true);
	}

	public static void setupJbangDir(Path dir) {
		// create JBang configuration dir if it does not yet exist
		dir.toFile().mkdirs();
	}

	public static Path getCacheDir(boolean init) {
		Path dir;
		String v = System.getenv(JBANG_CACHE_DIR);
		if (v != null) {
			dir = Paths.get(v);
		} else {
			dir = getConfigDir().resolve("cache");
		}

		if (init)
			setupCache(dir);

		return dir;
	}

	public static Path getCacheDir() {
		return getCacheDir(true);
	}

	private static void setupCache(Path dir) {
		// create cache dir if it does not yet exist
		dir.toFile().mkdirs();
	}

	public static Path getTrustedSourcesFile() {
		return getConfigDir().resolve(TRUSTED_SOURCES_JSON);
	}

	void createTrustedSources() {
		Path trustedSourcesFile = getTrustedSourcesFile();
		if (Files.notExists(trustedSourcesFile)) {
			String templateName = "trusted-sources.qute";
			Template template = Settings.getTemplateEngine().getTemplate(templateName);
			if (template == null)
				throw new ExitException(1, "Could not locate template named: '" + templateName + "'");
			String result = template.render();

			try {
				Util.writeString(trustedSourcesFile, result);
			} catch (IOException e) {
				Util.errorMsg("Could not create initial trusted-sources file at " + trustedSourcesFile, e);
			}

		}
	}

	public static TrustedSources getTrustedSources() {
		if (trustedSources == null) {
			Path trustedSourcesFile = getTrustedSourcesFile();
			if (Files.isRegularFile(trustedSourcesFile)) {
				try {
					trustedSources = TrustedSources.load(trustedSourcesFile);
				} catch (IOException e) {
					Util.warnMsg("Could not read " + trustedSourcesFile);
					trustedSources = new TrustedSources(new String[0]);
				}
			} else {
				trustedSources = new TrustedSources(new String[0]);
			}
		}
		return trustedSources;
	}

	public static void clearCache() {
		try {
			Files	.walk(Settings.getCacheDir())
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		} catch (IOException e) {
			throw new ExitException(-1, "Could not delete cache.", e);
		}
	}

	static TemplateEngine te;

	public static TemplateEngine getTemplateEngine() {
		if (te == null) {
			te = new TemplateEngine();
		}
		return te;
	}

	public static class Alias {
		@SerializedName(value = "script-ref", alternate = { "scriptRef" })
		public final String scriptRef;
		public final String description;
		public final List<String> arguments;
		public final Map<String, String> properties;

		public Alias(String scriptRef, String description, List<String> arguments, Map<String, String> properties) {
			this.scriptRef = scriptRef;
			this.description = description;
			this.arguments = arguments;
			this.properties = properties;
		}
	}

	public static class Aliases {
		public Map<String, Alias> aliases = new HashMap<>();
		@SerializedName(value = "base-ref", alternate = { "baseRef" })
		public String baseRef;
		public String description;
	}

	public static Path getAliasesFile() {
		return getConfigDir().resolve(ALIASES_JSON);
	}

	public static Aliases getAliasesFromCatalog(Path catalogPath, boolean updateCache) {
		Aliases aliases;
		if (updateCache || !catalogCache.containsKey(catalogPath)) {
			aliases = new Aliases();
			if (Files.isRegularFile(catalogPath)) {
				try (Reader in = Files.newBufferedReader(catalogPath)) {
					Gson parser = new Gson();
					aliases = parser.fromJson(in, Aliases.class);
					// Validate the result (Gson can't do this)
					check(aliases.aliases != null, "Missing required attribute 'aliases'");
					for (String aliasName : aliases.aliases.keySet()) {
						Alias alias = aliases.aliases.get(aliasName);
						check(alias.scriptRef != null, "Missing required attribute 'aliases.scriptRef'");
					}
				} catch (IOException e) {
					// Ignore errors
				}
			}
			catalogCache.put(catalogPath, aliases);
		} else {
			aliases = catalogCache.get(catalogPath);
		}
		return aliases;
	}

	public static Aliases getAliasesFromLocalCatalog() {
		return getAliasesFromCatalog(getAliasesFile(), false);
	}

	public static Map<String, Alias> getAliases() {
		return getAliasesFromLocalCatalog().aliases;
	}

	public static void addAlias(String name, String scriptRef, String description, List<String> arguments,
			Map<String, String> properties) {
		getAliases().put(name, new Alias(scriptRef, description, arguments, properties));

		try (Writer out = Files.newBufferedWriter(getAliasesFile())) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(getAliasesFromLocalCatalog(), out);
		} catch (IOException ex) {
			Util.warnMsg("Unable to add alias: " + ex.getMessage());
		}
	}

	public static void removeAlias(String name) {
		if (getAliases().containsKey(name)) {
			try (Writer out = Files.newBufferedWriter(getAliasesFile())) {
				getAliases().remove(name);
				Gson parser = new GsonBuilder().setPrettyPrinting().create();
				parser.toJson(getAliasesFromLocalCatalog(), out);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
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

	static class CatalogInfo {
		Map<String, Catalog> catalogs = new HashMap<>();
	}

	public static Path getCatalogsFile() {
		return getConfigDir().resolve(CATALOGS_JSON);
	}

	public static CatalogInfo getCatalogInfo() {
		if (catalogInfo == null) {
			Path catalogsPath = getCatalogsFile();
			if (Files.isRegularFile(catalogsPath)) {
				try (Reader in = Files.newBufferedReader(catalogsPath)) {
					Gson parser = new Gson();
					catalogInfo = parser.fromJson(in, CatalogInfo.class);
					// Validate the result (Gson can't do this)
					check(catalogInfo.catalogs != null, "Missing required attribute 'catalogs'");
					for (String catName : catalogInfo.catalogs.keySet()) {
						Catalog cat = catalogInfo.catalogs.get(catName);
						check(cat.catalogRef != null, "Missing required attribute 'catalogs.catalogRef'");
					}
				} catch (IOException e) {
					catalogInfo = new CatalogInfo();
				}
			} else {
				catalogInfo = new CatalogInfo();
			}
		}
		return catalogInfo;
	}

	public static Map<String, Catalog> getCatalogs() {
		return getCatalogInfo().catalogs;
	}

	public static void addCatalog(String name, String catalogRef, String description) {
		getCatalogs().put(name, new Catalog(catalogRef, description));

		try (Writer out = Files.newBufferedWriter(getCatalogsFile())) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(getCatalogInfo(), out);
		} catch (IOException ex) {
			Util.warnMsg("Unable to add catalog: " + ex.getMessage());
		}
	}

	public static void removeCatalog(String name) {
		if (getCatalogs().containsKey(name)) {
			try (Writer out = Files.newBufferedWriter(getCatalogsFile())) {
				getCatalogs().remove(name);
				Gson parser = new GsonBuilder().setPrettyPrinting().create();
				parser.toJson(getCatalogInfo(), out);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove catalog: " + ex.getMessage());
			}
		}
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new JsonParseException(message);
		}
	}
}
