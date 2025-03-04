package dev.jbang.dependencies;

import static dev.jbang.util.Util.warnMsg;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import dev.jbang.Settings;
import dev.jbang.util.Util;

public class DependencyCache {
	private static Map<String, List<ArtifactInfo>> depCache = null;

	private static Map<String, List<ArtifactInfo>> getCache() {
		if (depCache == null) {
			// Use cached classpath from previous run if present if
			if (Files.isRegularFile(Settings.getCacheDependencyFile())) {
				try (Reader out = Files.newBufferedReader(Settings.getCacheDependencyFile())) {
					JsonDeserializer<ArtifactInfo> serializer = (json, typeOfT, context) -> {
						JsonObject jsonObject = json.getAsJsonObject();
						MavenCoordinate gav = MavenCoordinate.fromCanonicalString(jsonObject.get("gav").getAsString());
						Path file = Paths.get(jsonObject.get("file").getAsString());
						long ts = jsonObject.has("ts") ? jsonObject.get("ts").getAsLong() : 0;
						return new ArtifactInfo(gav, file, ts);
					};
					Gson parser = new GsonBuilder()
						.setPrettyPrinting()
						.registerTypeAdapter(ArtifactInfo.class, serializer)
						.create();

					Type empMapType = new TypeToken<Map<String, List<ArtifactInfo>>>() {
					}.getType();
					depCache = parser.fromJson(out, empMapType);
				} catch (IOException e) {
					Util.errorMsg("Issue writing to dependency cache", e);
					depCache = new HashMap<>();
				}
			} else {
				depCache = new HashMap<>();
			}
		}
		return depCache;
	}

	public static void cache(String depsHash, List<ArtifactInfo> artifacts) {
		// Add classpath to cache

		Map<String, List<ArtifactInfo>> cache = getCache();
		cache.put(depsHash, artifacts);

		try (Writer out = Files.newBufferedWriter(Settings.getCacheDependencyFile())) {
			JsonSerializer<ArtifactInfo> serializer = (src, typeOfSrc, context) -> {
				JsonObject json = new JsonObject();
				json.addProperty("gav", src.getCoordinate().toCanonicalForm());
				json.addProperty("file", src.getFile().toString());
				json.addProperty("ts", src.getTimestamp());
				return json;
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

	public static List<ArtifactInfo> findDependenciesByHash(String depsHash) {
		Map<String, List<ArtifactInfo>> cache = getCache();
		if (cache.containsKey(depsHash)) {
			// Make sure that local dependencies have not been wiped since resolving them
			// (like by deleting .m2) and are up-to-date
			List<ArtifactInfo> cachedCP = cache.get(depsHash);
			boolean allValid = cachedCP.stream().allMatch(ArtifactInfo::isUpToDate);
			if (allValid) {
				return cachedCP;
			} else {
				warnMsg("Detected missing or out-of-date dependencies in cache.");
				if (Util.isVerbose()) {
					cachedCP.stream().filter(ai -> !ai.isUpToDate()).forEach(ai -> {
						if (Files.isReadable(ai.getFile())) {
							Util.verboseMsg(
									"   Artifact out of date: " + ai.getFile() + " : " + ai.getTimestamp() + " != "
											+ ai.getFile().toFile().lastModified());
						} else {
							Util.verboseMsg("   Artifact not found in local cache: " + ai.getFile());
						}
					});
				}
			}
		}
		return null;
	}

	public static void clear() {
		depCache = null;
	}

}
