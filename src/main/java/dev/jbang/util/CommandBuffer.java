package dev.jbang.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommandBuffer {
	private List<String> arguments;

	// NB: This might not be a definitive list of safe characters
	// cmdSafeChars = Pattern.compile("[^\\Q&()[]{}^=;!'+,`~<>|\\E]*");
	static Pattern cmdSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:;@()-\\\\]*");
	// TODO: Figure out what the real list of safe characters is for PowerShell
	static Pattern pwrSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:@()\\\\-]*");
	static Pattern shellSafeChars = Pattern.compile("[a-zA-Z0-9._+=:@%/-]*");

	static Pattern cmdNeedQuotesChars = Pattern.compile("[\\Q&()[]{}^=;!'+,`~\\E]");

	public static CommandBuffer of() {
		return new CommandBuffer();
	}

	public static CommandBuffer of(Collection<String> arguments) {
		return new CommandBuffer(arguments);
	}

	public static CommandBuffer of(String... arguments) {
		return new CommandBuffer(arguments);
	}

	public CommandBuffer() {
		arguments = new ArrayList<>();
	}

	public CommandBuffer(Collection<String> arguments) {
		this.arguments = new ArrayList<>(arguments);
	}

	public CommandBuffer(String... arguments) {
		this.arguments = new ArrayList<>(Arrays.asList(arguments));
	}

	public ProcessBuilder asProcessBuilder() {
		return asProcessBuilder(Util.getShell());
	}

	public ProcessBuilder asProcessBuilder(Util.Shell shell) {
		List<String> args = arguments	.stream()
										.map(a -> escapeProcessBuilderArgument(a, shell))
										.collect(Collectors.toList());
		return new ProcessBuilder(args);
	}

	public String asCommandLine() {
		return asCommandLine(Util.getShell());
	}

	public String asCommandLine(Util.Shell shell) {
		return String.join(" ", escapeShellArguments(arguments, shell));
	}

	public String asJavaArgsFile(Util.Shell shell) throws IOException {
		// @-files avoid problems on Windows with very long command lines
		final String cmd = escapeShellArgument(arguments.get(0), shell);
		final Path argsFile = Files.createTempFile("jbang", ".args");
		try (PrintWriter pw = new PrintWriter(argsFile.toFile())) {
			// write all arguments except the first to the file
			for (int i = 1; i < arguments.size(); ++i) {
				pw.println(escapeArgsFileArgument(arguments.get(i)));
			}
		}
		return cmd + " @" + argsFile;
	}

	/**
	 * Escapes list of arguments where necessary using the way of escaping that is
	 * appropriate for the shell we're running in
	 */
	private static List<String> escapeShellArguments(List<String> args, Util.Shell shell) {
		return args.stream().map(arg -> escapeShellArgument(arg, shell)).collect(Collectors.toList());
	}

	public static String escapeShellArgument(String arg, Util.Shell shell) {
		switch (shell) {
		case bash:
			return escapeBashArgument(arg);
		case cmd:
			return escapeCmdArgument(arg);
		case powershell:
			return escapePowershellArgument(arg);
		}
		return arg;
	}

	private static String escapeBashArgument(String arg) {
		if (!shellSafeChars.matcher(arg).matches()) {
			arg = arg.replaceAll("(['])", "'\\\\''");
			arg = "'" + arg + "'";
		}
		return arg;
	}

	private static String escapeCmdArgument(String arg) {
		if (!cmdSafeChars.matcher(arg).matches()) {
			// Windows quoting is just weird
			arg = arg.replaceAll("([()!^<>&|% ])", "^$1");
			arg = arg.replaceAll("([\"])", "\\\\^$1");
			arg = "^\"" + arg + "^\"";
		}
		return arg;
	}

	private static String escapePowershellArgument(String arg) {
		if (!pwrSafeChars.matcher(arg).matches()) {
			arg = arg.replaceAll("(['])", "''");
			arg = "'" + arg + "'";
		}
		return arg;
	}

	private static String escapeArgsFileArgument(String arg) {
		if (!shellSafeChars.matcher(arg).matches()) {
			arg = arg.replaceAll("([\"'\\\\])", "\\\\$1");
			arg = "\"" + arg + "\"";
		}
		return arg;
	}

	private static String escapeProcessBuilderArgument(String arg, Util.Shell shell) {
		// This code is meant to deal with special characters on Windows
		// that get treated as argument separators if left unquoted.
		// (For a list of those characters see the output of `CMD /?`)
		// The code is NOT able to handle double quotes as part of the
		// argument, this is close to impossible to handle on Windows.
		// (see
		// https://stackoverflow.com/questions/30157414/batch-argument-with-quotes-and-spaces)
		if (shell != Util.Shell.bash && cmdNeedQuotesChars.matcher(arg).find()) {
			arg = "\"" + arg + "\"";
		}
		return arg;
	}
}
