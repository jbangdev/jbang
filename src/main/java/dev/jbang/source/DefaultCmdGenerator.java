package dev.jbang.source;

import static dev.jbang.source.builders.BaseBuilder.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class DefaultCmdGenerator implements CmdGenerator {
	private Util.Shell shell = Util.getShell();

	// 8192 character command line length limit imposed by CMD.EXE
	private static final int COMMAND_LINE_LENGTH_LIMIT = 8000;

	public DefaultCmdGenerator setShell(Util.Shell shell) {
		this.shell = shell;
		return this;
	}

	@Override
	public String generate(Input input, RunContext ctx) throws IOException {
		List<String> fullArgs = generateCommandLineList(input, ctx);
		String args = String.join(" ", escapeOSArguments(fullArgs, shell));
		// Check if we need to use @-files on Windows
		boolean useArgsFile = false;
		if (args.length() > COMMAND_LINE_LENGTH_LIMIT && Util.getShell() != Util.Shell.bash) {
			// @file is only available from java 9 onwards.
			String requestedJavaVersion = ctx.getJavaVersion() != null ? ctx.getJavaVersion()
					: input.getJavaVersion().orElse(null);
			int actualVersion = JavaUtil.javaVersion(requestedJavaVersion);
			useArgsFile = actualVersion >= 9;
		}
		if (useArgsFile) {
			// @-files avoid problems on Windows with very long command lines
			final String javaCmd = escapeOSArgument(fullArgs.get(0), shell);
			final File argsFile = File.createTempFile("jbang", ".args");
			try (PrintWriter pw = new PrintWriter(argsFile)) {
				// write all arguments except the first to the file
				for (int i = 1; i < fullArgs.size(); ++i) {
					pw.println(escapeArgsFileArgument(fullArgs.get(i)));
				}
			}

			return javaCmd + " @" + argsFile;
		} else {
			return args;
		}
	}

	String generateCommandLine(Input input, RunContext ctx) throws IOException {
		List<String> fullArgs = generateCommandLineList(input, ctx);
		return String.join(" ", escapeOSArguments(fullArgs, shell));
	}

	List<String> generateCommandLineList(Input input, RunContext ctx) throws IOException {
		List<String> fullArgs = new ArrayList<>();

		if (ctx.isNativeImage()) {
			String imagename = getImageName(input.getJarFile()).toString();
			if (new File(imagename).exists()) {
				fullArgs.add(imagename);
			} else {
				Util.warnMsg("native built image not found - running in java mode.");
			}
		}

		if (fullArgs.isEmpty()) {
			String classpath = ctx.resolveClassPath(input);

			List<String> optionalArgs = new ArrayList<>();

			String requestedJavaVersion = ctx.getJavaVersion() != null ? ctx.getJavaVersion()
					: input.getJavaVersion().orElse(null);
			String javacmd;
			if (ctx.isForceJsh() || input.isJShell() || ctx.isInteractive()) {
				javacmd = JavaUtil.resolveInJavaHome("jshell", requestedJavaVersion);

				if (input.getJarFile() != null && input.getJarFile().exists()) {
					if (Util.isBlankString(classpath)) {
						classpath = input.getJarFile().getAbsolutePath();
					} else {
						classpath = input.getJarFile().getAbsolutePath() + Settings.CP_SEPARATOR + classpath.trim();
					}
				}

				// NB: See https://github.com/jbangdev/jbang/issues/992 for the reasons why we
				// use the -J flags below

				optionalArgs.add("--execution=local");
				optionalArgs.add("-J--add-modules=ALL-SYSTEM");

				if (!Util.isBlankString(classpath)) {
					optionalArgs.add("--class-path=" + classpath);
					optionalArgs.add("-J--class-path=" + classpath);
				}

				optionalArgs.add("--startup=DEFAULT");

				File tempFile = File.createTempFile("jbang_arguments_", input.getResourceRef().getFile().getName());

				String defaultImports = "import java.lang.*;\n" +
						"import java.util.*;\n" +
						"import java.io.*;" +
						"import java.net.*;" +
						"import java.math.BigInteger;\n" +
						"import java.math.BigDecimal;\n";
				Util.writeString(tempFile.toPath(),
						defaultImports + generateArgs(ctx.getArguments(), ctx.getProperties()) +
								generateStdInputHelper() +
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

				if (ctx.isDebugEnabled()) {
					Util.warnMsg("debug not possible when running via jshell.");
				}
				if (ctx.isFlightRecordingEnabled()) {
					Util.warnMsg("Java Flight Recording not possible when running via jshell.");
				}

			} else {
				javacmd = JavaUtil.resolveInJavaHome("java", requestedJavaVersion);

				addPropertyFlags(ctx.getProperties(), "-D", optionalArgs);

				// optionalArgs.add("--source 11");
				if (ctx.isDebugEnabled()) {
					optionalArgs.add(
							"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + ctx.getDebugString());
				}

				if (ctx.isEnableAssertions()) {
					optionalArgs.add("-ea");
				}

				if (ctx.isEnableSystemAssertions()) {
					optionalArgs.add("-esa");
				}

				if (ctx.isFlightRecordingEnabled()) {
					// TODO: find way to generate ~/.jbang/script.jfc to configure flightrecorder to
					// have 0 ms thresholds
					String jfropt = "-XX:StartFlightRecording=" + ctx	.getFlightRecorderString()
																		.replace("{baseName}",
																				Util.getBaseName(input	.getResourceRef()
																										.getFile()
																										.toString()));
					optionalArgs.add(jfropt);
					Util.verboseMsg("Flight recording enabled with:" + jfropt);
				}

				if (input.getJarFile() != null) {
					if (Util.isBlankString(classpath)) {
						classpath = input.getJarFile().getAbsolutePath();
					} else {
						classpath = input.getJarFile().getAbsolutePath() + Settings.CP_SEPARATOR + classpath.trim();
					}
				}
				if (!Util.isBlankString(classpath)) {
					optionalArgs.add("-classpath");
					optionalArgs.add(classpath);
				}

				if (Optional.ofNullable(ctx.getClassDataSharing()).orElse(input.enableCDS())) {
					String cdsJsa = input.getJarFile().getAbsolutePath() + ".jsa";
					if (input instanceof SourceSet && input.getJarFile().exists()) {
						Util.verboseMsg("CDS: Archiving Classes At Exit at " + cdsJsa);
						optionalArgs.add("-XX:ArchiveClassesAtExit=" + cdsJsa);
					} else {
						Util.verboseMsg("CDS: Using shared archive classes from " + cdsJsa);
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
					Input asrc = agent.source;
					if (asrc.getJarFile() != null) {
						jar = asrc.getJarFile().toString();
					} else if (asrc.isJar()) {
						jar = asrc.getResourceRef().getFile().toString();
						// should we log a warning/error if agent jar not present ?
					}
					if (jar == null) {
						throw new ExitException(BaseCommand.EXIT_INTERNAL_ERROR,
								"No jar found for agent " + asrc.getResourceRef().getOriginalResource());
					}
					fullArgs.add("-javaagent:" + jar
							+ (agent.context.getJavaAgentOption() != null
									? "=" + agent.context.getJavaAgentOption()
									: ""));

				});

			fullArgs.addAll(ctx.getRuntimeOptionsMerged(input));
			fullArgs.addAll(ctx.getAutoDetectedModuleArguments(input, requestedJavaVersion));
			fullArgs.addAll(optionalArgs);

			// deduce mainclass or jshell argument but skip it in case interactive for a jar
			// launch.
			String mainClass = ctx.getMainClassOr(input);
			if (!ctx.isInteractive() && mainClass != null) {
				fullArgs.add(mainClass);
			} else {
				if (ctx.isForceJsh() || input.isJShell()) {
					SourceSet ss = (SourceSet) input;
					for (Source s : ss.getSources()) {
						fullArgs.add(s.getResourceRef().getFile().toString());
					}
				} else if (!ctx.isInteractive() /* && src.isJar() */) {
					throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
							"no main class deduced, specified nor found in a manifest");
				}
			}
		}

		// add additonal java arguments, but if interactive that is passed via jsh
		// script
		// thus if !interactive and not jshell then generate the exit mechanics for
		// jshell to exit at the end.
		if (!ctx.isForceJsh() && !input.isJShell() && !ctx.isInteractive()) {
			addJavaArgs(ctx.getArguments(), fullArgs);
		} else if (!ctx.isInteractive()) {
			File tempFile = File.createTempFile("jbang_exit_", input.getResourceRef().getFile().getName());
			Util.writeString(tempFile.toPath(), "/exit");
			fullArgs.add(tempFile.toString());
		}

		return fullArgs;
	}

	private static void addPropertyFlags(Map<String, String> properties, String def, List<String> result) {
		properties.forEach((k, e) -> result.add(def + k + "=" + e));
	}

	private static void addJavaArgs(List<String> args, List<String> result) {
		result.addAll(args);
	}

	private static String generateMain(String main) {
		if (main != null) {
			return "\nint userMain(String[] args) { return " + main + "(args);}\n";
		}
		return "";
	}

	public static String generateArgs(List<String> args, Map<String, String> properties) {

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

	private static String generateStdInputHelper() {
		String buf = "\nStream<String> lines() { return new BufferedReader(new InputStreamReader(System.in)).lines(); }\n";
		buf += "\nStream<String> lines(String path) throws IOException { return Files.lines(Path.of(path)); }\n";
		buf += "/open PRINTING\n";
		return buf;
	}
}
