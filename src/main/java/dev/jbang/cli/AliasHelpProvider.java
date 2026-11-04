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
}
