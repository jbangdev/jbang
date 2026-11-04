package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang trust --help}.
 */
public class TrustHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "jbang only runs scripts from remote URLs you trust, and asks the first time it\n"
				+ "meets a new one. These commands manage that list of trusted URL prefixes.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} add https://github.com/jbangdev/",
				"${COMMAND-FULL-NAME} list",
				"${COMMAND-FULL-NAME} remove https://github.com/jbangdev/");
	}

	/** {@code jbang trust add --help} */
	public static class Add extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Trusts scripts whose URL starts with one of the given prefixes. Keep the prefix\n"
					+ "as narrow as you can, e.g. a user or repository instead of a whole site.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} https://github.com/jbangdev/",
					"${COMMAND-FULL-NAME} https://github.com/myorg/scripts/");
		}
	}
}
