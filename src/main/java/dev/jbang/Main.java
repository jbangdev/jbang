package dev.jbang;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.CommandResult;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.cli.JBang;
import dev.jbang.util.Util;
import dev.jbang.util.VersionChecker;

public class Main {
	public static void main(String... args) {
		// Set up JUL logging so the output looks like JBang output
		try {
			LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
		} catch (IOException e) {
			// Ignore
		}

		if (AeshRuntimeRunner.handleDynamicCompletion(args, JBang.class)) {
			return;
		}

		String[] newArgs = handleDefaultRun(args);

		Util.verboseMsg("jbang version " + Util.getJBangVersion());
		Future<String> versionCheckResult = VersionChecker.newerVersionAsync();
		int exitCode = 0;
		try {
			CommandResult result = AeshRuntimeRunner.builder()
				.command(JBang.class)
				.args(newArgs)
				.execute();
			if (result != null) {
				exitCode = result.getResultValue();
			}
		} catch (ExitException e) {
			if (e.getStatus() != 0) {
				Util.errorMsg(null, e);
			}
			exitCode = e.getStatus();
		} catch (Exception e) {
			Util.errorMsg(null, e);
			if (Util.isVerbose()) {
				Util.infoMsg(
						"If you believe this a bug in jbang, open an issue at https://github.com/jbangdev/jbang/issues");
			}
			exitCode = BaseCommand.EXIT_INTERNAL_ERROR;
		} finally {
			VersionChecker.informOrCancel(versionCheckResult);
		}
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	private static Set<String> subcommandNames;

	public static Set<String> getSubcommandNames() {
		if (subcommandNames == null) {
			Set<String> names = new LinkedHashSet<>();
			names.add("run");
			names.add("build");
			names.add("edit");
			names.add("init");
			names.add("alias");
			names.add("template");
			names.add("catalog");
			names.add("trust");
			names.add("cache");
			names.add("completion");
			names.add("jdk");
			names.add("version");
			names.add("wrapper");
			names.add("info");
			names.add("app");
			names.add("export");
			names.add("config");
			names.add("deps");
			subcommandNames = names;
		}
		return subcommandNames;
	}

	public static String[] handleDefaultRun(String[] args) {
		if (args == null) {
			return args;
		}
		// Filter out null entries that can appear when tests pass null in varargs
		boolean hasNulls = false;
		for (String a : args) {
			if (a == null) {
				hasNulls = true;
				break;
			}
		}
		if (hasNulls) {
			List<String> filtered = new ArrayList<>();
			for (String a : args) {
				if (a != null) {
					filtered.add(a);
				}
			}
			args = filtered.toArray(new String[0]);
		}
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
		// Check for deprecated flags in leading options only
		for (String opt : leadingOpts) {
			String key = opt.contains("=") ? opt.substring(0, opt.indexOf("=")) : opt;
			String replacement = getDeprecatedFlagReplacement(key);
			if (replacement != null) {
				System.err.printf(
						"%s is a deprecated and now removed flag. See %s for more details on its replacement.%n",
						key, replacement);
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
						key + " is a deprecated and now removed flag");
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
			} else if (!getSubcommandNames().contains(cmd)) {
				if (Catalog.isValidName("jbang-" + cmd) && Alias.get("jbang-" + cmd) != null) {
					// We found a matching "jbang-xxx" alias
					remainingArgs.set(0, "jbang-" + cmd);
				} else if (Catalog.isValidName(cmd) && Alias.get(cmd) != null) {
					// We found an exactly matching alias.
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
					throw new ExitException(BaseCommand.EXIT_EXECUTE, cmdLine);
				}
				// In all other cases assume it's an implicit "run"
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

	private static String getDeprecatedFlagReplacement(String flag) {
		switch (flag) {
		case "--init":
			return "jbang init --help";
		case "--edit":
		case "--edit-live":
			return "jbang edit --help";
		case "--trust":
			return "jbang trust --help";
		case "--alias":
			return "jbang alias --help";
		default:
			return null;
		}
	}

	private static List<String> stripNonInheritedJBangOpts(List<String> opts) {
		List<String> jbangOpts = opts.stream()
			.filter(o -> "--preview".equals(o) || o.startsWith("--preview="))
			.collect(Collectors.toList());
		opts.removeAll(jbangOpts);
		return jbangOpts;
	}
}
