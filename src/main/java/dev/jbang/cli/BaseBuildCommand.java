package dev.jbang.cli;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import dev.jbang.source.*;

import picocli.CommandLine;

public abstract class BaseBuildCommand extends BaseScriptCommand {
	protected String javaVersion;

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	@CommandLine.Option(names = { "-s",
			"--sources" }, converter = CommaSeparatedConverter.class, description = "Add additional sources.")
	List<String> sources;

	@CommandLine.Option(names = { "-r", "--resources" }, description = "Add additional files.")
	List<String> resources;

	@CommandLine.Option(names = { "-m",
			"--main" }, description = "Main class to use when running. Used primarily for running jar's.")
	String main;

	@CommandLine.Option(names = { "-j",
			"--java" }, description = "JDK version to use for running the script.")
	void setJavaVersion(String javaVersion) {
		if (!javaVersion.matches("\\d+[+]?")) {
			throw new IllegalArgumentException(
					"Invalid version, should be a number optionally followed by a plus sign");
		}
		this.javaVersion = javaVersion;
	}

	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image")
	boolean nativeImage;

	@CommandLine.Option(names = { "--catalog" }, description = "Path to catalog file to be used instead of the default")
	File catalog;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

	static Code buildIfNeeded(Code code, RunContext ctx) throws IOException {
		if (needsJar(code, ctx)) {
			SourceSet ss = (SourceSet) code;
			code = ss.builder(ctx).build();
		}
		return code;
	}

	RunContext getRunContext() {
		RunContext ctx = new RunContext();
		ctx.setProperties(dependencyInfoMixin.getProperties());
		ctx.setAdditionalDependencies(dependencyInfoMixin.getDependencies());
		ctx.setAdditionalRepositories(dependencyInfoMixin.getRepositories());
		ctx.setAdditionalClasspaths(dependencyInfoMixin.getClasspaths());
		ctx.setAdditionalSources(sources);
		ctx.setAdditionalResources(resources);
		ctx.setForceJsh(forcejsh);
		ctx.setJavaVersion(javaVersion);
		ctx.setMainClass(main);
		ctx.setNativeImage(nativeImage);
		ctx.setCatalog(catalog);
		return ctx;
	}
}
