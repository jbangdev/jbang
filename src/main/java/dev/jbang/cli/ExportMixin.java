package dev.jbang.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import dev.jbang.catalog.CatalogUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class ExportMixin {

	@CommandLine.Mixin
	ScriptMixin scriptMixin;

	@CommandLine.Mixin
	BuildMixin buildMixin;

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	@CommandLine.Option(names = { "-O",
			"--output" }, description = "The name or path to use for the exported file. If not specified a name will be determined from the original source reference and export flags.")
	Path outputFile;// mixins todo above

	@CommandLine.Option(names = { "--force",
	}, description = "Force export, i.e. overwrite exported file if already exists")
	boolean force;

	public ExportMixin() {
	}

	Path getJarOutputPath() {
		Path outputPath = getOutputPath();
		// Ensure the file ends in `.jar`
		if (!outputPath.toString().endsWith(".jar")) {
			outputPath = Paths.get(outputPath + ".jar");
		}
		return outputPath;
	}

	Path getNativeOutputPath() {
		Path outputPath = getOutputPath();
		// Ensure that on Windows the file ends in `.exe`
		if (Util.isWindows() && !outputPath.toString().endsWith(".exe")) {
			outputPath = Paths.get(outputPath + ".exe");
		}
		return outputPath;
	}

	private Path getOutputPath() {
		// Determine the output file location and name
		Path cwd = Util.getCwd();
		Path outputPath;
		if (outputFile != null) {
			outputPath = outputFile;
		} else {
			String outName = CatalogUtil.nameFromRef(scriptMixin.scriptOrFile);
			outputPath = Paths.get(outName);
		}
		outputPath = cwd.resolve(outputPath);
		return outputPath;
	}
}