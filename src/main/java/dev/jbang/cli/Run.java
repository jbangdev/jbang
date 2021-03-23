package dev.jbang.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

import dev.jbang.Settings;
import dev.jbang.source.RunContext;
import dev.jbang.source.Source;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Builds and runs provided script.")
public class Run extends BaseBuildCommand {

	// 8192 character command line length limit imposed by CMD.EXE
	private static final int COMMAND_LINE_LENGTH_LIMIT = 8000;

	@CommandLine.Option(names = { "-r",
			"--jfr" }, fallbackValue = "filename={baseName}.jfr", parameterConsumer = KeyValueFallbackConsumer.class, arity = "0..1", description = "Launch with Java Flight Recorder enabled.")
	String flightRecorderString;

	boolean enableFlightRecording() {
		return flightRecorderString != null;
	}

	@CommandLine.Option(names = { "-d",
			"--debug" }, fallbackValue = "4004", parameterConsumer = DebugFallbackConsumer.class, description = "Launch with java debug enabled on specified port (default: ${FALLBACK-VALUE}) ")
	String debugString;

	// should take arguments for package/classes when picocli fixes its flag
	// handling bug in release 4.6.
	// https://docs.oracle.com/cd/E19683-01/806-7930/assert-4/index.html
	@CommandLine.Option(names = { "--enableassertions", "--ea" }, description = "Enable assertions")
	boolean enableAssertions;

	@CommandLine.Option(names = { "--enablesystemassertions", "--esa" }, description = "Enable system assertions")
	boolean enableSystemAssertions;

	boolean debug() {
		return debugString != null;
	}

	@CommandLine.Option(names = { "--javaagent" }, parameterConsumer = KeyOptionalValueConsumer.class)
	Map<String, Optional<String>> javaAgentSlots;

	@CommandLine.Option(names = { "--interactive" }, description = "activate interactive mode")
	boolean interactive;

	@CommandLine.Parameters(index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams = new ArrayList<>();

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = RunContext.create(userParams, dependencyInfoMixin.getProperties(),
				dependencyInfoMixin.getDependencies(), dependencyInfoMixin.getClasspaths(), forcejsh);
		Source src = Source.forResource(scriptOrFile, ctx);
		src = prepareArtifacts(src, ctx);

		String cmdline = generateOSCommandLine(src, ctx);
		debug("run: " + cmdline);
		out.println(cmdline);

		return EXIT_EXECUTE;
	}

	Source prepareArtifacts(Source src, RunContext ctx) throws IOException {
		src = buildIfNeeded(src, ctx);

		if (javaAgentSlots != null) {
			for (Map.Entry<String, Optional<String>> agentOption : javaAgentSlots.entrySet()) {
				String javaAgent = agentOption.getKey();
				Optional<String> javaAgentOptions = agentOption.getValue();

				RunContext actx = RunContext.create(userParams, dependencyInfoMixin.getProperties(),
						dependencyInfoMixin.getDependencies(), dependencyInfoMixin.getClasspaths(), forcejsh);
				Source asrc = Source.forResource(javaAgent, actx);
				actx.setJavaAgentOption(javaAgentOptions.orElse(null));
				if (needsJar(asrc, actx)) {
					info("Building javaagent...");
					asrc = buildIfNeeded(asrc, actx);
				}
				ctx.addJavaAgent(asrc, actx);
			}
		}

		return src;
	}

	String generateOSCommandLine(Source src, RunContext ctx) throws IOException {
		List<String> fullArgs = generateCommandLineList(src, ctx);
		String args = String.join(" ", escapeArguments(fullArgs));
		// This avoids long classpath problem on Windows.
		// @file is only available from java 9 onwards.
		if (args.length() > COMMAND_LINE_LENGTH_LIMIT && Util.isWindows()
				&& JavaUtil.determineJavaVersion() >= 9 && !Util.isUsingPowerShell()) {
			final String javaCmd = fullArgs.get(0);
			StringJoiner joiner = new StringJoiner(" ");
			// we must skip the first value
			for (int i = 1; i < fullArgs.size(); ++i)
				joiner.add(fullArgs.get(i));
			args = joiner.toString();
			final File argsFile = File.createTempFile("jbang", ".args");
			try (PrintWriter pw = new PrintWriter(argsFile)) {
				pw.println(args);
			}

			return javaCmd + " @" + argsFile.toString();
		} else {
			return args;
		}
	}

