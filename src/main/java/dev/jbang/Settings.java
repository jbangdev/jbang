package dev.jbang;

import static dev.jbang.Util.warnMsg;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import io.quarkus.qute.Template;

public class Settings {
	static AliasUtil.CatalogInfo catalogInfo = null;

	public static final String JBANG_REPO = "JBANG_REPO";
	public static final String JBANG_DIR = "JBANG_DIR";
	public static final String JBANG_CACHE_DIR = "JBANG_CACHE_DIR";

	public static final String CATALOGS_JSON = "catalogs.json";
	public static final String TRUSTED_SOURCES_JSON = "trusted-sources.json";
	public static final String DEPENDENCY_CACHE_TXT = "dependency_cache.txt";
	public static final String DEPENDENCY_CACHE_JSON = "dependency_cache.json";

	final public static String CP_SEPARATOR = File.pathSeparator;

	private static TrustedSources trustedSources;

	static Map<String, List<ArtifactInfo>> cache = null;

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
		urls, jars, jdks, projects, scripts, stdins
	}

	public static void clearCache(CacheClass... classes) {
		List<CacheClass> ccs = Arrays.asList(classes);
		for (CacheClass cc : ccs) {
			Util.infoMsg("Clearing cache for " + cc.name());
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
				Util.deleteFolder(getCacheDir(cc), true);
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
		try {
			Path cat = Paths.get(catalogRef);
			if (!cat.isAbsolute() && Files.isRegularFile(cat)) {
				catalogRef = cat.toAbsolutePath().toString();
			}
		} catch (InvalidPathException ex) {
			// Ignore
		}
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

	static protected void clearDependencyCache() {
		cache = null;
	}

	protected static void cacheDependencies(String depsHash, List<ArtifactInfo> artifacts) {
		// Add classpath to cache

		if (cache == null) {
			cache = new HashMap<>(1);
		}
		cache.put(depsHash, artifacts);

		try (Writer out = Files.newBufferedWriter(getCacheDependencyFile())) {
			JsonSerializer<ArtifactInfo> serializer = new JsonSerializer<ArtifactInfo>() {
				@Override
				public JsonElement serialize(ArtifactInfo src, Type typeOfSrc, JsonSerializationContext context) {
					JsonObject json = new JsonObject();
					json.addProperty("gav", src.getCoordinate().toCanonicalForm());
					json.addProperty("file", src.asFile().getPath());
					return json;
				}
			};
			Gson parser = new GsonBuilder()
											.setPrettyPrinting()
											.registerTypeAdapter(ArtifactInfo.class, serializer)
											.create();

			parser.toJson(cache, out);
		} catch (IOException e) {
			Util.errorMsg("Issue writing to dependency cache", e);
		}
	}

	protected static List<ArtifactInfo> findDependenciesInCache(String depsHash) {
		// Use cached classpath from previous run if present if
		if (cache == null && Files.isRegularFile(Settings.getCacheDependencyFile())) {

			try (Reader out = Files.newBufferedReader(getCacheDependencyFile())) {
				JsonDeserializer<ArtifactInfo> serializer = new JsonDeserializer<ArtifactInfo>() {
					@Override
					public ArtifactInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
							throws JsonParseException {
						JsonObject jsonObject = json.getAsJsonObject();

						return new ArtifactInfo(MavenCoordinates.createCoordinate(jsonObject.get("gav").getAsString()),
								new File(jsonObject.get("file").getAsString()));
					}
				};
				Gson parser = new GsonBuilder()
												.setPrettyPrinting()
												.registerTypeAdapter(ArtifactInfo.class, serializer)
												.create();

				Type empMapType = new TypeToken<Map<String, List<ArtifactInfo>>>() {
				}.getType();
				cache = parser.fromJson(out, empMapType);
			} catch (IOException e) {
				Util.errorMsg("Issue writing to dependency cache", e);
			}
		}

		if (cache != null && cache.containsKey(depsHash)) {
			List<ArtifactInfo> cachedCP = cache.get(depsHash);

			// Make sure that local dependencies have not been wiped since resolving them
			// (like by deleting .m2)
			boolean allExists = cachedCP.stream().allMatch(it -> it.asFile().exists());
			if (allExists) {
				return cachedCP;
			} else {
				warnMsg("Detected missing dependencies in cache.");
			}
		}
		return null;
	}

}
