package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.DecoratedSource;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		xrunit = DecoratedSource.forResource(scriptOrFile, null, properties, dependencies, classpaths, fresh,
				forcejsh);

		if (xrunit.needsJar()) {
			build((DecoratedSource) xrunit);
		}

		return EXIT_OK;
	}
}
