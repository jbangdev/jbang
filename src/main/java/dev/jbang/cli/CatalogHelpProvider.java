package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang catalog --help}.
 */
public class CatalogHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Catalogs are jbang-catalog.json files that publish aliases and templates,\n"
				+ "locally or on GitHub, GitLab and other hosts. Once added, run their aliases as\n"
				+ "`jbang <alias>@<catalog>`.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} add --name demo jbangdev",
				"${COMMAND-FULL-NAME} list",
				"${COMMAND-FULL-NAME} update",
				"${COMMAND-FULL-NAME} remove demo");
	}

	/** {@code jbang catalog add --help} */
	public static class Add extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Registers a catalog under a name. Give a file, a URL, or a short GitHub\n"
					+ "reference such as `jbangdev` (the jbangdev/jbang-catalog repository).";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} --name demo jbangdev",
					"${COMMAND-FULL-NAME} --name team https://github.com/myteam/jbang-tools",
					"${COMMAND-FULL-NAME} --global --name local ./jbang-catalog.json");
		}
	}
}
