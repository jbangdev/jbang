package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang export --help}.
 */
public class ExportHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Builds a script, jar or GAV and exports the result in a form you can ship or\n"
				+ "keep working on: a runnable jar (local, portable or fat), a native executable, a\n"
				+ "minimized JDK image, a Maven repository layout, or a full Gradle/Maven project.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} fatjar hello.java",
				"${COMMAND-FULL-NAME} portable -O dist/app.jar hello.java",
				"${COMMAND-FULL-NAME} native hello.java",
				"${COMMAND-FULL-NAME} maven -g org.acme hello.java");
	}

	/** {@code jbang export local --help} */
	public static class Local extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Exports a jar whose manifest Class-Path points at the dependencies in your local\n"
					+ "jbang/Maven cache. Quick and small, but it only runs on this machine.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} -O hello.jar hello.java");
		}
	}

	/** {@code jbang export portable --help} */
	public static class Portable extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Exports a jar plus a lib/ folder holding its dependencies. Copy both together\n"
					+ "and run it anywhere with `java -jar`.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} -O dist/hello.jar hello.java");
		}
	}

	/** {@code jbang export fatjar --help} */
	public static class Fatjar extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Exports a single executable jar with all dependencies merged in (service files\n"
					+ "are merged, duplicates skipped). Run it with `java -jar`.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} -O app.jar hello.java");
		}
	}

	/** {@code jbang export native --help} */
	public static class Native extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Builds a native executable using GraalVM native-image, so it starts fast and\n"
					+ "needs no JVM to run. Requires native-image to be available.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} -O hello hello.java");
		}
	}

	/** {@code jbang export jlink --help} */
	public static class Jlink extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Creates a minimized JDK image with only the modules your app needs, plus a\n"
					+ "launcher when a main class is known. Works best when the code and its\n"
					+ "dependencies are modules. Pass extra jlink options after --.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} hello.java -- --strip-debug");
		}
	}

	/** {@code jbang export mavenrepo --help} */
	public static class Mavenrepo extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Writes the built jar and a generated POM in Maven repository layout, by default\n"
					+ "into your local ~/.m2 repository. Needs a group ID (--group) unless the script\n"
					+ "declares its GAV.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} -g org.acme hello.java",
					"${COMMAND-FULL-NAME} -g org.acme -a hello -v 1.0 -O build/repo --force hello.java");
		}
	}

	/** {@code jbang export gradle --help} */
	public static class Gradle extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Generates a standalone Gradle project (sources plus build.gradle with your\n"
					+ "dependencies) from a script, for when you outgrow jbang. Source files only.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} -g org.acme -a hello -v 1.0 hello.java");
		}
	}

	/** {@code jbang export maven --help} */
	public static class Maven extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Generates a standalone Maven project (sources plus pom.xml with your\n"
					+ "dependencies) from a script, for when you outgrow jbang. Source files only.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} hello.java",
					"${COMMAND-FULL-NAME} -g org.acme -a hello -v 1.0 hello.java");
		}
	}
}
