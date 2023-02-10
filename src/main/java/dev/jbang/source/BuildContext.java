package dev.jbang.source;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.util.Util;

public class BuildContext {
	private final Project project;
	private final Path buildDir;

	public static BuildContext forProject(Project project) {
		return forProject(project, null);
	}

	public static BuildContext forProject(Project project, Path buildDirOverride) {
		if (buildDirOverride != null) {
			return new BuildContext(project, buildDirOverride);
		} else {
			return new BuildContext(project, getBuildDir(project));
		}
	}

	@Nonnull
	private static Path getBuildDir(Project project) {
		Path baseDir = Settings.getCacheDir(Cache.CacheClass.jars);
		return baseDir.resolve(
				project.getResourceRef().getFile().getFileName() + "." + project.getStableId());
	}

	private BuildContext(Project project, Path buildDir) {
		this.project = project;
		this.buildDir = buildDir;
	}

	public BuildContext forSubProject(Project subProject, String subProjectTypeName) {
		return forProject(subProject, buildDir.resolve(subProjectTypeName));
	}

	public Path getJarFile() {
		if (project.isJShell()) {
			return null;
		} else if (project.isJar()) {
			return project.getResourceRef().getFile();
		} else {
			return getBasePath(".jar");
		}
	}

	public Path getNativeImageFile() {
		if (project.isJShell()) {
			return null;
		}
		if (Util.isWindows()) {
			return getBasePath(".exe");
		} else {
			return getBasePath(".bin");
		}
	}

	@Nonnull
	public Path getCompileDir() {
		return buildDir.resolve("classes");
	}

	@Nonnull
	public Path getGeneratedSourcesDir() {
		return buildDir.resolve("generated");
	}

	@Nonnull
	private Path getBasePath(String extension) {
		return buildDir.resolve(
				Util.sourceBase(project.getResourceRef().getFile().getFileName().toString()) + extension);
	}

	/**
	 * Determines if the associated jar is up-to-date, returns false if it needs to
	 * be rebuilt
	 */
	public boolean isUpToDate() {
		return getJarFile() != null && Files.exists(getJarFile()) && project.resolveClassPath().isValid();
	}
}
