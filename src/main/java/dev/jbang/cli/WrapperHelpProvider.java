package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Prose and examples shown in {@code jbang wrapper --help}.
 */
public class WrapperHelpProvider extends CommandHelpProvider {

	@Override
	protected String prose() {
		return "Adds jbang wrapper scripts to a folder (usually a project), so anyone can run\n"
				+ "your scripts with `./jbang` without installing jbang first.";
	}

	@Override
	protected List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} install",
				"${COMMAND-FULL-NAME} install -d myproject");
	}

	/** {@code jbang wrapper install --help} */
	public static class Install extends CommandHelpProvider {
		@Override
		protected String prose() {
			return "Copies the jbang wrapper (jbang, jbang.cmd, jbang.ps1 and .jbang/jbang.jar) into\n"
					+ "the current folder, or the one given with --dir. Commit them so others can use\n"
					+ "`./jbang`.";
		}

		@Override
		protected List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME}",
					"${COMMAND-FULL-NAME} -d myproject",
					"${COMMAND-FULL-NAME} --force");
		}
	}
}
