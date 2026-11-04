package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang template --help}.
 */
public class TemplateHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Templates are the starting points `jbang init --template=<name>` creates scripts\n"
				+ "from. Add your own to a catalog to reuse them across projects.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} list",
				"${COMMAND-FULL-NAME} add --name logo showlogo.java img.jpg",
				"${COMMAND-FULL-NAME} remove logo");
	}

	/** {@code jbang template add --help} */
	public static class Add extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Saves one or more files as a named template. The first file becomes the script;\n"
					+ "the rest are copied alongside it. Use --property for values `jbang init` asks\n"
					+ "for.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} --name logo showlogo.java img.jpg",
					"${COMMAND-FULL-NAME} --global --name tool tool.java",
					"${COMMAND-FULL-NAME} --name greet -P greeting=Greeting::Hello greet.java");
		}
	}
}