	String generateCommandLine(Source src, RunContext ctx) throws IOException {
		List<String> fullArgs = generateCommandLineList(src, ctx);
		return String.join(" ", escapeArguments(fullArgs));
	}

	List<String> generateCommandLineList(Source src, RunContext ctx) throws IOException {
		List<String> fullArgs = new ArrayList<>();

		if (nativeImage && (ctx.isForceJsh() || src.isJShell())) {
			warn(".jsh cannot be used with --native thus ignoring --native.");
			nativeImage = false;
		}

		if (nativeImage) {
			String imagename = getImageName(src.getJarFile()).toString();
			if (new File(imagename).exists()) {
				fullArgs.add(imagename);
			} else {
				warn("native built image not found - running in java mode.");
			}
		}

		if (fullArgs.isEmpty()) {
			String classpath = ctx.resolveClassPath(src);
			if (src.getJarFile() != null) {
				if (classpath.trim().isEmpty()) {
					classpath = src.getJarFile().getAbsolutePath();
				} else {
					classpath = src.getJarFile().getAbsolutePath() + Settings.CP_SEPARATOR + classpath.trim();
				}
			}
			List<String> optionalArgs = new ArrayList<>();

			String requestedJavaVersion = javaVersion != null ? javaVersion : src.getJavaVersion();
			String javacmd = resolveInJavaHome("java", requestedJavaVersion);
			if (ctx.isForceJsh() || src.isJShell() || interactive) {

				javacmd = resolveInJavaHome("jshell", requestedJavaVersion);
				if (!classpath.trim().isEmpty()) {
					optionalArgs.add("--class-path=" + classpath);
				}

				optionalArgs.add("--startup=DEFAULT");

				File tempFile = File.createTempFile("jbang_arguments_", src.getResourceRef().getFile().getName());

				String defaultImports = "import java.lang.*;\n" +
						"import java.util.*;\n" +
						"import java.io.*;" +
						"import java.net.*;" +
						"import java.math.BigInteger;\n" +
						"import java.math.BigDecimal;\n";
				Util.writeString(tempFile.toPath(),
						defaultImports + generateArgs(ctx.getArguments(), ctx.getProperties()) +
								generateMain(ctx.getMainClass()));
				if (ctx.getMainClass() != null) {
					if (!ctx.getMainClass().contains(".")) {
						Util.warnMsg("Main class `" + ctx.getMainClass()
								+ "` is in the default package which JShell unfortunately does not support. You can still use JShell to explore the JDK and any dependencies available on the classpath.");
					} else {
						Util.infoMsg("You can run the main class `" + ctx.getMainClass() + "` using: userMain(args)");
					}
				}
				optionalArgs.add("--startup=" + tempFile.getAbsolutePath());

				if (debug()) {
					warn("debug not possible when running via jshell.");
				}
				if (enableFlightRecording()) {
					warn("Java Flight Recording not possible when running via jshell.");
				}

			} else {
				addPropertyFlags(ctx.getProperties(), "-D", optionalArgs);

				// optionalArgs.add("--source 11");
				if (debug()) {
					optionalArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugString);
				}

				if (enableAssertions) {
					optionalArgs.add("-ea");
				}

				if (enableSystemAssertions) {
					optionalArgs.add("-esa");
				}

				if (enableFlightRecording()) {
					// TODO: find way to generate ~/.jbang/script.jfc to configure flightrecorder to
					// have 0 ms thresholds
					String jfropt = "-XX:StartFlightRecording=" + flightRecorderString.replace("{baseName}",
							Util.getBaseName(src.getResourceRef().getFile().toString()));
					optionalArgs.add(jfropt);
					Util.verboseMsg("Flight recording enabled with:" + jfropt);
				}

				if (!classpath.trim().isEmpty()) {
					optionalArgs.add("-classpath");
					optionalArgs.add(classpath);
				}

				if (optionActive(cds(), src.enableCDS())) {
					String cdsJsa = src.getJarFile().getAbsolutePath() + ".jsa";
					if (src.isCreatedJar()) {
						debug("CDS: Archiving Classes At Exit at " + cdsJsa);
						optionalArgs.add("-XX:ArchiveClassesAtExit=" + cdsJsa);
					} else {
						debug("CDS: Using shared archive classes from " + cdsJsa);
						optionalArgs.add("-XX:SharedArchiveFile=" + cdsJsa);
					}
				}
			}

			fullArgs.add(javacmd);
			ctx	.getJavaAgents()
				.forEach(agent -> {
					// for now we don't include any transitive dependencies. could consider putting
					// on bootclasspath...or not.
					String jar = null;
					Source asrc = agent.source;
					if (asrc.getJarFile() != null) {
						jar = asrc.getJarFile().toString();
					} else if (asrc.isJar()) {
						jar = asrc.getResourceRef().getFile().toString();
						// should we log a warning/error if agent jar not present ?
					}
					if (jar == null) {
						throw new ExitException(EXIT_INTERNAL_ERROR,
								"No jar found for agent " + asrc.getResourceRef().getOriginalResource());
					}
					fullArgs.add("-javaagent:" + jar
							+ (agent.context.getJavaAgentOption() != null
									? "=" + agent.context.getJavaAgentOption()
									: ""));

				});

			fullArgs.addAll(ctx.getRuntimeOptionsOr(src));
			fullArgs.addAll(ctx.getAutoDetectedModuleArguments(src, requestedJavaVersion));
			fullArgs.addAll(optionalArgs);

			if (main != null) { // if user specified main class it overrides any other main class calculation
				ctx.setMainClass(main);
			}

			// deduce mainclass or jshell argument but skip it in case interactive for a jar
			// launch.
			String mainClass = ctx.getMainClassOr(src);
			if (!interactive && mainClass != null) {
				fullArgs.add(mainClass);
			} else {
				if (ctx.isForceJsh() || src.isJShell()) {
					fullArgs.add(src.getResourceRef().getFile().toString());
				} else if (!interactive /* && src.isJar() */) {
					throw new ExitException(EXIT_INVALID_INPUT,
							"no main class deduced, specified nor found in a manifest");
				}
			}
		}

