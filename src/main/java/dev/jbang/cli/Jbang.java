package dev.jbang.cli;

import static java.lang.System.err;
import static java.util.Arrays.asList;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Help.Column.Overflow.SPAN;
import static picocli.CommandLine.Help.Column.Overflow.WRAP;
import static picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST;
import static picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.ScopeType;

import java.io.File;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import dev.jbang.DeprecatedMessageHandler;
import dev.jbang.ExitException;
import dev.jbang.Util;
import dev.jbang.VersionProvider;

import picocli.CommandLine;
import picocli.CommandLine.Help.TextTable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.UsageMessageSpec;

@Command(name = "jbang", header = "${COMMAND-NAME} is a tool for building and running .java/.jsh scripts and jar packages.", footer = "\nCopyright: 2020 Max Rydahl Andersen and jbang.dev contributors, License: MIT\nWebsite: https://jbang.dev", description = {
		"",
		"  ${COMMAND-NAME} init hello.java [args...]",
		"        (to initialize a script)",
		"  or  ${COMMAND-NAME} edit --open=code --live hello.java",
		"        (to edit a script in IDE with live updates)",
		"  or  ${COMMAND-NAME} hello.java [args...]",
		"        (to run a .java file)",
		"  or  ${COMMAND-NAME} gavsearch@jbangdev [args...]",
		"        (to run a alias from a catalog)",
		"  or  ${COMMAND-NAME} group-id:artifact-id:version [args...]",
		"        (to run a .jar file found with a GAV id)",

		"" }, versionProvider = VersionProvider.class, subcommands = {
				Run.class, Build.class, Edit.class, Init.class, Alias.class, Catalog.class, Trust.class, Cache.class,
				Completion.class, Jdk.class, Version.class, Wrapper.class, Info.class, App.class, Export.class,
				Config.class })
public class Jbang extends BaseCommand {

	@CommandLine.ArgGroup(exclusive = true)
	Exclusive exclusive = new Exclusive();

	static class Exclusive {
		@Option(names = {
				"--verbose" }, description = "jbang will be verbose on what it does.", scope = ScopeType.INHERIT)
		void setVerbose(boolean verbose) {
			Util.setVerbose(verbose);
		}

		@Option(names = {
				"--quiet" }, description = "jbang will be quiet, only print when error occurs.", scope = ScopeType.INHERIT)
		void setQuiet(boolean quiet) {
			Util.setQuiet(quiet);
		}
	}

	public Integer doCall() {
		spec.commandLine().usage(err);
		return EXIT_OK;
	}

	public static CommandLine getCommandLine() {
		PrintWriter errW = new PrintWriter(err, true);

		return getCommandLine(errW, errW);
	}

