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
	static AliasInfo aliasInfo = null;

	final public static String CP_SEPARATOR = System.getProperty("os.name").toLowerCase().contains("windows")
			? ";"
			: ":";

	private static TrustedSources trustedSources;

	public static File getLocalMavenRepo() {
		return new File(System.getenv().getOrDefault("JBANG_REPO", System.getProperty("user.home") + "/.m2/repository"))
																														.getAbsoluteFile();
	}

	public static Path getCacheDependencyFile() {
		return getCacheDir(true).resolve("dependency_cache.txt");
	}

	public static Path getConfigDir(boolean init) {
		Path dir;
		String jd = System.getenv("JBANG_DIR");
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
		String v = System.getenv("JBANG_CACHE_DIR");
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
		return getConfigDir().resolve("trusted-sources.json");
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
		public final String scriptRef;

		Alias(String scriptRef) {
			this.scriptRef = scriptRef;
		}
	}

	static class AliasInfo {
		Map<String, Alias> aliases = new HashMap<>();
	}

	public static Path getAliasesFile() {
		return getConfigDir().resolve("aliases.json");
	}

	private static AliasInfo getAliasInfo() {
		if (aliasInfo == null) {
			Path aliasesFile = getAliasesFile();
			if (Files.isRegularFile(aliasesFile)) {
				try (Reader in = Files.newBufferedReader(aliasesFile)) {
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

		try (Writer out = Files.newBufferedWriter(getAliasesFile())) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(getAliasInfo(), out);
		} catch (IOException ex) {
			Util.warnMsg("Unable to add alias: " + ex.getMessage());
		}
	}

	public static void removeAlias(String name) {
		if (getAliasInfo().aliases.containsKey(name)) {
			try (Writer out = Files.newBufferedWriter(getAliasesFile())) {
				getAliases().remove(name);
				Gson parser = new GsonBuilder().setPrettyPrinting().create();
				parser.toJson(getAliasInfo(), out);
			} catch (IOException ex) {
				Util.warnMsg("Unable to remove alias: " + ex.getMessage());
			}
		}
	}
}
