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

	@CommandLine.Option(names = {
			"--build-dir" }, description = "Use given directory for build results")
	Path buildDir;

	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image")
	boolean nativeImage;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

	ProjectBuilder createProjectBuilder() {
		return ProjectBuilder
								.create()
								.setProperties(dependencyInfoMixin.getProperties())
								.additionalDependencies(dependencyInfoMixin.getDependencies())
								.additionalRepositories(dependencyInfoMixin.getRepositories())
								.additionalClasspaths(dependencyInfoMixin.getClasspaths())
								.additionalSources(scriptMixin.sources)
								.additionalResources(scriptMixin.resources)
								.forceType(scriptMixin.forceType)
								.catalog(scriptMixin.catalog)
								.javaVersion(buildMixin.javaVersion)
								.mainClass(buildMixin.main)
								.nativeImage(nativeImage)
								.buildDir(buildDir);
	}
}
