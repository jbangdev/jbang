package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import io.quarkus.qute.Template;

public class Settings {
	static Map<Path, AliasUtil.Aliases> catalogCache = new HashMap<>();
	static AliasUtil.CatalogInfo catalogInfo = null;

	public static final String JBANG_REPO = "JBANG_REPO";
	public static final String JBANG_DIR = "JBANG_DIR";
	public static final String JBANG_CACHE_DIR = "JBANG_CACHE_DIR";

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

	public enum CacheClass {
		urls, jars, jdks
	}

	public static void clearCache(CacheClass... classes) {
		if (classes.length == 0) {
			classes = CacheClass.values();
		}
		List<CacheClass> ccs = Arrays.asList(classes);
		for (CacheClass cc : ccs) {
			if (cc == CacheClass.jdks && Util.isWindows() && JdkManager.isCurrentJdkManaged()) {
				// We're running using a managed JDK on Windows so we can't just delete the
				// entire folder!
				try {
					for (Integer v : JdkManager.listInstalledJdks()) {
						JdkManager.uninstallJdk(v);
					}
				} catch (IOException ex) {
					Util.errorMsg("Error clearing JDK cache", ex);
				}
			} else {
				Util.deleteFolder(Settings.getCacheDir().resolve(cc.name()), true);
			}
		}
	}

	static TemplateEngine te;

	public static TemplateEngine getTemplateEngine() {
		if (te == null) {
			te = new TemplateEngine();
		}
		return te;
	}

	public static Path getAliasesFile() {
		return getConfigDir().resolve(AliasUtil.JBANG_CATALOG_JSON);
	}

	public static AliasUtil.Aliases getAliasesFromCatalog(Path catalogPath, boolean updateCache) {
		AliasUtil.Aliases aliases;
		if (updateCache || !catalogCache.containsKey(catalogPath)) {
			aliases = AliasUtil.readAliasesFromCatalog(catalogPath);
			catalogCache.put(catalogPath, aliases);
		} else {
			aliases = catalogCache.get(catalogPath);
		}
		return aliases;
	}

	public static AliasUtil.Aliases getAliasesFromLocalCatalog() {
		return getAliasesFromCatalog(getAliasesFile(), false);
	}

	public static Map<String, AliasUtil.Alias> getAliases() {
		return getAliasesFromLocalCatalog().aliases;
	}

	public static void addAlias(String name, String scriptRef, String description, List<String> arguments,
			Map<String, String> properties) {
		getAliases().put(name, new AliasUtil.Alias(scriptRef, description, arguments, properties));
		try {
			AliasUtil.writeAliasesToCatalog(getAliasesFile());
		} catch (IOException ex) {
			Util.warnMsg("Unable to add alias: " + ex.getMessage());
		}
	}

	public static void removeAlias(String name) {
		if (getAliases().containsKey(name)) {
			getAliases().remove(name);
			try {
				AliasUtil.writeAliasesToCatalog(getAliasesFile());
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
		}
	}

	public static Path getCatalogsFile() {
		return getConfigDir().resolve(CATALOGS_JSON);
	}

	public static AliasUtil.CatalogInfo getCatalogInfo() {
		if (catalogInfo == null) {
			catalogInfo = AliasUtil.readCatalogInfo(getCatalogsFile());
		}
		return catalogInfo;
	}

	public static Map<String, AliasUtil.Catalog> getCatalogs() {
		return getCatalogInfo().catalogs;
	}

	public static AliasUtil.Catalog addCatalog(String name, String catalogRef, String description) {
		AliasUtil.Catalog catalog = new AliasUtil.Catalog(catalogRef, description);
		getCatalogs().put(name, catalog);
		try {
			AliasUtil.writeCatalogInfo(getCatalogsFile());
			return catalog;
		} catch (IOException ex) {
			Util.warnMsg("Unable to add catalog: " + ex.getMessage());
			return null;
		}
	}

	public static void removeCatalog(String name) {
		if (getCatalogs().containsKey(name)) {
			getCatalogs().remove(name);
			try {
				AliasUtil.writeCatalogInfo(getCatalogsFile());
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove catalog: " + ex.getMessage());
			}
		}
	}

}
