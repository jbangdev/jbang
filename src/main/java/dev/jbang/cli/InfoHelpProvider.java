package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang info --help}.
 */
public class InfoHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Prints details about a script or application for use by IDEs and other tools:\n"
				+ "resolved dependencies, class path, jar location and documentation.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} tools hello.java",
				"${COMMAND-FULL-NAME} classpath hello.java",
				"${COMMAND-FULL-NAME} jar hello.java",
				"${COMMAND-FULL-NAME} docs --open hello.java");
	}

	/** {@code jbang info tools --help} */
	public static class Tools extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Prints a JSON description of a script (sources, dependencies, class path, Java\n"
					+ "version and more) for IDEs and tools. Use --select to print just one field.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} --select=resolvedDependencies hello.java");
		}
	}
}
