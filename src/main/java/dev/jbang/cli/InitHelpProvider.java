package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang init --help}.
 */
public class InitHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Creates a new script from a template, ready to run with `jbang <file>`. The\n"
				+ "template is picked from the file extension unless you choose one with\n"
				+ "--template; `jbang template list` shows what is available. Give a prompt after\n"
				+ "the file name to have an AI model write the code instead (see the --ai-*\n"
				+ "options).";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} hello.java",
				"${COMMAND-FULL-NAME} --template=cli mytool.java",
				"${COMMAND-FULL-NAME} hello.kt",
				"${COMMAND-FULL-NAME} weather.java \"a CLI that prints the weather for a city\"");
	}
}
