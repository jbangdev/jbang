package dev.jbang.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

import dev.jbang.ExitException;
import dev.jbang.Script;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Builds and runs provided script.")
public class Run extends BaseBuildCommand {

	@CommandLine.Option(names = { "-r",
			"--jfr" }, fallbackValue = "filename={baseName}.jfr", parameterConsumer = KeyValueFallbackConsumer.class, arity = "0..1", description = "Launch with Java Flight Recorder enabled.")
	String flightRecorderString;

	boolean enableFlightRecording() {
		return flightRecorderString != null;
	}

	@CommandLine.Option(names = { "-d",
			"--debug" }, fallbackValue = "4004", parameterConsumer = DebugFallbackConsumer.class, description = "Launch with java debug enabled on specified port (default: ${FALLBACK-VALUE}) ")
	String debugString;

	boolean debug() {
		return debugString != null;
	}

	@CommandLine.Option(names = { "--javaagent" }, parameterConsumer = KeyOptionalValueConsumer.class)
	Map<String, Optional<String>> javaAgentSlots;

	void setJavaAgent(String param) {
		Optional<String> javaAgent = Optional.empty();
		Optional<String> javaAgentOptions = Optional.empty();

		int eqpos = param.indexOf("=");

		if (eqpos >= 0) {
			javaAgent = Optional.of(param.substring(0, eqpos));
			javaAgentOptions = Optional.of(param.substring(eqpos + 1));
		} else {
			javaAgent = Optional.of(param);
			javaAgentOptions = Optional.empty();
		}
	}

	@CommandLine.Option(names = { "--interactive" }, description = "activate interactive mode")
	boolean interactive;

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareArtifacts(prepareScript(scriptOrFile, userParams, properties, dependencies, classpaths));

		String cmdline = generateCommandLine(script);
		debug("run: " + cmdline);
		out.println(cmdline);

