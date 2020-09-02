package dev.jbang.cli;

import java.io.IOException;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends Run {

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareScript(scriptOrFile, userParams, properties, dependencies, classpaths);

		if (script.needsJar()) {
			build(script);
		}
		return 0;
	}
}
