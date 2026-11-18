package dev.jbang.cli;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.aesh.command.CommandDefinition;

import dev.jbang.ExitException;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;

@CommandDefinition(name = "build", description = "Compiles and stores script in the cache.", helpGroup = "Essentials")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {

		ProjectBuilder pb = createProjectBuilderForBuild();
		Project prj = pb.build(scriptMixin.scriptOrFile);
		Project.codeBuilder(BuildContext.forProject(prj, getBuildDir())).build();

		return ExitException.EXIT_OK;
	}

	ProjectBuilder createProjectBuilderForBuild() {
		return createBaseProjectBuilder().mainClass(buildMixin.main);
	}

	@Override
	public String prose() {
		return "Resolves dependencies and compiles a script, jar or GAV into jbang's cache\n"
				+ "without running it, so a later `jbang run` starts straight away. Takes the same\n"
				+ "build options as run, including --native.";
	}

	@Override
	public List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} hello.java",
				"${COMMAND-FULL-NAME} --fresh hello.java",
				"${COMMAND-FULL-NAME} --native hello.java");
	}
}
