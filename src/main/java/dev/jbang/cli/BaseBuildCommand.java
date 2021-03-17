package dev.jbang.cli;

import java.nio.file.Path;

import dev.jbang.source.*;

import picocli.CommandLine;

public abstract class BaseBuildCommand extends BaseCommand {

	@CommandLine.Mixin
	ScriptMixin scriptMixin;

	@CommandLine.Mixin
	BuildMixin buildMixin;

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	@CommandLine.Mixin
	NativeMixin nativeMixin;

	@CommandLine.Option(names = {
			"--build-dir" }, description = "Use given directory for build results")
	Path buildDir;

	@CommandLine.Option(names = { "--enable-preview" }, description = "Activate Java preview features")
	Boolean enablePreviewRequested;

	protected ProjectBuilder createBaseProjectBuilder() {
		return Project
			.builder()
			.setProperties(dependencyInfoMixin.getProperties())
			.additionalDependencies(dependencyInfoMixin.getDependencies())
			.additionalRepositories(dependencyInfoMixin.getRepositories())
			.additionalClasspaths(dependencyInfoMixin.getClasspaths())
			.additionalSources(scriptMixin.sources)
			.additionalResources(scriptMixin.resources)
			.forceType(scriptMixin.forceType)
			.catalog(scriptMixin.catalog)
			.javaVersion(buildMixin.javaVersion)
			.moduleName(buildMixin.module)
			.compileOptions(buildMixin.compileOptions)
			.manifestOptions(buildMixin.manifestOptions)
			.nativeImage(nativeMixin.nativeImage)
			.nativeOptions(nativeMixin.nativeOptions)
			.integrations(buildMixin.integrations)
			.enablePreview(enablePreviewRequested)
			.jdkManager(buildMixin.jdkProvidersMixin.getJdkManager());

		// NB: Do not put `.mainClass(buildMixin.main)` here
	}
}
