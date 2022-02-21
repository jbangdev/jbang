package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.source.RunContext;
import dev.jbang.source.Source;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = getRunContext();
		Source src = ctx.forResource(scriptOrFile);

		buildIfNeeded(src, ctx);

		return EXIT_OK;
	}
}
