package dev.jbang;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.JBang;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class Main {
	public static void main(String... args) {
		try {
			// Set up JUL logging so the output looks like JBang output
			LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
		} catch (IOException e) {
			// Ignore
		}

		CommandLine cli = JBang.getCommandLine();
		args = handleDefaultRun(cli.getCommandSpec(), args);
		int exitcode = cli.execute(args);
		System.exit(exitcode);
	}

	public static String[] handleDefaultRun(CommandLine.Model.CommandSpec spec, String[] args) {
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
		// Check if we have a parameter, and it's not the same as any of the subcommand
		// names
		if (!remainingArgs.isEmpty()) {
			String cmd = remainingArgs.get(0);
			if (hasRunOpts(leadingOpts)) {
				List<String> jbangOpts = stripNonInheritedJBangOpts(leadingOpts);
				List<String> result = new ArrayList<>(jbangOpts);
				result.add("run");
				result.addAll(leadingOpts);
				result.addAll(remainingArgs);
				args = result.toArray(args);
			} else if (!spec.subcommands().containsKey(cmd)) {
				if (Catalog.isValidName("jbang-" + cmd) && Alias.get("jbang-" + cmd) != null) {
					// We found a matching "jbang-xxx" alias
					remainingArgs.set(0, "jbang-" + cmd);
				} else if (Catalog.isValidName(cmd) && Alias.get(cmd) != null) {
					// We found an exactly matching alias
					// We do this test because we want aliases to have a higher
					// priority than the next case, which is to look up commands
					// in the user's PATH which might be slow-ish
				} else if (Catalog.isValidName(cmd) && Util.searchPath("jbang-" + cmd) != null) {
					// We found a matching "jbang-xxx" command on the user's PATH
					List<String> result = new ArrayList<>();
					result.add("jbang-" + cmd);
					result.addAll(leadingOpts);
					result.add("--");
					result.addAll(remainingArgs.subList(1, remainingArgs.size()));
					String cmdLine = String.join(" ", result);
					Util.verboseMsg("run plugin: " + cmdLine);
					System.out.println(cmdLine);
					System.exit(BaseCommand.EXIT_EXECUTE);
				} else {
					// In all other cases assume it's an implicit "run" (no need to do anything)
				}
				List<String> jbangOpts = stripNonInheritedJBangOpts(leadingOpts);
				List<String> result = new ArrayList<>(jbangOpts);
				result.add("run");
				result.addAll(leadingOpts);
				result.addAll(remainingArgs);
				args = result.toArray(args);
			}
		}
		return args;
	}

	private static boolean hasRunOpts(List<String> opts) {
		boolean res = opts.contains("-i") || opts.contains("--interactive")
				|| opts.contains("-c") || opts.contains("--code") || opts.contains("--build-dir");
		res = res || opts.stream()
			.anyMatch(o -> o.startsWith("-i=") || o.startsWith("--interactive=")
					|| o.startsWith("-c=") || o.startsWith("--code=") || o.startsWith("--build-dir="));
		return res;
	}

	private static List<String> stripNonInheritedJBangOpts(List<String> opts) {
		List<String> jbangOpts = opts.stream()
			.filter(o -> "--preview".equals(o) || o.startsWith("--preview="))
			.collect(Collectors.toList());
		opts.removeAll(jbangOpts);
		return jbangOpts;
	}
}
