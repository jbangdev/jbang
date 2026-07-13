package dev.jbang.dependencies;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public class BuildSystemClassPaths {

	private static final List<String> BUILD_FILES = Arrays.asList("pom.xml", "build.gradle", "build.gradle.kts");

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
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Build file not found: " + dependency);
			}
		}
		return resolved;
	}

	public static boolean isBuildFileDependency(String dependency) {
		if (dependency.startsWith("^")) {
			return BUILD_FILES.contains(dependency.substring(1));
		}
		Path fileName = Paths.get(dependency).getFileName();
		return fileName != null && BUILD_FILES.contains(fileName.toString());
	}

	private static Path toBuildFile(String dependency, Path sourceDir) {
		if (dependency.startsWith("^")) {
			String fileName = dependency.substring(1);
			if (BUILD_FILES.contains(fileName)) {
				return findNearest(sourceDir, fileName);
			}
			return null;
		}
		Path path = Paths.get(dependency);
		if (!path.isAbsolute()) {
			path = Util.getCwd().resolve(path);
		}
		path = path.normalize();
		if (Files.isRegularFile(path) && BUILD_FILES.contains(path.getFileName().toString())) {
			return path;
		}
		return null;
	}

	private static Path findNearest(Path dir, String fileName) {
		Path cur = dir.toAbsolutePath().normalize();
		while (cur != null && cur.startsWith(Settings.getLocalRootDir())) {
			Path candidate = cur.resolve(fileName);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
			cur = cur.getParent();
		}
		return null;
	}

	private static List<String> resolve(Path buildFile) {
		try {
			Util.infoMsg("Resolving classpath from " + buildFile);
			Path cache = cacheFile(buildFile);
			if (!Util.isFresh() && Files.isRegularFile(cache)) {
				Util.verboseMsg("Using cached classpath from " + cache);
				List<String> cached = split(Util.readString(cache));
				Util.verboseMsg("Resolved classpath: " + String.join(File.pathSeparator, cached));
				return cached;
			}
			Files.createDirectories(cache.getParent());
			List<String> result;
			if (buildFile.getFileName().toString().equals("pom.xml")) {
				runMaven(buildFile, cache);
				result = split(Util.readString(cache));
			} else {
				String cp = runGradle(buildFile);
				Util.writeString(cache, cp);
				result = split(cp);
			}
			Util.verboseMsg("Resolved classpath: " + String.join(File.pathSeparator, result));
			return result;
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR,
					"Could not resolve classpath from " + buildFile, e);
		}
	}

	private static Path cacheFile(Path buildFile) throws IOException {
		String stat = buildFile.toAbsolutePath() + ":" + Files.size(buildFile) + ":"
				+ Files.getLastModifiedTime(buildFile).toMillis();
		return Settings.getCacheDir(Cache.CacheClass.deps)
			.resolve("build-classpath-" + Util.getStableID(stat) + ".txt");
	}

	private static void runMaven(Path pom, Path outputFile) throws IOException {
		Path dir = pom.getParent();
		List<String> command = new ArrayList<>();
		command.add(mavenCommand(dir));
		command.add("-q");
		command.add("dependency:build-classpath");
		command.add("-Dmdep.outputFile=" + outputFile.toAbsolutePath());
		command.add("-Dmdep.pathSeparator=" + File.pathSeparator);
		run(command, dir, false);
	}

	private static String runGradle(Path buildFile) throws IOException {
		Path dir = buildFile.getParent();
		Path init = Settings.getCacheDir(Cache.CacheClass.deps).resolve("jbang-classpath.gradle");
		Util.writeString(init,
				"allprojects { plugins.withId('java') { tasks.register('jbangPrintClasspath') { doLast { println(sourceSets.main.runtimeClasspath.asPath) } } } }\n");
		List<String> command = new ArrayList<>();
		command.add(gradleCommand(dir));
		command.add("-q");
		command.add("-I");
		command.add(init.toString());
		command.add("jbangPrintClasspath");
		return run(command, dir, true);
	}

	private static String mavenCommand(Path dir) {
		Path wrapper = dir.resolve(Util.isWindows() ? "mvnw.cmd" : "mvnw");
		return Files.isRegularFile(wrapper) ? wrapper.toString() : "mvn";
	}

	private static String gradleCommand(Path dir) {
		Path wrapper = dir.resolve(Util.isWindows() ? "gradlew.bat" : "gradlew");
		return Files.isRegularFile(wrapper) ? wrapper.toString() : "gradle";
	}

	private static String run(List<String> command, Path dir, boolean captureOutput) throws IOException {
		Util.verboseMsg("Build classpath: " + String.join(" ", command));
		ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile());
		if (!captureOutput) {
			pb.inheritIO();
		} else {
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		}
		Process process = pb.start();
		String output = "";
		if (captureOutput) {
			try (InputStream is = process.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = is.read(buffer)) != -1) {
					baos.write(buffer, 0, read);
				}
				output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
			}
		}
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, e);
		}
		if (process.exitValue() != 0) {
			throw new ExitException(process.exitValue(),
					"Could not resolve build classpath using: " + String.join(" ", command));
		}
		return output;
	}

	private static List<String> split(String classPath) {
		if (Util.isBlankString(classPath)) {
			return Collections.emptyList();
		}
		return Arrays.stream(classPath.trim().split(java.util.regex.Pattern.quote(File.pathSeparator)))
			.filter(p -> !p.isEmpty())
			.collect(Collectors.toList());
	}
}
