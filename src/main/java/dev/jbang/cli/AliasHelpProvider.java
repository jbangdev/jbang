package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang alias --help}.
 */
public class AliasHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Aliases give a short name to a script, jar or GAV (plus any options), so you can\n"
				+ "run it with `jbang <name>`. They are stored in the nearest jbang-catalog.json\n"
				+ "(falling back to your user catalog), or use --global / --file to choose.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} add --name hello hello.java",
				"${COMMAND-FULL-NAME} add --global --name cowsay org.example:cowsay:1.0",
				"${COMMAND-FULL-NAME} list",
				"${COMMAND-FULL-NAME} remove hello");
	}

	/** {@code jbang alias add --help} */
	public static class Add extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Saves a script, jar or GAV under a name, together with any options and arguments\n"
					+ "you give (dependencies, runtime options, ...), so `jbang <name>` reproduces it.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} --name hello hello.java",
					"${COMMAND-FULL-NAME} --name cowsay --deps org.slf4j:slf4j-simple:2.0.9 cowsay.java",
					"${COMMAND-FULL-NAME} --name greet hello.java World",
					"${COMMAND-FULL-NAME} --global --name tool org.example:tool:1.0 serve");
		}
	}

	/** {@code jbang alias list --help} */
	public static class ListAliases extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Lists the aliases you can run: from the nearest catalogs, or from a named\n"
					+ "catalog when you give one.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME}",
					"${COMMAND-FULL-NAME} --show-origin",
					"${COMMAND-FULL-NAME} jbangdev");
		}
	}
}
