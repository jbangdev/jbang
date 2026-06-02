package dev.jbang.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;

public abstract class BaseBuildCommand extends BaseCommand {

	@Mixin
	ScriptMixin scriptMixin;

	@Mixin
	BuildMixin buildMixin;

	@Mixin
	DependencyInfoMixin dependencyInfoMixin;

	@Mixin
	NativeMixin nativeMixin;

	@Option(name = "build-dir", description = "Use given directory for build results")
	String buildDirStr;

	@Option(name = "enable-preview", hasValue = false, description = "Activate Java preview features")
	Boolean enablePreviewRequested;

	@Override
	public void afterParse() {
		super.afterParse();
		if (buildMixin.javaVersion != null && !buildMixin.javaVersion.matches("\\d+[+]?")) {
			throw new ExitException(EXIT_INVALID_INPUT,
					String.format(
							"Invalid value for '--java': '%s' should be a number optionally followed by a plus sign",
							buildMixin.javaVersion));
		}
		dependencyInfoMixin.applyIgnoreTransitiveRepositories();
	}

	protected JdkManager getJdkManager() {
		return buildMixin.jdkMixin.getJdkManager();
	}

	public Jdk getProjectJdk(Project project) {
		Jdk jdk = project.projectJdk();
		if (buildMixin.javaVersion != null) {
			jdk = getJdkManager().getOrInstallJdk(buildMixin.javaVersion);
		}
		return jdk;
	}

	protected Path getBuildDir() {
		return buildDirStr != null ? Paths.get(buildDirStr) : null;
	}

	protected ProjectBuilder createBaseProjectBuilder() {
		return dev.jbang.source.Project
			.builder()
			.setProperties(dependencyInfoMixin.getProperties())
			.additionalDependencies(dependencyInfoMixin.getDependencies())
			.additionalRepositories(dependencyInfoMixin.getRepositories())
			.additionalClasspaths(dependencyInfoMixin.getClasspaths())
			.additionalSources(scriptMixin.sources)
			.additionalResources(scriptMixin.resources)
			.forceType(scriptMixin.getForceType())
			.catalog(scriptMixin.catalog)
			.javaVersion(buildMixin.javaVersion)
			.moduleName(buildMixin.module)
			.compileOptions(buildMixin.compileOptions)
			.manifestOptions(buildMixin.manifestOptions)
			.nativeImage(nativeMixin.nativeImage)
			.nativeOptions(nativeMixin.nativeOptions)
			.integrations(buildMixin.getIntegrations())
			.enablePreview(enablePreviewRequested)
			.jdkManager(getJdkManager());

		// NB: Do not put .mainClass(main) here
	}
}
