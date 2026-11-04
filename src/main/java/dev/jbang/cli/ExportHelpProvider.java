package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang export --help}.
 */
public class ExportHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Builds a script, jar or GAV and exports the result in a form you can ship or keep\n"
				+ "working on: a runnable jar (local, portable or fat), a native executable, a\n"
				+ "minimized JDK image, a Maven repository layout, or a full Gradle/Maven project.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} fatjar hello.java",
				"${COMMAND-FULL-NAME} portable -O dist/app.jar hello.java",
				"${COMMAND-FULL-NAME} native hello.java",
				"${COMMAND-FULL-NAME} maven -g org.acme hello.java");
	}
}
