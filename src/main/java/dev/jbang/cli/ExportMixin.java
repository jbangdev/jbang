package dev.jbang.cli;

import static dev.jbang.source.builders.BaseBuilder.getImageName;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.RunContext;
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

	Path getFileOutputPath(RunContext ctx) {
		// Determine the output file location and name
		Path cwd = Util.getCwd();
		Path outputPath;
		if (outputFile != null) {
			outputPath = outputFile;
		} else {
			String outName = CatalogUtil.nameFromRef(ctx.getOriginalRef());
			if (buildMixin.nativeImage) {
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