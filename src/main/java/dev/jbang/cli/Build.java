package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@CommandLine.Option(names = { "-O",
			"--output" }, description = "Directory to write application binary to.")
	Path outputDir;

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareScript(scriptOrFile, userParams, properties, dependencies, classpaths);

		if (script.needsJar()) {
			build(script);
		}

		if (outputDir != null) {
			// TODO make sure the output names are nicer
			Path jar = script.getJar().toPath();
			if (nativeImage) {
				Path img = getImageName(jar.toFile()).toPath();
				Files.copy(img, outputDir.resolve(img.getFileName()));
			} else {
				Files.copy(jar, outputDir.resolve(jar.getFileName()));
			}
		}

		return EXIT_OK;
	}
}
