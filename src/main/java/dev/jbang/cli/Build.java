package dev.jbang.cli;

import java.io.IOException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;

import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;

@CommandDefinition(name = "build", description = "Compiles and stores script in the cache.", generateHelp = true, helpGroup = "Essentials", defaultValueProvider = JBangDefaultValueProvider.class)
public class Build extends BaseBuildCommand {

	@Argument(paramLabel = "scriptOrFile", index = "0", arity = "0..1", description = "A file or URL to a Java code file")
	String scriptArg;

	@Override
	public void afterParse() {
		super.afterParse();
		if (scriptArg != null) {
			scriptMixin.scriptOrFile = scriptArg;
		}
	}

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
