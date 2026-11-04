package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang app --help}.
 */
public class AppHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Installs scripts, jars, GAVs or aliases as commands on your PATH, so you can run\n"
				+ "them without typing jbang. Run `jbang app setup` once to put the install folder\n"
				+ "on your PATH.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} install --name hello hello.java",
				"${COMMAND-FULL-NAME} list",
				"${COMMAND-FULL-NAME} uninstall hello",
				"${COMMAND-FULL-NAME} setup");
	}

	/** {@code jbang app install --help} */
	public static class Install extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Creates a command that runs the given script, jar, GAV or alias, keeping any run\n"
					+ "options you pass. It is built up front unless you use --no-build.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} --name search gavsearch@jbangdev",
					"${COMMAND-FULL-NAME} --name blaze com.fizzed:blaze-lite:RELEASE",
					"${COMMAND-FULL-NAME} --native --name mytool mytool.java");
		}
	}

	/** {@code jbang app setup --help} */
	public static class Setup extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Adds the folder holding installed commands to your PATH (and optionally jbang's\n"
					+ "Java) by updating your shell setup. Open a new shell afterwards.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME}",
					"${COMMAND-FULL-NAME} --java");
		}
	}
}