		// add additonal java arguments, but if interactive that is passed via jsh
		// script
		// thus if !interactive and not jshell then generate the exit mechanics for
		// jshell to exit at the end.
		if (!ctx.isForceJsh() && !src.isJShell() && !interactive) {
			addJavaArgs(ctx.getArguments(), fullArgs);
		} else if (!interactive) {
			File tempFile = File.createTempFile("jbang_exit_", src.getResourceRef().getFile().getName());
			Util.writeString(tempFile.toPath(), "/exit");
			fullArgs.add(tempFile.toString());
		}

		return fullArgs;
	}

	static boolean optionActive(Optional<Boolean> master, boolean local) {
		return master.map(Boolean::booleanValue).orElse(local);
	}

	private void addPropertyFlags(Map<String, String> properties, String def, List<String> result) {
		properties.forEach((k, e) -> {
			result.add(def + k + "=" + e);
		});
	}

	private void addJavaArgs(List<String> args, List<String> result) {
		result.addAll(args);
	}

	static List<String> escapeArguments(List<String> args) {
		return args.stream().map(Run::escapeArgument).collect(Collectors.toList());
	}

	// NB: This might not be a definitive list of safe characters
	static Pattern cmdSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:;@()-]*");

	// TODO: Figure out what the real list of safe characters is for PowerShell
	static Pattern pwrSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:;@()-]*");

	static Pattern shellSafeChars = Pattern.compile("[a-zA-Z0-9._+=:@%/-]*");

	static String escapeArgument(String arg) {
		if (Util.isWindows()) {
			if (Util.isUsingPowerShell()) {
				if (!pwrSafeChars.matcher(arg).matches()) {
					arg = arg.replaceAll("(['])", "''");
					return "'" + arg + "'";
				}
			} else {
				if (!cmdSafeChars.matcher(arg).matches()) {
					// Windows quoting is just weird
					arg = arg.replaceAll("([()!^<>&|% ])", "^$1");
					arg = arg.replaceAll("([\"])", "\\\\^$1");
					return "^\"" + arg + "^\"";
				}
			}
		} else {
			if (!shellSafeChars.matcher(arg).matches()) {
				arg = arg.replaceAll("(['])", "'\\\\''");
				return "'" + arg + "'";
			}
		}
		return arg;
	}

	static String generateMain(String main) {
		if (main != null) {
			return "\nint userMain(String[] args) { return " + main + "(args);}\n";
		}
		return "";
	}

	static String generateArgs(List<String> args, Map<String, String> properties) {

		String buf = "String[] args = { " +
				args.stream()
					.map(s -> '"' + StringEscapeUtils.escapeJava(s) + '"')
					.collect(Collectors.joining(", "))
				+
				" }" +
				(properties.isEmpty() ? "" : "\n") +
				properties	.entrySet()
							.stream()
							.map(x -> "System.setProperty(\"" + x.getKey() + "\",\"" + x.getValue() + "\");")
							.collect(Collectors.joining("\n"));
		return buf;
	}

	/**
	 * Helper class to peek ahead at `--debug` to pickup --debug=5000, --debug 5000,
	 * --debug *:5000 as debug parameters but not --debug somefile.java
	 */
	static class DebugFallbackConsumer implements CommandLine.IParameterConsumer {

		Pattern p = Pattern.compile("(.*?:)?(\\d+)");

		@Override
		public void consumeParameters(Stack<String> args, CommandLine.Model.ArgSpec argSpec,
				CommandLine.Model.CommandSpec commandSpec) {
			String arg = args.pop();
			Matcher m = p.matcher(arg);
			if (m.matches()) {
				argSpec.setValue(arg);
			} else {
				String fallbackValue = (argSpec.isOption()) ? ((CommandLine.Model.OptionSpec) argSpec).fallbackValue()
						: null;
				try {
					argSpec.setValue(fallbackValue);
				} catch (Exception badFallbackValue) {
					throw new CommandLine.InitializationException("FallbackValue for --debug must be an int",
							badFallbackValue);
				}
				args.push(arg);
			}
		}
	}

	/**
	 * Helper class to peek ahead at `--jfr` to pickup x=y,t=y but not --jfr
	 * somefile.java
	 */
	static class KeyValueFallbackConsumer implements CommandLine.IParameterConsumer {

		Pattern p = Pattern.compile("(\\S*?)=(\\S+)");

		@Override
		public void consumeParameters(Stack<String> args, CommandLine.Model.ArgSpec argSpec,
				CommandLine.Model.CommandSpec commandSpec) {
			String arg = args.pop();
			Matcher m = p.matcher(arg);
			if (m.matches()) {
				argSpec.setValue(arg);
			} else {
				String fallbackValue = (argSpec.isOption()) ? ((CommandLine.Model.OptionSpec) argSpec).fallbackValue()
						: null;
				try {
					argSpec.setValue(fallbackValue);
				} catch (Exception badFallbackValue) {
					throw new CommandLine.InitializationException("FallbackValue for --jfr must be an string",
							badFallbackValue);
				}
				args.push(arg);
			}
		}
	}

}
