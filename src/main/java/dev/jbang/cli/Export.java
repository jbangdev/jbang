package dev.jbang.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import dev.jbang.Util;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the result of a build.")
public class Export extends BaseBuildCommand {

	@CommandLine.Option(names = { "-O",
			"--output" }, description = "The name or path to use for the exported file. If not specified a name will be determined from the original source ref")
	Path outputFile;

	@CommandLine.Option(names = { "--force",
	}, description = "Force export, i.e. overwrite exported file if already exists", defaultValue = "false")
	boolean force;

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareScript(scriptOrFile, null, properties, dependencies, classpaths);

		if (script.needsJar()) {
			build(script);
		}

		// Determine the output file location and name
		Path cwd = Util.getCwd();
		Path outputPath;
		if (outputFile != null) {
			outputPath = outputFile;
		} else {
			String outName = AppInstall.chooseCommandName(script);
			if (nativeImage) {
				outName = getImageName(new File(outName)).getName();
			} else {
				outName += ".jar";
			}
			outputPath = Paths.get(outName);
		}
		outputPath = cwd.resolve(outputPath);

		// Copy the JAR or native binary
		Path source = script.getJar().toPath();
		if (nativeImage) {
			source = getImageName(source.toFile()).toPath();
		}

		if (outputPath.toFile().exists()) {
			if (force) {
				outputPath.toFile().delete();
			} else {
				warn("Cannot export as " + outputPath + " already exists. Use --force to overwrite.");
				return EXIT_INVALID_INPUT;
			}
		}

		Files.copy(source, outputPath);
		info("Exported to " + outputPath);
		return EXIT_OK;
	}
}
