package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.Script;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = Script.prepareScript(scriptOrFile, null, properties, dependencies, classpaths);

		if (script.needsJar()) {
			build(script);
		}

		return EXIT_OK;
	}
}
