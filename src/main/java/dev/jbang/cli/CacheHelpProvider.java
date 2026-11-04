package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang cache --help}.
 */
public class CacheHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "jbang caches downloaded scripts, built jars, dependencies and JDKs to make later\n"
				+ "runs fast. Use these commands to clear them when something looks stale.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} clear",
				"${COMMAND-FULL-NAME} clear --deps",
				"${COMMAND-FULL-NAME} clear --all");
	}

	/** {@code jbang cache clear --help} */
	public static class Clear extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Without options it clears the jar, script, stdin, URL and dependency caches.\n"
					+ "Pick specific caches with the flags below, or use --all to also remove JDKs,\n"
					+ "compilers and temporary projects.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME}",
					"${COMMAND-FULL-NAME} --jar --url",
					"${COMMAND-FULL-NAME} --all");
		}
	}
}
