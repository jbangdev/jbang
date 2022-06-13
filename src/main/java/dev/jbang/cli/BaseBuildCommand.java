package dev.jbang.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

import dev.jbang.source.*;

import picocli.CommandLine;

public abstract class BaseBuildCommand extends BaseCommand {

	@CommandLine.Mixin
	ScriptMixin scriptMixin;

	@CommandLine.Mixin
	BuildMixin buildMixin;

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

	RunContext getRunContext() {
		RunContext ctx = new RunContext();
		ctx.setProperties(dependencyInfoMixin.getProperties());
		ctx.setAdditionalDependencies(dependencyInfoMixin.getDependencies());
		ctx.setAdditionalRepositories(dependencyInfoMixin.getRepositories());
		ctx.setAdditionalClasspaths(dependencyInfoMixin.getClasspaths());
		ctx.setAdditionalSources(scriptMixin.sources);
		ctx.setAdditionalResources(scriptMixin.resources);
		ctx.setForceType(scriptMixin.forceType);
		ctx.setCatalog(scriptMixin.catalog);
		ctx.setJavaVersion(buildMixin.javaVersion);
		ctx.setMainClass(buildMixin.main);
		ctx.setNativeImage(buildMixin.nativeImage);
		return ctx;
	}
}
