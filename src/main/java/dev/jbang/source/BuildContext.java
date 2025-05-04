package dev.jbang.source;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.util.Util;

public class BuildContext {
	private final Project project;
	private final Path buildDirOverride;
	private final Path buildDir;

	// Cached values
	private ModularClassPath mcp;

	public static BuildContext forProject(Project project) {
		return forProject(project, null);
	}

	public static BuildContext forProject(Project project, Path buildDirOverride) {
		if (buildDirOverride != null) {
			return new BuildContext(project, buildDirOverride, buildDirOverride);
		} else {
			return new BuildContext(project, null, getBuildDir(null, project));
		}
	}

	@Nonnull
	private static Path getBuildDir(Path baseDir, Project project) {
		if (baseDir == null) {
			baseDir = Settings.getCacheDir(Cache.CacheClass.jars);
		}
		return baseDir.resolve(
				project.getResourceRef().getFile().getFileName() + "." + project.getStableId());
	}

	private BuildContext(Project project, Path buildDirOverride, Path buildDir) {
		this.project = project;
		this.buildDirOverride = buildDirOverride;
		this.buildDir = buildDir;
	}

	public BuildContext forSubProject(Project subProject) {
		return forProject(subProject, getBuildDir(buildDirOverride, subProject));
	}

	public Project getProject() {
		return project;
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

	public Path getJsaFile() {
		if (project.isJShell()) {
			return null;
		}
		return getBasePath(".jsa");
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
		return getJarFile() != null && Files.exists(getJarFile()) && resolveClassPath().isValid();
	}

	@Nonnull
	public ModularClassPath resolveClassPath() {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver();
			project.updateDependencyResolver(resolver);
			for (Project prj : project.getSubProjects()) {
				prj.updateDependencyResolver(resolver);
				resolver.addClassPath(forSubProject(prj).getJarFile().toAbsolutePath().toString());
			}
			mcp = resolver.resolve();
		}
		return mcp;
	}
}
