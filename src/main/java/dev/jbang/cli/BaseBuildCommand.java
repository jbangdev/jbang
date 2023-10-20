package dev.jbang.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
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

	@CommandLine.Mixin
	JdkProvidersMixin jdkProvidersMixin;

	@CommandLine.Option(names = {
			"--build-dir" }, description = "Use given directory for build results")
	Path buildDir;

	@CommandLine.Option(names = { "--enable-preview" }, description = "Activate Java preview features")
	Boolean enablePreviewRequested;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

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
						.enablePreview(enablePreviewRequested);

		// NB: Do not put `.mainClass(buildMixin.main)` here
	}
}
