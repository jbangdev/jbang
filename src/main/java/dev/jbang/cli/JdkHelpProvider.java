package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang jdk --help}.
 */
public class JdkHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Installs and manages the JDKs jbang uses. JDKs are normally fetched on demand\n"
				+ "when a script needs one; use these commands to pre-install versions, pick the\n"
				+ "default, or point other tools at a jbang-managed JDK.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} install 21",
				"${COMMAND-FULL-NAME} list",
				"${COMMAND-FULL-NAME} default 21",
				"${COMMAND-FULL-NAME} home 21");
	}
}
