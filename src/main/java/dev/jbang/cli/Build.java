package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.source.ProjectBuilder;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		scriptMixin.validate();
		jdkProvidersMixin.initJdkProviders();

		ProjectBuilder pb = createProjectBuilder();
		pb.build(scriptMixin.scriptOrFile).builder().build();

		return EXIT_OK;
	}
}
