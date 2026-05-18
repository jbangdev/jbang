package dev.jbang.cli;

import java.io.IOException;

import org.aesh.command.CommandDefinition;

import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;

@CommandDefinition(name = "build", description = "Compiles and stores script in the cache.", generateHelp = true, helpGroup = "Essentials", defaultValueProvider = JBangDefaultValueProvider.class)
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		scriptMixin.validate();

		ProjectBuilder pb = createProjectBuilderForBuild();
		Project prj = pb.build(scriptMixin.scriptOrFile);
		Project.codeBuilder(BuildContext.forProject(prj, getBuildDir())).build();

		return EXIT_OK;
	}

	ProjectBuilder createProjectBuilderForBuild() {
		return createBaseProjectBuilder().mainClass(buildMixin.main);
	}
}
