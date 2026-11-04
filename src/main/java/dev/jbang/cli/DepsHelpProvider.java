package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang deps --help}.
 */
public class DepsHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Finds Maven artifacts and adds them as dependencies to a script's //DEPS lines\n"
				+ "or a build.jbang file.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} search jackson",
				"${COMMAND-FULL-NAME} search jackson app.java",
				"${COMMAND-FULL-NAME} add com.fasterxml.jackson.core:jackson-databind:2.17.0 app.java");
	}

	/** {@code jbang deps search --help} */
	public static class Search extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Interactively searches your local and the central Maven repository. The chosen\n"
					+ "artifact is added to the target file if you give one, otherwise its coordinates\n"
					+ "are printed.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} jackson",
					"${COMMAND-FULL-NAME} jackson app.java",
					"${COMMAND-FULL-NAME} --query=jackson --target=build.jbang");
		}
	}

	/** {@code jbang deps add --help} */
	public static class Add extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Adds one or more groupId:artifactId:version dependencies to the target file,\n"
					+ "which comes last.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} info.picocli:picocli:4.7.6 app.java",
					"${COMMAND-FULL-NAME} org.slf4j:slf4j-api:2.0.13 org.slf4j:slf4j-simple:2.0.13 build.jbang");
		}
	}
}
