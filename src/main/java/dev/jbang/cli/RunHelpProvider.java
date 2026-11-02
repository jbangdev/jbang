package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang run --help}.
 */
public class RunHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Runs code from almost anywhere: a Maven artifact (GAV coordinate), a `.jar`,\n"
				+ "a catalog alias, a URL, or a local script in Java, JShell, Kotlin, Groovy\n"
				+ "or Markdown. jbang resolves any dependencies, builds when needed, then runs it.\n"
				+ "\n"
				+ "run is the default command, so `jbang <resource>` is shorthand for\n"
				+ "`jbang run <resource>`. Anything after the resource is passed to it as\n"
				+ "arguments rather than to jbang.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} org.example:myapp:1.2.3",
				"${COMMAND-FULL-NAME} myapp.jar",
				"${COMMAND-FULL-NAME} gavsearch@jbangdev org.slf4j:slf4j-api",
				"${COMMAND-FULL-NAME} hello.java",
				"${COMMAND-FULL-NAME} -c 'System.out.println(\"hi\")'");
	}
}