	static CommandLine.IExecutionExceptionHandler executionExceptionHandler = new CommandLine.IExecutionExceptionHandler() {
		@Override
		public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult)
				throws Exception {
			Util.errorMsg(null, ex);
			if (Util.isVerbose()) {
				Util.infoMsg(
						"If you believe this a bug in jbang open issue at https://github.com/jbangdev/jbang/issues");
			}

			if (ex instanceof ExitException) {
				return ((ExitException) ex).getStatus();
			} else {
				return EXIT_INTERNAL_ERROR;
			}
		}
	};

	static CommandLine.IExitCodeExceptionMapper exitCodeExceptionMapper = new CommandLine.IExitCodeExceptionMapper() {
		@Override
		public int getExitCode(Throwable t) {
			if (t instanceof ExitException) {
				return ((ExitException) t).getStatus();
			} else if (t instanceof CommandLine.ParameterException) {
				return EXIT_INVALID_INPUT;
			}
			return EXIT_GENERIC_ERROR;
		}
	};

	public static CommandLine getCommandLine(PrintWriter localout, PrintWriter localerr) {
		CommandLine cl = new CommandLine(new Jbang());

		cl.getHelpSectionMap().remove(SECTION_KEY_COMMAND_LIST_HEADING);
		cl.getHelpSectionMap().put(SECTION_KEY_COMMAND_LIST, getCommandRenderer());

		return cl	.setExitCodeExceptionMapper(exitCodeExceptionMapper)
					.setExecutionExceptionHandler(executionExceptionHandler)
					.setParameterExceptionHandler(new DeprecatedMessageHandler(cl.getParameterExceptionHandler()))
					.setStopAtPositional(true)
					.setOut(localout)
					.setErr(localerr);
	}

	public static CommandGroupRenderer getCommandRenderer() {
		Map<String, List<String>> sections = new LinkedHashMap<>();
		sections.put("Essentials", asList("run", "build"));
		sections.put("Editing", asList("init", "edit"));
		sections.put("Caching", asList("cache", "export", "jdk"));
		sections.put("Configuration", asList("config", "trust", "alias", "catalog", "app"));
		sections.put("Other", asList("completion", "info", "version", "wrapper"));
		CommandGroupRenderer renderer = new CommandGroupRenderer(sections);
		return renderer;
	}

	static List<MavenCoordinate> findDeps(File pom) {
		// todo use to dump out pom dependencies
		return Maven.resolver()
					.loadPomFromFile(pom)
					.importCompileAndRuntimeDependencies()
					.resolve()
					.withoutTransitivity()
					.asList(MavenCoordinate.class);
	}

	public static class CommandGroupRenderer implements CommandLine.IHelpSectionRenderer {
		private final Map<String, List<String>> sections;

		public CommandGroupRenderer(Map<String, List<String>> sections) {
			this.sections = sections;
		}

		/**
		 * validate all commands in Help is covered by section and each section command
		 * exist in help.
		 * 
		 * @param help
		 */
		public void validate(CommandLine.Help help) {
			Set<String> cmds = new HashSet<>();
			sections.forEach((key, value) -> cmds.addAll(value));

			Set<String> actualcmds = new HashSet<>(help.subcommands().keySet());

			actualcmds.removeAll(cmds);

			cmds.removeAll(help.subcommands().keySet());

			if (cmds.size() > 0) {
				throw new IllegalStateException("Section help defined for non existent commands" + cmds);
			}

			if (actualcmds.size() > 0) {
				throw new IllegalStateException(("Commands found with no assigned section" + actualcmds));
			}

			sections.forEach((key, value) -> cmds.addAll(value));

		}

		// @Override
		public String render(CommandLine.Help help) {
			if (help.commandSpec().subcommands().isEmpty()) {
				return "";
			}

			StringBuilder result = new StringBuilder();

			sections.forEach((key, value) -> result.append(renderSection(key, value, help)));
			return result.toString();
		}

		private String renderSection(String sectionHeading, List<String> cmdNames, CommandLine.Help help) {
			TextTable textTable = createTextTable(help);

			for (String name : cmdNames) {
				CommandSpec sub = help.commandSpec().subcommands().get(name).getCommandSpec();

				// create comma-separated list of command name and aliases
				String names = sub.names().toString();
				names = names.substring(1, names.length() - 1); // remove leading '[' and trailing ']'

				// description may contain line separators; use Text::splitLines to handle this
				String description = description(sub.usageMessage());
				CommandLine.Help.Ansi.Text[] lines = help.colorScheme().text(String.format(description)).splitLines();

				for (int i = 0; i < lines.length; i++) {
					CommandLine.Help.Ansi.Text cmdNamesText = help.colorScheme().commandText(i == 0 ? names : "");
					textTable.addRowValues(cmdNamesText, lines[i]);
				}
			}
			return help.createHeading("%n" + sectionHeading + ":%n") + textTable.toString();
		}

		private TextTable createTextTable(CommandLine.Help help) {
			CommandSpec spec = help.commandSpec();
			// prepare layout: two columns
			// the left column overflows, the right column wraps if text is too long
			int commandLength = maxLength(spec.subcommands(), 37);
			TextTable textTable = TextTable.forColumns(help.colorScheme(),
					new CommandLine.Help.Column(commandLength + 2, 2, SPAN),
					new CommandLine.Help.Column(spec.usageMessage().width() - (commandLength + 2), 2, WRAP));
			textTable.setAdjustLineBreaksForWideCJKCharacters(
					spec.usageMessage().adjustLineBreaksForWideCJKCharacters());
			return textTable;
		}

		private int maxLength(Map<String, CommandLine> subcommands, int max) {
			int result = subcommands.values()
									.stream()
									.map(cmd -> cmd.getCommandSpec().names().toString().length() - 2)
									.max(Integer::compareTo)
									.get();
			return Math.min(max, result);
		}

		private String description(UsageMessageSpec usageMessage) {
			if (usageMessage.header().length > 0) {
				return usageMessage.header()[0];
			}
			if (usageMessage.description().length > 0) {
				return usageMessage.description()[0];
			}
			return "";
		}
	}
}
