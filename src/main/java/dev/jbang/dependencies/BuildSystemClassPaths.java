package dev.jbang.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import dev.jbang.Cache;
import dev.jbang.ExitException;
import dev.jbang.Settings;
import dev.jbang.util.Util;

/**
 * Entry point for treating a build file (pom.xml, build.gradle, ...) as a JBang
 * dependency. Delegates the tool-specific work to {@link BuildSystem}
 * strategies and layers on filename dispatch, path resolution, and a filesystem
 * cache keyed by (build file path, mtime, size).
 */
public class BuildSystemClassPaths {

	/**
	 * Registered strategies. Order is not significant — each filename should be
	 * claimed by exactly one strategy.
	 */
	private static final List<BuildSystem> SYSTEMS = Arrays.asList(
			new MavenBuildSystem(),
			new GradleBuildSystem(),
			new SbtBuildSystem(),
			new MillBuildSystem());

	public static List<String> dependencies(List<String> dependencies) {
		if (dependencies == null || dependencies.isEmpty()) {
			return Collections.emptyList();
		}
		return dependencies.stream()
			.filter(dep -> !isBuildFileDependency(dep))
			.collect(Collectors.toList());
	}

	public static List<String> classPaths(List<String> dependencies, Path sourceFile) {
		if (dependencies == null || dependencies.isEmpty()) {
			return Collections.emptyList();
		}
		Path sourceDir = sourceFile != null && sourceFile.getParent() != null ? sourceFile.getParent() : Util.getCwd();
		List<String> resolved = new ArrayList<>();
		for (String dependency : dependencies) {
			Path buildFile = toBuildFile(dependency, sourceDir);
			if (buildFile != null) {
				resolved.addAll(resolve(buildFile));
			} else if (isBuildFileDependency(dependency)) {
				throw new ExitException(ExitException.EXIT_INVALID_INPUT, "Build file not found: " + dependency);
			}
		}
		return resolved;
	}

	public static boolean isBuildFileDependency(String dependency) {
		// GAV coordinates (`group:artifact:version[:classifier]`) and JitPack
		// refs are never build files. Filter them out first, matching the
		// classification done by `Directives.sourceDependencies()`. Otherwise
		// `Paths.get("group:artifact:version")` would throw on Windows because
		// colons are not valid path characters there.
		if (isGav(dependency)) {
			return false;
		}
		String name = dependency.startsWith("^") ? dependency.substring(1) : fileName(dependency);
		return name != null && find(name) != null;
	}

	private static boolean isGav(String dependency) {
		return DependencyUtil.looksLikeAPossibleGav(dependency) || JitPackUtil.possibleMatch(dependency);
	}

	private static String fileName(String dependency) {
		Path p = Paths.get(dependency).getFileName();
		return p == null ? null : p.toString();
	}

	private static BuildSystem find(String fileName) {
		for (BuildSystem sys : SYSTEMS) {
			if (sys.supports(fileName)) {
				return sys;
			}
		}
		return null;
	}

	private static Path toBuildFile(String dependency, Path sourceDir) {
		// Same guard as `isBuildFileDependency` — GAV coordinates are not paths
		// on Windows.
		if (isGav(dependency)) {
			return null;
		}
		if (dependency.startsWith("^")) {
			String name = dependency.substring(1);
			if (find(name) == null) {
				return null;
			}
			return Util.findNearestWith(sourceDir, Util.acceptFile(name));
		}
		Path path = Paths.get(dependency);
		if (!path.isAbsolute()) {
			path = sourceDir.resolve(path);
		}
		path = path.normalize();
		String name = path.getFileName() == null ? null : path.getFileName().toString();
		return Files.isRegularFile(path) && name != null && find(name) != null ? path : null;
	}

	private static List<String> resolve(Path buildFile) {
		try {
			List<String> cached = readCache(buildFile);
			if (cached != null) {
				Util.verboseMsg("Using cached compile classpath for " + buildFile);
				return cached;
			}
			BuildSystem system = find(buildFile.getFileName().toString());
			if (system == null) {
				// Should be unreachable because callers filter via find() first.
				throw new ExitException(ExitException.EXIT_INVALID_INPUT,
						"Unsupported build file: " + buildFile);
			}
			Util.infoMsg("Resolving compile classpath from " + buildFile);
			List<String> result = split(system.resolveClassPath(buildFile));
			Util.verboseMsg("Resolved classpath: " + String.join(File.pathSeparator, result));
			writeCache(buildFile, result);
			return result;
		} catch (IOException e) {
			throw new ExitException(ExitException.EXIT_GENERIC_ERROR,
					"Could not resolve classpath from " + buildFile, e);
		}
	}

	private static List<String> split(String classPath) {
		if (Util.isBlankString(classPath)) {
			return Collections.emptyList();
		}
		return Arrays.stream(classPath.trim().split(java.util.regex.Pattern.quote(File.pathSeparator)))
			.filter(p -> !p.isEmpty())
			.collect(Collectors.toList());
	}

	// --- caching -----------------------------------------------------------

	private static Path cacheFile(Path buildFile) {
		Path dir = Settings.getCacheDir(Cache.CacheClass.projects).resolve("buildclasspaths");
		dir.toFile().mkdirs();
		return dir.resolve(hash(buildFile.toAbsolutePath().normalize().toString()) + ".json");
	}

	private static String hash(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(input.hashCode());
		}
	}

	private static class CacheEntry {
		String buildFile;
		long mtime;
		long size;
		List<String> classPath;
	}

	private static List<String> readCache(Path buildFile) {
		try {
			Path cache = cacheFile(buildFile);
			if (!Files.isRegularFile(cache)) {
				return null;
			}
			CacheEntry entry = new Gson().fromJson(Util.readString(cache), CacheEntry.class);
			if (entry == null || entry.classPath == null) {
				return null;
			}
			long mtime = Files.getLastModifiedTime(buildFile).toMillis();
			long size = Files.size(buildFile);
			if (entry.mtime != mtime || entry.size != size) {
				return null;
			}
			// Sanity-check that referenced jars still exist; a stale cache pointing
			// at a wiped local repo would fail with a much less helpful error later.
			for (String p : entry.classPath) {
				if (!Files.exists(Paths.get(p))) {
					return null;
				}
			}
			return entry.classPath;
		} catch (IOException | JsonParseException e) {
			return null;
		}
	}

	private static void writeCache(Path buildFile, List<String> classPath) {
		try {
			CacheEntry entry = new CacheEntry();
			entry.buildFile = buildFile.toAbsolutePath().normalize().toString();
			entry.mtime = Files.getLastModifiedTime(buildFile).toMillis();
			entry.size = Files.size(buildFile);
			entry.classPath = classPath;
			Util.writeString(cacheFile(buildFile), new Gson().toJson(entry));
		} catch (IOException e) {
			Util.verboseMsg("Could not write build classpath cache: " + e.getMessage());
		}
	}
}
