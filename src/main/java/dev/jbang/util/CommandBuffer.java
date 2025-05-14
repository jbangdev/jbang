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

	// 8192 character command line length limit imposed by CMD.EXE
	public static final int MAX_LENGTH_WINCLI = 8000;
	// Windows API has a limit of 32,768 characters for the lpCommandLine parameter
	public static final int MAX_LENGTH_WINPROCBUILDER = 32000;

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
		List<String> args = arguments.stream()
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

	public CommandBuffer usingArgsFile() throws IOException {
		// @-files avoid problems on Windows with very long command lines
		final Path argsFile = Files.createTempFile("jbang", ".args");
		try (PrintWriter pw = new PrintWriter(argsFile.toFile())) {
			// write all arguments except the first to the file
			for (int i = 1; i < arguments.size(); ++i) {
				pw.println(escapeArgsFileArgument(arguments.get(i)));
			}
		}
		return CommandBuffer.of(arguments.get(0), "@" + argsFile);
	}

	public CommandBuffer applyWindowsMaxLengthLimit(int maxLength, Util.Shell shell) throws IOException {
		String args = asCommandLine(shell);
		// Check if we can and need to use @-files on Windows
		if (args.length() > maxLength && Util.getShell() != Util.Shell.bash) {
			return usingArgsFile();
		} else {
			return this;
		}
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
			// We need to use single quoting to prevent any form of expansion.
			// But to be able to use single quotes inside the argument we need
			// to escape them with a backslash. But if we are already using
			// single quotes then we can not use escapes. To fix that we use
			// a concatenation of single quoted strings with escaped single
			// quotes in between.
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
		if (Util.isWindows() && cmdNeedQuotesChars.matcher(arg).find()) {
			arg = "\"" + arg + "\"";
		}
		return arg;
	}
}
