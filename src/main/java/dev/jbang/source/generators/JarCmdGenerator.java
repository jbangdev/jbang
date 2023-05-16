package dev.jbang.source.generators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.net.JdkManager;
import dev.jbang.net.JdkProvider;
import dev.jbang.source.*;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.Util;

public class JarCmdGenerator extends BaseCmdGenerator<JarCmdGenerator> {
	private List<String> runtimeOptions = Collections.emptyList();
	private boolean assertions;
	private boolean systemAssertions;
	private boolean classDataSharing;
	private String mainClass;
	private boolean mainRequired;
	private String moduleName;

	public JarCmdGenerator runtimeOptions(List<String> runtimeOptions) {
		if (runtimeOptions != null) {
			this.runtimeOptions = runtimeOptions;
		} else {
			this.runtimeOptions = Collections.emptyList();
		}
		return this;
	}

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

	public JarCmdGenerator mainClass(String mainClass) {
		this.mainClass = mainClass;
		return this;
	}

	public JarCmdGenerator mainRequired(boolean mainRequired) {
		this.mainRequired = mainRequired;
		return this;
	}

	public JarCmdGenerator moduleName(String moduleName) {
		this.moduleName = moduleName;
		return this;
	}

	public JarCmdGenerator(Project prj, BuildContext ctx) {
		super(prj, ctx);
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		String classpath = project.resolveClassPath().getClassPath();

		List<String> optionalArgs = new ArrayList<>();

		String requestedJavaVersion = project.getJavaVersion();
		JdkProvider.Jdk jdk = JdkManager.getOrInstallJdk(requestedJavaVersion);
		String javacmd = JavaUtil.resolveInJavaHome("java", requestedJavaVersion);

		addPropertyFlags(project.getProperties(), "-D", optionalArgs);

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

		if (project.enablePreview()) {
			optionalArgs.add("--enable-preview");
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

		if (ctx.getJarFile() != null) {
			if (Util.isBlankString(classpath)) {
				classpath = ctx.getJarFile().toAbsolutePath().toString();
			} else {
				classpath = ctx.getJarFile().toAbsolutePath() + Settings.CP_SEPARATOR + classpath.trim();
			}
		}
		if (!Util.isBlankString(classpath)) {
			if (moduleName != null && project.getModuleName().isPresent()) {
				optionalArgs.addAll(Arrays.asList("-p", classpath));
			} else {
				optionalArgs.addAll(Arrays.asList("-classpath", classpath));
			}
		}

		if (classDataSharing || project.enableCDS()) {
			if (jdk.getMajorVersion() >= 13) {
				Path cdsJsa = ctx.getJsaFile().toAbsolutePath();
				if (Files.exists(cdsJsa)) {
					Util.verboseMsg("CDS: Using shared archive classes from " + cdsJsa);
					optionalArgs.add("-XX:SharedArchiveFile=" + cdsJsa);
				} else {
					Util.verboseMsg("CDS: Archiving Classes At Exit at " + cdsJsa);
					optionalArgs.add("-XX:ArchiveClassesAtExit=" + cdsJsa);
				}
			} else {
				Util.warnMsg(
						"ClassDataSharing can only be used on Java versions 13 and later. Rerun with `--java 13+` to enforce that");
			}
		}

		fullArgs.add(javacmd);

		fullArgs.addAll(project.getRuntimeOptions());
		fullArgs.addAll(runtimeOptions);
		fullArgs.addAll(project.resolveClassPath().getAutoDectectedModuleArguments(jdk));
		fullArgs.addAll(optionalArgs);

		String main = Optional.ofNullable(mainClass).orElse(project.getMainClass());
		if (main != null) {
			if (moduleName != null && project.getModuleName().isPresent()) {
				String modName = moduleName.isEmpty() ? ModuleUtil.getModuleName(project) : moduleName;
				fullArgs.add("-m");
				fullArgs.add(modName + "/" + main);
			} else {
				fullArgs.add(main);
			}
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
			String requestedJavaVersion = project.getJavaVersion();
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
