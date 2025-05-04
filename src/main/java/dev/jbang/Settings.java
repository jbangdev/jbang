package dev.jbang;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeParseException;

import dev.jbang.catalog.Catalog;
import dev.jbang.util.Util;

public class Settings {
	public static final String JBANG_REPO = "JBANG_REPO";
	public static final String JBANG_DIR = "JBANG_DIR";
	public static final String JBANG_CACHE_DIR = "JBANG_CACHE_DIR";
	public static final String JBANG_LOCAL_ROOT = "JBANG_LOCAL_ROOT";

	public static final String TRUSTED_SOURCES_JSON = "trusted-sources.json";
	public static final String DEPENDENCY_CACHE_JSON = "dependency_cache.json";
	public static final String DEFAULT_JDK = "currentjdk";
	public static final String JBANG_DOT_DIR = ".jbang";
	public static final String BIN_DIR = "bin";
	public static final String EDITOR_DIR = "editor";

	public static final String ENV_DEFAULT_JAVA_VERSION = "JBANG_DEFAULT_JAVA_VERSION";
	public static final String ENV_NO_VERSION_CHECK = "JBANG_NO_VERSION_CHECK";

	public static final int DEFAULT_JAVA_VERSION = 17;
	public static final int DEFAULT_ALPINE_JAVA_VERSION = 16;

	final public static String CP_SEPARATOR = File.pathSeparator;

	final public static String CONFIG_CONNECTION_TIMEOUT = "connection-timeout";
	final public static int DEFAULT_CONNECTION_TIMEOUT = -1;

	final public static String CONFIG_CACHE_EVICT = "cache-evict";
	final public static String DEFAULT_CACHE_EVICT = "PT12H";

	public static Path getJBangLocalMavenRepoOverride() {
		String jbangRepo = System.getenv().get(JBANG_REPO);
		if (jbangRepo != null) {
			return Paths.get(jbangRepo);
		} else {
			return null;
		}
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
			setupJBangDir(dir);

		return dir;
	}

	public static Path getConfigDir() {
		return getConfigDir(true);
	}

	public static Path getDefaultJdkDir() {
		return getConfigDir(true).resolve(DEFAULT_JDK);
	}

	public static Path getConfigBinDir() {
		return getConfigDir(true).resolve(BIN_DIR);
	}

	public static Path getConfigEditorDir() {
		return getConfigDir(true).resolve(EDITOR_DIR);
	}

	/**
	 * This returns the directory where the lookup of "local" files should stop.
	 * Local files are files like `jbang-catalog.json` that are read from the
	 * current directory and then recursively upwards until the root of the file
	 * system has been reached or until the folder returned by this method was
	 * encountered.
	 * 
	 * @return The directory where local lookup should be stopped
	 */
	public static Path getLocalRootDir() {
		Path dir;
		String jlr = System.getenv(JBANG_LOCAL_ROOT);
		if (jlr != null) {
			dir = Paths.get(jlr);
		} else {
			dir = Paths.get(System.getProperty("user.home"));
		}
		return dir;
	}

	public static void setupJBangDir(Path dir) {
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
		Path dir;
		String v = System.getenv(JBANG_CACHE_DIR + "_" + cclass.name().toUpperCase());
		if (v != null) {
			dir = Paths.get(v);
			Cache.setupCache(dir);
		} else {
			dir = getCacheDir().resolve(cclass.name());
		}
		return dir;
	}

	public static int getDefaultJavaVersion() {
		String v = System.getenv(ENV_DEFAULT_JAVA_VERSION);
		if (v != null) {
			return Integer.parseInt(v);
		}
		if (Util.getOS() == Util.OS.alpine_linux) {
			return DEFAULT_ALPINE_JAVA_VERSION;
		} else {
			return DEFAULT_JAVA_VERSION;
		}
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

	public static Path getUserConfigFile() {
		return getConfigDir().resolve(Configuration.JBANG_CONFIG_PROPS);
	}

	public static int getConnectionTimeout() {
		return (int) Configuration.instance().getNumber(CONFIG_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT);
	}

	public static long getCacheEvict() {
		String val = Configuration.instance().get(CONFIG_CACHE_EVICT, DEFAULT_CACHE_EVICT);
		if ("never".equalsIgnoreCase(val)) {
			return -1L;
		} else {
			try {
				// First try to parse as a simple number
				return Long.parseLong(val);
			} catch (NumberFormatException ex) {
				try {
					// If that failed try again using ISO8601 Duration format
					return Duration.parse(val).getSeconds();
				} catch (DateTimeParseException ex2) {
					Util.warnMsg("Invalid duration in config: " + val);
					return 0;
				}
			}
		}
	}

}
