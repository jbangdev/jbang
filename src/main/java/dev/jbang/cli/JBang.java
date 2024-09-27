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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import dev.jbang.Configuration;
import dev.jbang.util.Util;
import dev.jbang.util.VersionChecker;

import picocli.CommandLine;
import picocli.CommandLine.Help.TextTable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.UsageMessageSpec;

@Command(name = "jbang", header = "${COMMAND-NAME} is a tool for building and running .java/.jsh scripts and jar packages.", footer = "\nCopyright: 2020-2024 jbang.dev contributors, License: MIT\nWebsite: https://jbang.dev", description = {
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
		"",
		" note: run is the default command. To get help about run use ${COMMAND-NAME} run --help",

		"" }, versionProvider = VersionProvider.class, subcommands = {
				Run.class, Build.class, Edit.class, Init.class, Alias.class, Template.class, Catalog.class, Trust.class,
				Cache.class, Completion.class, Jdk.class, Version.class, Wrapper.class, Info.class, App.class,
				Export.class, Config.class })
public class JBang extends BaseCommand {

	@CommandLine.Option(names = { "-V",
			"--version" }, versionHelp = true, description = "Display version info (use `jbang --verbose version` for more details)")
	boolean versionRequested;

	@CommandLine.Option(names = { "--preview" }, description = "Enable jbang preview features")
	void setPreview(boolean preview) {
		Util.setPreview(preview);
	}

	@CommandLine.ArgGroup(exclusive = true)
	VerboseQuietExclusive verboseQuietExclusive = new VerboseQuietExclusive();

	static class VerboseQuietExclusive {
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

	@CommandLine.ArgGroup(exclusive = true)
	OfflineFreshExclusive offlineFreshExclusive = new OfflineFreshExclusive();

	static class OfflineFreshExclusive {
		@CommandLine.Option(names = { "-o",
				"--offline" }, description = "Work offline. Fail-fast if dependencies are missing. No connections will be attempted", scope = ScopeType.INHERIT)
		void setOffline(boolean offline) {
			Util.setOffline(offline);
		}

		@CommandLine.Option(names = {
				"--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources.", scope = ScopeType.INHERIT)
		void setFresh(boolean fresh) {
			Util.setFresh(fresh);
		}
	}

	public Integer doCall() {
		spec.commandLine().usage(err);
		return EXIT_OK;
	}

	public static CommandLine getCommandLine() {
		Util.setVerbose(false);
		Util.setQuiet(false);
		Util.setOffline(false);
		Util.setFresh(false);
		Util.setPreview(false);
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
						"If you believe this a bug in jbang, open an issue at https://github.com/jbangdev/jbang/issues");
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

	static CommandLine.IExecutionStrategy executionStrategy = new CommandLine.RunLast() {
		@Override
		protected List<Object> handle(CommandLine.ParseResult parseResult) throws CommandLine.ExecutionException {
			Util.verboseMsg("jbang version " + Util.getJBangVersion());
			Future<String> versionCheckResult = VersionChecker.newerVersionAsync();
			List<Object> result = super.handle(parseResult);
			VersionChecker.informOrCancel(versionCheckResult);
			return result;
		}
	};

	static CommandLine.IDefaultValueProvider defaultValueProvider = new CommandLine.IDefaultValueProvider() {
		@Override
		public String defaultValue(CommandLine.Model.ArgSpec argSpec) {
			String val = null;
			if (argSpec.isOption()
					&& argSpec.defaultValue() == null
					&& Util.isNullOrEmptyString(((CommandLine.Model.OptionSpec) argSpec).fallbackValue())) {
				String key = argSpecKey(argSpec);
				// We skip all "app install" options
				if (!key.startsWith("app.install.")) {
					// First we check the full name, eg "app.list.format"
					val = getValue(key);
					if (val == null) {
						// Finally we check the option name only, eg "format"
						val = getValue(argOptName(argSpec));
					}
				}
			}
			return val;
		}

		private String getValue(String key) {
			String val = null;
			String propkey = "jbang." + key;
			if (System.getProperties().containsValue(propkey)) {
				val = System.getProperty(propkey);
			} else {
				Configuration cfg = Configuration.instance();
				if (cfg.containsKey(key)) {
					val = Objects.toString(cfg.get(key));
				}
			}
			return val;
		}
	};

	static String argSpecKey(CommandLine.Model.ArgSpec argSpec) {
		List<String> cmdNames = names(argSpec.command());
		cmdNames.add(argOptName(argSpec));
		return String.join(".", cmdNames);
	}

	static String argOptName(CommandLine.Model.ArgSpec argSpec) {
		return stripDashes(((CommandLine.Model.OptionSpec) argSpec).longestName()).replace("-", "");
	}

	private static List<String> names(CommandSpec cmd) {
		List<String> result = new ArrayList<>();
		while (!cmd.name().equalsIgnoreCase("jbang")) {
			result.add(0, cmd.name());
			cmd = cmd.parent();
		}
		return result;
	}

	private static String stripDashes(String name) {
		if (name.startsWith("--")) {
			return name.substring(2);
		} else if (name.startsWith("-")) {
			return name.substring(1);
		} else {
			return name;
		}
	}

	static class ConfigurationResourceBundle extends ResourceBundle {

		private static final String PREFIX = "default.";

		@Override
		protected Object handleGetObject(String propkey) {
			if (propkey.startsWith(PREFIX)) {
				String key = propkey.substring(PREFIX.length());
				return Configuration.instance().get(key);
			} else {
				return null;
			}
		}

		@Override
		public Enumeration<String> getKeys() {
			return Collections.enumeration(Configuration.instance()
														.flatten()
														.keySet()
														.stream()
														.map(k -> "default." + k)
														.collect(Collectors.toSet()));
		}
	}

	public static CommandLine getCommandLine(PrintWriter localout, PrintWriter localerr) {
		CommandLine cl = new CommandLine(new JBang());

		cl.getHelpSectionMap().remove(SECTION_KEY_COMMAND_LIST_HEADING);
		cl.getHelpSectionMap().put(SECTION_KEY_COMMAND_LIST, getCommandRenderer());

		return cl	.setExitCodeExceptionMapper(exitCodeExceptionMapper)
					.setExecutionExceptionHandler(executionExceptionHandler)
					.setParameterExceptionHandler(new DeprecatedMessageHandler(cl.getParameterExceptionHandler()))
					.setExecutionStrategy(executionStrategy)
					.setDefaultValueProvider(defaultValueProvider)
					.setResourceBundle(new ConfigurationResourceBundle())
					.setStopAtPositional(true)
					.setAllowOptionsAsOptionParameters(true)
					.setAllowSubcommandsAsOptionParameters(true)
					.setOut(localout)
					.setErr(localerr);
	}

	public static CommandGroupRenderer getCommandRenderer() {
		Map<String, List<String>> sections = new LinkedHashMap<>();
		sections.put("Essentials", asList("run", "build"));
		sections.put("Editing", asList("init", "edit"));
		sections.put("Caching", asList("cache", "export", "jdk"));
		sections.put("Configuration", asList("config", "trust", "alias", "template", "catalog", "app"));
		sections.put("Other", asList("completion", "info", "version", "wrapper"));
		CommandGroupRenderer renderer = new CommandGroupRenderer(sections);
		return renderer;
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
				CommandLine.Help.Ansi.Text[] lines = help.colorScheme().text(description).splitLines();

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
