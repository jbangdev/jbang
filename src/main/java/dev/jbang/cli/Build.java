package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.source.Input;
import dev.jbang.source.RunContext;

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
		Input input = ctx.forResource(scriptOrFile);

		buildIfNeeded(input, ctx);

		return EXIT_OK;
	}
}
