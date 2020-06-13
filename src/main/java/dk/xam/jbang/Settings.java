package dk.xam.jbang;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.quarkus.qute.Template;

public class Settings {
	private static AliasInfo aliasInfo = null;

	final private static Path JBANG_DIR;
	final private static Path JBANG_CACHE_DIR;
	final private static Path JBANG_ALIASES_FILE;
	final private static Path DEP_LOOKUP_CACHE_FILE;
	final private static Path JBANG_TRUSTED_SOURCES_FILE;
	final public static String CP_SEPARATOR = System.getProperty("os.name").toLowerCase().contains("windows")
			? ";"
			: ":";

	private static TrustedSources trustedSources;

	static {
		String jd = System.getenv("JBANG_DIR");
		if (jd != null) {
			JBANG_DIR = Paths.get(jd);
		} else {
			JBANG_DIR = Paths.get(System.getProperty("user.home")).resolve(".jbang");
		}

		String v = System.getenv("JBANG_CACHE_DIR");
		if (v != null) {
			JBANG_CACHE_DIR = Paths.get(v);
		} else {
			JBANG_CACHE_DIR = JBANG_DIR.resolve("cache");
		}

		DEP_LOOKUP_CACHE_FILE = JBANG_CACHE_DIR.resolve("dependency_cache.txt");

		JBANG_TRUSTED_SOURCES_FILE = JBANG_DIR.resolve("trusted-sources.json");

		setupCache();

		String af = System.getenv("JBANG_ALIASES_FILE");
		if (af != null) {
			JBANG_ALIASES_FILE = Paths.get(af);
		} else {
			JBANG_ALIASES_FILE = JBANG_DIR.resolve("aliases.json");
		}
	}

	public static File getLocalMavenRepo() {
		return new File(System.getenv().getOrDefault("JBANG_REPO", System.getProperty("user.home") + "/.m2/repository"))
																														.getAbsoluteFile();
	}

	public static File getCacheDependencyFile() {
		setupCache();
		return DEP_LOOKUP_CACHE_FILE.toFile();
	}

	public static File getCacheDir(boolean init) {
		if (init)
			setupCache();
		return JBANG_CACHE_DIR.toFile();
	}

	public static File getCacheDir() {
		return getCacheDir(true);
	}

	public static void setupCache() {
		// create cache dir if it does not yet exist
		JBANG_CACHE_DIR.toFile().mkdirs();
	}

	public static File getTrustedSourcesFile() {
		return JBANG_TRUSTED_SOURCES_FILE.toFile();
	}

	void createTrustedSources() {
		if (Files.notExists(JBANG_TRUSTED_SOURCES_FILE)) {
			String templateName = "trusted-sources.qute";
			Template template = Settings.getTemplateEngine().getTemplate(templateName);
			if (template == null)
				throw new ExitException(1, "Could not locate template named: '" + templateName + "'");
			String result = template.render();

			try {
				Util.writeString(JBANG_TRUSTED_SOURCES_FILE, result);
			} catch (IOException e) {
				Util.errorMsg("Could not create initial trusted-sources file at " + JBANG_TRUSTED_SOURCES_FILE, e);
			}

		}
	}

	public static TrustedSources getTrustedSources() {
		if (trustedSources == null) {

			if (Files.isRegularFile(JBANG_TRUSTED_SOURCES_FILE)) {
				try {
					trustedSources = TrustedSources.load(JBANG_TRUSTED_SOURCES_FILE);
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

	public static void setupJBangDir() {
		// create JBang configuration dir if it does not yet exist
		JBANG_DIR.toFile().mkdirs();
	}

	static class Alias {
		final String scriptRef;

		Alias(String scriptRef) {
			this.scriptRef = scriptRef;
		}
	}

	static class AliasInfo {
		Map<String, Alias> aliases = new HashMap<>();
	}

	private static AliasInfo getAliasInfo() {
		if (aliasInfo == null) {
			if (Files.isRegularFile(JBANG_ALIASES_FILE)) {
				try (Reader in = Files.newBufferedReader(JBANG_ALIASES_FILE)) {
					Gson parser = new Gson();
					aliasInfo = parser.fromJson(in, AliasInfo.class);
				} catch (IOException e) {
					// Ignore errors
				}
			} else {
				aliasInfo = new AliasInfo();
			}
		}
		return aliasInfo;
	}

	public static Map<String, Alias> getAliases() {
		return getAliasInfo().aliases;
	}

	public static void addAlias(String name, String scriptRef) {
		if (getAliases().containsKey(scriptRef)) {
			throw new ExitException(1, "Can't create alias to another alias.");
		}
		getAliases().put(name, new Alias(scriptRef));

		setupJBangDir();
		try (Writer out = Files.newBufferedWriter(JBANG_ALIASES_FILE)) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(getAliasInfo(), out);
		} catch (IOException ex) {
			Util.warnMsg("Unable to add alias: " + ex.getMessage());
		}
	}

	public static void removeAlias(String name) {
		if (getAliasInfo().aliases.containsKey(name)) {
			setupJBangDir();
			try (Writer out = Files.newBufferedWriter(JBANG_ALIASES_FILE)) {
				getAliases().remove(name);
				Gson parser = new GsonBuilder().setPrettyPrinting().create();
				parser.toJson(getAliasInfo(), out);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
		}
	}
}
