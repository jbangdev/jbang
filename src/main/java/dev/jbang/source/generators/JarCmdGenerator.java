package dev.jbang.source.generators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.*;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class JarCmdGenerator extends BaseCmdGenerator<JarCmdGenerator> {
	private final Project project;

	private boolean assertions;
	private boolean systemAssertions;
	private boolean classDataSharing;
	private boolean mainRequired;

	public JarCmdGenerator assertions(boolean assertions) {
		this.assertions = assertions;
		return this;
	}

	public JarCmdGenerator systemAssertions(boolean systemAssertions) {
		this.systemAssertions = systemAssertions;
		return this;
	}

	public JarCmdGenerator classDataSharing(boolean classDataSharing) {
		this.classDataSharing = classDataSharing;
		return this;
	}

	public JarCmdGenerator mainRequired(boolean mainRequired) {
		this.mainRequired = mainRequired;
		return this;
	}

	public JarCmdGenerator(Project prj) {
		this.project = prj;
	}

	@Override
	protected Project getProject() {
		return project;
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		String classpath = project.resolveClassPath().getClassPath();

		List<String> optionalArgs = new ArrayList<>();

		String requestedJavaVersion = getProject().getJavaVersion();
		String javacmd = JavaUtil.resolveInJavaHome("java", requestedJavaVersion);

		addPropertyFlags(properties, "-D", optionalArgs);

		if (debugString != null) {
			optionalArgs.add(
					"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugString);
		}

		if (assertions) {
			optionalArgs.add("-ea");
		}

		if (systemAssertions) {
			optionalArgs.add("-esa");
		}

		if (flightRecorderString != null) {
			// TODO: find way to generate ~/.jbang/script.jfc to configure flightrecorder to
			// have 0 ms thresholds
			String jfropt = "-XX:StartFlightRecording=" + flightRecorderString
																				.replace("{baseName}",
																						Util.getBaseName(
																								project	.getResourceRef()
																										.getFile()
																										.toString()));
			optionalArgs.add(jfropt);
			Util.verboseMsg("Flight recording enabled with:" + jfropt);
		}

		if (project.getJarFile() != null) {
			if (Util.isBlankString(classpath)) {
				classpath = project.getJarFile().toAbsolutePath().toString();
			} else {
				classpath = project.getJarFile().toAbsolutePath() + Settings.CP_SEPARATOR + classpath.trim();
			}
		}
		if (!Util.isBlankString(classpath)) {
			optionalArgs.add("-classpath");
			optionalArgs.add(classpath);
		}

		if (classDataSharing || project.enableCDS()) {
			Path cdsJsa = project.getJarFile().toAbsolutePath();
			if (Files.exists(cdsJsa)) {
				Util.verboseMsg("CDS: Using shared archive classes from " + cdsJsa);
				optionalArgs.add("-XX:SharedArchiveFile=" + cdsJsa);
			} else {
				Util.verboseMsg("CDS: Archiving Classes At Exit at " + cdsJsa);
				optionalArgs.add("-XX:ArchiveClassesAtExit=" + cdsJsa);
			}
		}

		fullArgs.add(javacmd);
		addAgentsArgs(fullArgs);

		fullArgs.addAll(project.getRuntimeOptions());
		fullArgs.addAll(project.resolveClassPath().getAutoDectectedModuleArguments(requestedJavaVersion));
		fullArgs.addAll(optionalArgs);

		String mainClass = project.getMainClass();
		if (mainClass != null) {
			fullArgs.add(mainClass);
		} else if (mainRequired) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"no main class deduced, specified nor found in a manifest");
		}

		fullArgs.addAll(arguments);

		return fullArgs;
	}

	protected String generateCommandLineString(List<String> fullArgs) throws IOException {
		CommandBuffer cb = CommandBuffer.of(fullArgs);
		String args = cb.asCommandLine(shell);
		// Check if we can and need to use @-files on Windows
		boolean useArgsFile = false;
		if (args.length() > COMMAND_LINE_LENGTH_LIMIT && Util.getShell() != Util.Shell.bash) {
			// @file is only available from java 9 onwards.
			String requestedJavaVersion = getProject().getJavaVersion();
			int actualVersion = JavaUtil.javaVersion(requestedJavaVersion);
			useArgsFile = actualVersion >= 9;
		}
		if (useArgsFile) {
			return cb.asJavaArgsFile(shell);
		} else {
			return args;
		}
	}

	private static void addPropertyFlags(Map<String, String> properties, String def, List<String> result) {
		properties.forEach((k, e) -> result.add(def + k + "=" + e));
	}
}
