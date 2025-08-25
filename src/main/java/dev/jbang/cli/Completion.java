package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@CommandLine.Command(name = "completion", description = {
		"Generate bash/zsh or fish completion script for ${ROOT-COMMAND-NAME:-the root command of this command}.",
		"Run the following command to give `${ROOT-COMMAND-NAME:-$PARENTCOMMAND}` TAB completion in the current shell:",
		"",
		"  bash/zsh: source <(${PARENT-COMMAND-FULL-NAME:-$PARENTCOMMAND} ${COMMAND-NAME})",
		"",
		"  fish: eval (<(${PARENT-COMMAND-FULL-NAME:-$PARENTCOMMAND} ${COMMAND-NAME} --shell fish)" })
public class Completion extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return completion();
	}

	@CommandLine.Option(names = { "-s",
			"--shell" }, description = "The shell to generate the completion script for. Supported shells: bash (zsh) and fish")
	private String shell = "bash";

	public int completion() throws IOException {

		String script;
		if (shell.equals("bash")) {
			script = AutoComplete.bash(
					spec.parent().name(),
					spec.parent().commandLine());
		} else if (shell.equals("fish")) {
			script = fish(
					spec.parent().name(),
					spec.parent().commandLine());
		} else {
			throw new IllegalArgumentException("Unsupported shell: " + shell);
		}

		// not PrintWriter.println: scripts with Windows line separators fail in strange
		// ways!

		PrintStream out = System.out;
		out.print(script);
		out.print('\n');
		out.flush();
		return EXIT_OK;
	}

	// copied from picocli fish PR https://github.com/remkop/picocli/pull/2463
	// once merged, we can remove this class and use picocli's own fish completion

	private static class CommandDescriptor {
		final String functionName;
		final String parentFunctionName;
		final String parentWithoutTopLevelCommand;
		final String commandName;
		final CommandLine commandLine;

		CommandDescriptor(String functionName, String parentFunctionName, String parentWithoutTopLevelCommand,
				String commandName, CommandLine commandLine) {
			this.functionName = functionName;
			this.parentFunctionName = parentFunctionName;
			this.parentWithoutTopLevelCommand = parentWithoutTopLevelCommand;
			this.commandName = commandName;
			this.commandLine = commandLine;
		}
	}

	private static List<CommandDescriptor> createHierarchy(String scriptName, CommandLine commandLine) {
		List<CommandDescriptor> result = new ArrayList<CommandDescriptor>();
		result.add(new CommandDescriptor("_picocli_" + scriptName, "", "", scriptName, commandLine));
		createSubHierarchy(scriptName, "", commandLine, result);
		return result;
	}

	private static String concat(String infix, String... values) {
		return concat(infix, Arrays.asList(values));
	}

	private static String concat(String infix, List<String> values) {
		return concat(infix, values, null, new NullFunction());
	}

	private static class NullFunction implements Function<CharSequence, String> {
		public String apply(CharSequence value) {
			return value.toString();
		}
	}

	private static <V, T extends V> String concat(String infix, List<T> values, T lastValue,
			Function<V, String> normalize) {
		StringBuilder sb = new StringBuilder();
		for (T val : values) {
			if (sb.length() > 0) {
				sb.append(infix);
			}
			sb.append(normalize.apply(val));
		}
		if (lastValue == null) {
			return sb.toString();
		}
		if (sb.length() > 0) {
			sb.append(infix);
		}
		return sb.append(normalize.apply(lastValue)).toString();
	}

	private static void createSubHierarchy(String scriptName, String parentWithoutTopLevelCommand,
			CommandLine commandLine, List<CommandDescriptor> out) {
		// breadth-first: generate command lists and function calls for predecessors +
		// each subcommand
		for (Map.Entry<String, CommandLine> entry : commandLine.getSubcommands().entrySet()) {
			CommandSpec spec = entry.getValue().getCommandSpec();
			if (spec.usageMessage().hidden()) {
				continue;
			} // #887 skip hidden subcommands
			String commandName = entry.getKey(); // may be an alias
			String functionNameWithoutPrefix = bashify(
					concat("_", parentWithoutTopLevelCommand.replace(' ', '_'), commandName));
			String functionName = concat("_", "_picocli", scriptName, functionNameWithoutPrefix);
			String parentFunctionName = parentWithoutTopLevelCommand.length() == 0
					? concat("_", "_picocli", scriptName)
					: concat("_", "_picocli", scriptName, bashify(parentWithoutTopLevelCommand.replace(' ', '_')));

			// remember the function name and associated subcommand so we can easily
			// generate a function later
			out.add(new CommandDescriptor(functionName, parentFunctionName, parentWithoutTopLevelCommand, commandName,
					entry.getValue()));
		}

		// then recursively do the same for all nested subcommands
		for (Map.Entry<String, CommandLine> entry : commandLine.getSubcommands().entrySet()) {
			if (entry.getValue().getCommandSpec().usageMessage().hidden()) {
				continue;
			} // #887 skip hidden subcommands
			String commandName = entry.getKey();
			String newParent = concat(" ", parentWithoutTopLevelCommand, commandName);
			createSubHierarchy(scriptName, newParent, entry.getValue(), out);
		}
	}

	private static String bashify(CharSequence value) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '_') {
				builder.append(c);
			} else if (Character.isSpaceChar(c)) {
				builder.append('_');
			}
		}
		if (Character.isDigit(builder.charAt(0))) { // #2336 bash variables cannot start with a digit
			builder.insert(0, "_");
		}
		return builder.toString();
	}

	public static String fish(String scriptName, CommandLine commandLine) {
		if (scriptName == null) {
			throw new NullPointerException("scriptName");
		}
		if (commandLine == null) {
			throw new NullPointerException("commandLine");
		}
		List<CommandDescriptor> hierarchy = createHierarchy(scriptName, commandLine);
		StringBuilder result = new StringBuilder();

		String parentFunction = "";
		List<CommandDescriptor> currentLevel = new ArrayList<CommandDescriptor>();
		List<String> currentLevelCommands = new ArrayList<String>();

		CommandDescriptor rootDescriptor = null;
		for (CommandDescriptor descriptor : hierarchy) {
			if (descriptor.parentFunctionName.equals("")) {
				rootDescriptor = descriptor;
				parentFunction = descriptor.functionName;
				continue;
			}
			if (!descriptor.parentFunctionName.equals(parentFunction)) {
				processLevel(scriptName, result, currentLevel, currentLevelCommands, parentFunction, rootDescriptor);
				rootDescriptor = null;

				currentLevel.clear();
				currentLevelCommands.clear();
				parentFunction = descriptor.parentFunctionName;
			}

			currentLevel.add(descriptor);
			currentLevelCommands.add(descriptor.commandName);
		}
		processLevel(scriptName, result, currentLevel, currentLevelCommands, parentFunction, rootDescriptor);

		return result.toString();
	}

	private static void processLevel(String scriptName, StringBuilder result, List<CommandDescriptor> currentLevel,
			List<String> currentLevelCommands, String levelName,
			CommandDescriptor rootDescriptor) {
		if (levelName.equals("")) {
			levelName = "root";
		}

		// fish doesn't like dashes in variable names
		levelName = levelName.replaceAll("-", "_");

		result.append("\n# ").append(levelName).append(" completion\n");
		result.append("set -l ").append(levelName);
		if (!currentLevelCommands.isEmpty()) {
			result.append(" ").append(join(" ", currentLevelCommands));
		}
		result.append("\n");
		if (rootDescriptor != null) {
			String condition = " --condition \"not __fish_seen_subcommand_from $" + levelName + "\"";
			for (OptionSpec optionSpec : rootDescriptor.commandLine.getCommandSpec().options()) {
				completeFishOption(scriptName, optionSpec, condition, result);
			}
		}
		for (CommandDescriptor commandDescriptor : currentLevel) {

			result.append("complete -c ").append(scriptName);
			result.append(" --no-files"); // do not show files
			result.append(" --condition \"not __fish_seen_subcommand_from $").append(levelName).append("\"");
			if (!commandDescriptor.parentWithoutTopLevelCommand.equals("")) {
				result.append(" --condition '__fish_seen_subcommand_from ")
					.append(
							commandDescriptor.parentWithoutTopLevelCommand)
					.append("'");
			}

			result.append(" --arguments ").append(commandDescriptor.commandName);

			String[] descriptions = commandDescriptor.commandLine.getCommandSpec().usageMessage().description();
			String description = descriptions.length > 0 ? descriptions[0] : "";
			result.append(" -d '").append(sanitizeFishDescription(description)).append("'\n");

			String condition = getFishCondition(commandDescriptor);
			for (OptionSpec optionSpec : commandDescriptor.commandLine.getCommandSpec().options()) {
				completeFishOption(scriptName, optionSpec, condition, result);
			}
		}
	}

	private static String getFishCondition(CommandDescriptor commandDescriptor) {
		StringBuilder condition = new StringBuilder();
		condition.append(" --condition \"__fish_seen_subcommand_from ")
			.append(commandDescriptor.commandName)
			.append("\"");
		if (!commandDescriptor.parentWithoutTopLevelCommand.equals("")) {
			condition.append(" --condition '__fish_seen_subcommand_from ")
				.append(
						commandDescriptor.parentWithoutTopLevelCommand)
				.append("'");
		}
		return condition.toString();
	}

	private static void completeFishOption(String scriptName, OptionSpec optionSpec, String conditions,
			StringBuilder result) {
		result.append("complete -c ").append(scriptName);
		result.append(conditions);
		result.append(" --long-option ").append(optionSpec.longestName().replace("--", ""));

		if (!optionSpec.shortestName().equals(optionSpec.longestName())) {
			result.append(" --short-option ").append(optionSpec.shortestName().replace("-", ""));
		}

		if (optionSpec.completionCandidates() != null) {
			result.append(" --no-files --arguments '")
				.append(join(" ", extract(optionSpec.completionCandidates())))
				.append("' ");
		}

		String optionDescription = sanitizeFishDescription(
				optionSpec.description().length > 0 ? optionSpec.description()[0] : "");
		result.append(" -d '").append(optionDescription).append("'\n");
	}

	private static List<String> extract(Iterable<String> generator) {
		List<String> result = new ArrayList<String>();
		for (String e : generator) {
			result.add(e);
		}
		return result;
	}

	private static String sanitizeFishDescription(String description) {
		return description.replace("'", "\\'");
	}

	private static String join(String delimeter, List<String> list) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				result.append(delimeter);
			}
			result.append(list.get(i));
		}
		return result.toString();
	}
}
