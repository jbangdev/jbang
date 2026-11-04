package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang config --help}.
 */
public class ConfigHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Reads and writes jbang settings in jbang.properties. A setting named\n"
				+ "`<command>.<option>` becomes the default for that option, e.g. `edit.open`.\n"
				+ "Changes go to the nearest config file unless you choose --global or --file.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} list",
				"${COMMAND-FULL-NAME} set edit.open code",
				"${COMMAND-FULL-NAME} get edit.open",
				"${COMMAND-FULL-NAME} unset edit.open");
	}

	/** {@code jbang config set --help} */
	public static class Set extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Sets a configuration value, either as separate key and value or as key=value. A\n"
					+ "key of the form `<command>.<option>` sets the default for that option.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} edit.open code",
					"${COMMAND-FULL-NAME} --global run.java 21",
					"${COMMAND-FULL-NAME} format=json");
		}
	}
}
