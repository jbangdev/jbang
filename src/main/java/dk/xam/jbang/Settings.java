package dk.xam.jbang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import io.quarkus.qute.Template;

public class Settings {

	final private static File JBANG_CACHE_DIR;
	final private static File DEP_LOOKUP_CACHE_FILE;
	final private static File JBANG_TRUSTED_SOURCES_FILE;
	final public static String CP_SEPARATOR = System.getProperty("os.name").toLowerCase().contains("windows")
			? ";"
			: ":";

	private static TrustedSources trustedSources;

	static {
		String v = System.getenv("JBANG_CACHE_DIR");

		if (v != null) {
			JBANG_CACHE_DIR = new File(v);
		} else {
			JBANG_CACHE_DIR = new File(System.getProperty("user.home"), ".jbang");
		}

		DEP_LOOKUP_CACHE_FILE = new java.io.File(JBANG_CACHE_DIR, "dependency_cache.txt");

		JBANG_TRUSTED_SOURCES_FILE = new File(JBANG_CACHE_DIR, "trusted-sources.json");

		setupCache();
	}

	public static File getLocalMavenRepo() {
		return new File(System.getenv().getOrDefault("JBANG_REPO", System.getProperty("user.home") + "/.m2/repository"))
																														.getAbsoluteFile();
	}

	public static File getCacheDependencyFile() {
		setupCache();
		return DEP_LOOKUP_CACHE_FILE;
	}

	public static File getCacheDir(boolean init) {
		if (init)
			setupCache();
		return JBANG_CACHE_DIR;
	}

	public static File getCacheDir() {
		return getCacheDir(true);
	}

	public static void setupCache() {
		// create cache dir if it does not yet exist
		if (!JBANG_CACHE_DIR.exists()) {
			JBANG_CACHE_DIR.mkdir();
		}
	}

	public static File getTrustedSourcesFile() {
		return JBANG_TRUSTED_SOURCES_FILE;
	}

	void createTrustedSources() {
		if (Files.notExists(JBANG_TRUSTED_SOURCES_FILE.toPath())) {
			String templateName = "trusted-sources.qute";
			Template template = Settings.getTemplateEngine().getTemplate(templateName);
			if (template == null)
				throw new ExitException(1, "Could not locate template named: '" + templateName + "'");
			String result = template
									.render();

			try {
				Util.writeString(JBANG_TRUSTED_SOURCES_FILE.toPath(), result);
			} catch (IOException e) {
				Util.errorMsg("Could not create initial trusted-sources file at " + JBANG_TRUSTED_SOURCES_FILE, e);
			}

		}
	}

	public static TrustedSources getTrustedSources() {
		if (trustedSources == null) {

			if (Files.isRegularFile(JBANG_TRUSTED_SOURCES_FILE.toPath())) {
				try {
					trustedSources = TrustedSources.load(JBANG_TRUSTED_SOURCES_FILE.toPath());
				} catch (IOException e) {
					Util.warnMsg("Could not read " + JBANG_TRUSTED_SOURCES_FILE);
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
			Files	.walk(Settings.getCacheDir().toPath())
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
}
