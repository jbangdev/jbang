package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.DecoratedSource;
import dev.jbang.RunContext;
import dev.jbang.ScriptSource;
import dev.jbang.Source;

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
		Source src = xrunit.getSource();
		RunContext ctx = xrunit.getContext();

		if (DecoratedSource.needsJar(src, ctx)) {
			build((ScriptSource) xrunit.getSource(), xrunit.getContext());
		}

		return EXIT_OK;
	}
}
