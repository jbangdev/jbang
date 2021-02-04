package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.quarkus.qute.Template;

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

	public static final int DEFAULT_JAVA_VERSION = 11;

	final public static String CP_SEPARATOR = File.pathSeparator;

	private static TrustedSources trustedSources;

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
			setupCache(dir);

		return dir;
	}

	public static Path getCacheDir() {
		return getCacheDir(true);
	}

	public static Path getCacheDir(CacheClass cclass) {
		return getCacheDir().resolve(cclass.name());
	}

	private static void setupCache(Path dir) {
		// create cache dir if it does not yet exist
		dir.toFile().mkdirs();
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
		urls, jars, jdks, projects, scripts, stdins, deps
	}

	public static void clearCache(CacheClass... classes) {
		CacheClass[] ccs = classes;
		for (CacheClass cc : ccs) {
			Util.infoMsg("Clearing cache for " + cc.name());
			if (cc == CacheClass.jdks && Util.isWindows() && JdkManager.isCurrentJdkManaged()) {
				// We're running using a managed JDK on Windows so we can't just delete the
				// entire folder!
				for (Integer v : JdkManager.listInstalledJdks()) {
					JdkManager.uninstallJdk(v);
				}
			}
			if (cc == CacheClass.deps) {
				try {
					if (getCacheDependencyFile().toFile().exists()) {
						Util.verboseMsg("Deleting file " + getCacheDependencyFile());
						Files.deleteIfExists(getCacheDependencyFile().toAbsolutePath());
					}
				} catch (IOException io) {
					throw new ExitException(-1,
							"Could not delete dependency cache " + getCacheDependencyFile().toString(), io);
				}
			} else {
				Util.deletePath(getCacheDir(cc), true);
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

	public static Path getUserCatalogFile() {
		return getConfigDir().resolve(AliasUtil.JBANG_CATALOG_JSON);
	}

	public static Path getUserImplicitCatalogFile() {
		return getConfigDir().resolve(AliasUtil.JBANG_IMPLICIT_CATALOG_JSON);
	}

}
