package dev.jbang.cli;

import static dev.jbang.source.JarBuilder.getImageName;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.RunContext;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class ExportMixin {

	protected String javaVersion;// TODO: refactor these to be mixins
	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image")
	boolean nativeImage;
	@CommandLine.Option(names = {
			"--insecure" }, description = "Enable insecure trust of all SSL certificates.")
	boolean insecure;
	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;
	@CommandLine.Option(names = { "-O",
			"--output" }, description = "The name or path to use for the exported file. If not specified a name will be determined from the original source reference and export flags.")
	Path outputFile;// mixins todo above
	@CommandLine.Option(names = { "--force",
	}, description = "Force export, i.e. overwrite exported file if already exists")
	boolean force;

	@CommandLine.Parameters(paramLabel = "scriptOrFile", index = "0", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;

	public ExportMixin() {
	}

	@CommandLine.Option(names = { "-j",
			"--java" }, description = "JDK version to use for running the script.")
	void setJavaVersion(String javaVersion) {
		if (!javaVersion.matches("\\d+[+]?")) {
			throw new IllegalArgumentException(
					"Invalid version, should be a number optionally followed by a plus sign");
		}
		this.javaVersion = javaVersion;
	}

	Path getFileOutputPath(
			RunContext ctx) {
		// Determine the output file location and name
		Path cwd = Util.getCwd();
		Path outputPath;
		if (outputFile != null) {
			outputPath = outputFile;
		} else {
			String outName = CatalogUtil.nameFromRef(ctx.getOriginalRef());
			if (nativeImage) {
				outName = getImageName(new File(outName)).getName();
			} else {
				outName += ".jar";
			}
			outputPath = Paths.get(outName);
		}
		outputPath = cwd.resolve(outputPath);
		return outputPath;
	}
}