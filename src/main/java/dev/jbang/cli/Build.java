package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		scriptMixin.validate();
		jdkProvidersMixin.initJdkProviders();

		ProjectBuilder pb = createProjectBuilder();
		Project prj = pb.build(scriptMixin.scriptOrFile);
		prj.builder(BuildContext.forProject(prj, buildDir)).build();

		return EXIT_OK;
	}
}
