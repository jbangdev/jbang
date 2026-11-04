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

	/** {@code jbang jdk install --help} */
	public static class Install extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Installs a JDK by major version or id. Pass a path (or an installable archive)\n"
					+ "as well to register a JDK you already have instead of downloading one.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} 21",
					"${COMMAND-FULL-NAME} 21 /opt/java/jdk-21",
					"${COMMAND-FULL-NAME} --force 21");
		}
	}

	/** {@code jbang jdk list --help} */
	public static class ListJdks extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Lists the JDKs jbang has installed. Use --available to see versions you can\n"
					+ "install.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME}",
					"${COMMAND-FULL-NAME} --available",
					"${COMMAND-FULL-NAME} --format=json");
		}
	}

	/** {@code jbang jdk default --help} */
	public static class Default extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Sets the JDK jbang uses by default, installing it if needed. Without an argument\n"
					+ "it shows the current defaults.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} 21",
					"${COMMAND-FULL-NAME}");
		}
	}

	/** {@code jbang jdk java-env --help} */
	public static class JavaEnv extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Prints the shell commands that put a jbang-managed JDK on PATH and set\n"
					+ "JAVA_HOME. Evaluate the output to use that JDK in your current shell.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"eval $(${COMMAND-FULL-NAME} 21)",
					"${COMMAND-FULL-NAME} 21");
		}
	}

	/** {@code jbang jdk exec --help} */
	public static class Exec extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Runs a command with a jbang-managed JDK on PATH and JAVA_HOME: the default JDK,\n"
					+ "or the one given with -j (installed if needed).";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} java -version",
					"${COMMAND-FULL-NAME} -j 21 mvn package");
		}
	}
}
