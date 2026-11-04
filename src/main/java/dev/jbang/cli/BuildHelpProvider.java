package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang build --help}.
 */
public class BuildHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Resolves dependencies and compiles a script, jar or GAV into jbang's cache\n"
				+ "without running it, so a later `jbang run` starts straight away. Takes the same\n"
				+ "build options as run, including --native.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} hello.java",
				"${COMMAND-FULL-NAME} --fresh hello.java",
				"${COMMAND-FULL-NAME} --native hello.java");
	}
}
