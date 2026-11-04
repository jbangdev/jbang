package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang edit --help}.
 */
public class EditHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Creates a temporary IDE project that links to your script and its dependencies,\n"
				+ "so you get code completion without a build file. It opens the editor given by\n"
				+ "--open (or $JBANG_EDITOR), asking you to pick one if none is set. Use --no-open\n"
				+ "to only print the project path, and --live to regenerate it when dependencies\n"
				+ "change.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} --no-open hello.java",
				"${COMMAND-FULL-NAME} --open=code hello.java",
				"${COMMAND-FULL-NAME} --live --open=idea hello.java",
				"${COMMAND-FULL-NAME} --sandbox hello.java");
	}
}
