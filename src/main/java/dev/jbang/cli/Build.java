package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.source.RunContext;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		scriptMixin.validate();

		RunContext ctx = getRunContext();
		ctx.forResource(scriptMixin.scriptOrFile).builder(ctx).build();

		return EXIT_OK;
	}
}