		return EXIT_EXECUTE;
	}

	Script prepareArtifacts(Script script) throws IOException {
		if (script.needsJar()) {
			build(script);
		}

		if (javaAgentSlots != null) {
			for (Map.Entry<String, Optional<String>> agentOption : javaAgentSlots.entrySet()) {
				String javaAgent = agentOption.getKey();
				Optional<String> javaAgentOptions = agentOption.getValue();

				Script agentScript = prepareScript(javaAgent, userParams, properties, dependencies, classpaths);
				agentScript.setJavaAgentOption(javaAgentOptions.orElse(null));
				if (agentScript.needsJar()) {
					info("Building javaagent...");
					build(agentScript);
				}

				script.addJavaAgent(agentScript);
			}
		}
		return script;
	}

	String generateCommandLine(Script script) throws IOException {

		List<String> fullArgs = new ArrayList<>();

		if (nativeImage) {
			String imagename = getImageName(script.getJar()).toString();
			if (new File(imagename).exists()) {
				fullArgs.add(imagename);
			} else {
				warn("native built image not found - running in java mode.");
			}
		}

		if (fullArgs.isEmpty()) {
			String classpath = script.resolveClassPath(offline);

			List<String> optionalArgs = new ArrayList<String>();

			String requestedJavaVersion = javaVersion != null ? javaVersion : script.javaVersion();
			String javacmd = resolveInJavaHome("java", requestedJavaVersion);
			if (script.getBackingFile().getName().endsWith(".jsh")) {

				javacmd = resolveInJavaHome("jshell", requestedJavaVersion);
				if (!classpath.trim().isEmpty()) {
					optionalArgs.add("--class-path=" + classpath);
				}

				optionalArgs.add("--startup=DEFAULT");

				File tempFile = File.createTempFile("jbang_arguments_", script.getBackingFile().getName());
				Util.writeString(tempFile.toPath(), generateArgs(script.getArguments(), script.getProperties()));

				optionalArgs.add("--startup=" + tempFile.getAbsolutePath());

				if (debug()) {
					warn("debug not possible when running via jshell.");
				}
				if (enableFlightRecording()) {
					warn("Java Flight Recording not possible when running via jshell.");
				}

			} else {
				addPropertyFlags(script.getProperties(), "-D", optionalArgs);

				// optionalArgs.add("--source 11");
				if (debug()) {
					optionalArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugString);
				}

				if (enableFlightRecording()) {
					// TODO: find way to generate ~/.jbang/script.jfc to configure flightrecorder to
					// have 0 ms thresholds
					String jfropt = "-XX:StartFlightRecording=" + flightRecorderString.replace("{baseName}",
							Util.getBaseName(script.getBackingFile().toString()));
					optionalArgs.add(jfropt);
					Util.verboseMsg("Flight recording enabled with:" + jfropt);
				}

				if (!classpath.trim().isEmpty()) {
					optionalArgs.add("-classpath");
					optionalArgs.add(classpath);
				}

				if (optionActive(cds(), script.enableCDS())) {
					String cdsJsa = script.getJar().getAbsolutePath() + ".jsa";
					if (script.wasJarCreated()) {
						debug("CDS: Archiving Classes At Exit at " + cdsJsa);
						optionalArgs.add("-XX:ArchiveClassesAtExit=" + cdsJsa);
					} else {
						debug("CDS: Using shared archive classes from " + cdsJsa);
						optionalArgs.add("-XX:SharedArchiveFile=" + cdsJsa);
					}
				}
				if (script.getPersistentJvmArgs() != null) {
					optionalArgs.addAll(script.getPersistentJvmArgs());
				}
			}

			fullArgs.add(javacmd);
			script	.getJavaAgents()
					.forEach(agent -> {
						// for now we don't include any transitive dependencies. could consider putting
						// on bootclasspath...or not.
						String jar = null;

						if (agent.getJar() != null) {
							jar = agent.getJar().toString();
						} else if (agent.forJar()) {
							jar = agent.getBackingFile().toString();
							// should we log a warning/error if agent jar not present ?
						}
						if (jar == null) {
							throw new ExitException(EXIT_INTERNAL_ERROR,
									"No jar found for agent " + agent.getOriginalFile());
						}
						fullArgs.add("-javaagent:" + jar
								+ (agent.getJavaAgentOption() != null ? "=" + agent.getJavaAgentOption() : ""));

					});

			fullArgs.addAll(script.collectRuntimeOptions());
			fullArgs.addAll(script.getAutoDetectedModuleArguments(requestedJavaVersion, offline));
			fullArgs.addAll(optionalArgs);

			if (main != null) { // if user specified main class it overrides any other main class calculation
				script.setMainClass(main);
			}

			if (script.getMainClass() != null) {
				fullArgs.add(script.getMainClass());
			} else {
				if (script.forJar()) {
					fullArgs.add("-jar");
				}
				fullArgs.add(script.getBackingFile().toString());
			}
		}

		if (!script.forJShell()) {
			addJavaArgs(script.getArguments(), fullArgs);
		} else if (!interactive) {
			File tempFile = File.createTempFile("jbang_exit_", script.getBackingFile().getName());
			Util.writeString(tempFile.toPath(), "/exit");
			fullArgs.add(tempFile.toString());
		}

		return String.join(" ", escapeArguments(fullArgs));

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
		args.forEach(arg -> {
			result.add(arg);
		});
	}

	static List<String> escapeArguments(List<String> args) {
		return args.stream().map(Run::escapeArgument).collect(Collectors.toList());
	}

	// NB: This might not be a definitive list of safe characters
	static Pattern cmdSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:;@()-]*");

	static Pattern shellSafeChars = Pattern.compile("[a-zA-Z0-9._+=:@%/-]*");

	static String escapeArgument(String arg) {
		if (Util.isWindows()) {
			if (!cmdSafeChars.matcher(arg).matches()) {
				// Windows quoting is just weird
				arg = arg.replaceAll("([()!^<>&|% ])", "^$1");
				arg = arg.replaceAll("([\"])", "\\\\^$1");
				return "^\"" + arg + "^\"";
			}
		} else {
			if (!shellSafeChars.matcher(arg).matches()) {
				arg = arg.replaceAll("(['])", "'\\\\''");
				return "'" + arg + "'";
			}
		}
		return arg;
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
