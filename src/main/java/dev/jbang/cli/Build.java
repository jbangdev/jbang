package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.ExtendedRunUnit;
import dev.jbang.RunUnit;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = RunUnit.forResource(scriptOrFile, null, properties, dependencies, classpaths, fresh,
				forcejsh);

		if (script.needsJar()) {
			build((ExtendedRunUnit) script);
		}

		return EXIT_OK;
	}
}
