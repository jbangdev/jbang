package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.source.RunContext;
import dev.jbang.source.Source;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = RunContext.create(null, dependencyInfoMixin.getProperties(),
				dependencyInfoMixin.getDependencies(), dependencyInfoMixin.getClasspaths(), forcejsh);
		Source src = Source.forResource(scriptOrFile, ctx);

		buildIfNeeded(src, ctx);

		return EXIT_OK;
	}
}
