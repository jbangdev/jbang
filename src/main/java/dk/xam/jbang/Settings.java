package dk.xam.jbang;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Properties;

import io.quarkus.qute.Template;

public class Settings {

	final private static File JBANG_CACHE_DIR;
	final private static Path JBANG_ALIASES_FILE;
	final private static File DEP_LOOKUP_CACHE_FILE;
	final private static File JBANG_TRUSTED_SOURCES_FILE;
	final public static String CP_SEPARATOR = System.getProperty("os.name").toLowerCase().contains("windows")
			? ";"
			: ":";

	private static TrustedSources trustedSources;

	final private static Properties aliases = new Properties();
	final private static String ALIASES_FILE_HEADER = "Aliases file, created by JBang";

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

		String af = System.getenv("JBANG_ALIASES_FILE");
		if (af != null) {
			JBANG_ALIASES_FILE = Paths.get(af);
		} else {
			JBANG_ALIASES_FILE = JBANG_CACHE_DIR.toPath().resolve("aliases");
		}
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

	public static Properties getAliases() {
		if (aliases.isEmpty() && Files.isRegularFile(JBANG_ALIASES_FILE)) {
			try (Reader in = Files.newBufferedReader(JBANG_ALIASES_FILE)) {
				aliases.load(in);
			} catch (IOException e) {
				// Ignore errors
			}
		}
		return aliases;
	}

	public static void addAlias(String name, String resource) {
		Properties as = getAliases();
		try (Writer out = Files.newBufferedWriter(JBANG_ALIASES_FILE)) {
			as.setProperty(name, resource);
			as.store(out, ALIASES_FILE_HEADER);
		} catch (IOException ex) {
			Util.warnMsg("Unable to add alias: " + ex.getMessage());
		}
	}

	public static void removeAlias(String name) {
		Properties as = getAliases();
		if (as.containsKey(name)) {
			try (Writer out = Files.newBufferedWriter(JBANG_ALIASES_FILE)) {
				as.remove(name);
				as.store(out, ALIASES_FILE_HEADER);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
		}
	}
}
