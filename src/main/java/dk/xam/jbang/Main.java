package dk.xam.jbang;

import java.util.ArrayList;
import java.util.List;

import dk.xam.jbang.cli.Jbang;

import picocli.CommandLine;

public class Main {
	public static void main(String... args) {
		CommandLine cli = Jbang.getCommandLine();
		args = handleDefaultRun(cli.getCommandSpec(), args);
		int exitcode = cli.execute(args);
		System.exit(exitcode);
	}

	private static String[] handleDefaultRun(CommandLine.Model.CommandSpec spec, String[] args) {
		List<String> leadingOpts = new ArrayList<>();
		List<String> remainingArgs = new ArrayList<>();
		boolean foundParam = false;
		for (String arg : args) {
			if (!arg.startsWith("-") || arg.equals("-") || arg.equals("--")) {
				foundParam = true;
			}
			if (foundParam) {
				remainingArgs.add(arg);
			} else {
				leadingOpts.add(arg);
			}
		}
		// Check if we have a parameter and it's not the same as any of the subcommand
		// names
		if (!remainingArgs.isEmpty() && !spec.subcommands().keySet().contains(remainingArgs.get(0))) {
			List<String> result = new ArrayList<>();
			result.add("run");
			result.addAll(leadingOpts);
			result.addAll(remainingArgs);
			args = result.toArray(args);
		}
		return args;
	}
}
