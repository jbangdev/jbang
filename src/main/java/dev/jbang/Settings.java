package dev.jbang;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import dev.jbang.catalog.Catalog;

public class Settings {
	public static final String JBANG_REPO = "JBANG_REPO";
	public static final String JBANG_DIR = "JBANG_DIR";
	public static final String JBANG_CACHE_DIR = "JBANG_CACHE_DIR";

	public static final String TRUSTED_SOURCES_JSON = "trusted-sources.json";
	public static final String DEPENDENCY_CACHE_JSON = "dependency_cache.json";
	public static final String CURRENT_JDK = "currentjdk";
	public static final String BIN_DIR = "bin";
	public static final String EDITOR_DIR = "editor";

	public static final String ENV_DEFAULT_JAVA_VERSION = "JBANG_DEFAULT_JAVA_VERSION";
	public static final String ENV_NO_VERSION_CHECK = "JBANG_NO_VERSION_CHECK";

	public static final int DEFAULT_JAVA_VERSION = 11;

	final public static String CP_SEPARATOR = File.pathSeparator;

	public static File getLocalMavenRepo() {
		return new File(System.getenv().getOrDefault(JBANG_REPO, System.getProperty("user.home") + "/.m2/repository"))
																														.getAbsoluteFile();
	}

	public static Path getCacheDependencyFile() {
		return getCacheDir(true).resolve(DEPENDENCY_CACHE_JSON);
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

	public static Path getCurrentJdkDir() {
		return getConfigDir(true).resolve(CURRENT_JDK);
	}

	public static Path getConfigBinDir() {
		return getConfigDir(true).resolve(BIN_DIR);
	}

	public static Path getConfigEditorDir() {
		return getConfigDir(true).resolve(EDITOR_DIR);
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
			Cache.setupCache(dir);

		return dir;
	}

	public static Path getCacheDir() {
		return getCacheDir(true);
	}

	public static Path getCacheDir(Cache.CacheClass cclass) {
		return getCacheDir().resolve(cclass.name());
	}

	public static int getDefaultJavaVersion() {
		String v = System.getenv(ENV_DEFAULT_JAVA_VERSION);
		if (v != null) {
			return Integer.parseInt(v);
		}
		return DEFAULT_JAVA_VERSION;
	}

	public static Path getTrustedSourcesFile() {
		return getConfigDir().resolve(TRUSTED_SOURCES_JSON);
	}

	public static Path getUserCatalogFile() {
		return getConfigDir().resolve(Catalog.JBANG_CATALOG_JSON);
	}

	public static Path getUserImplicitCatalogFile() {
		return getConfigDir().resolve(Catalog.JBANG_IMPLICIT_CATALOG_JSON);
	}

}
