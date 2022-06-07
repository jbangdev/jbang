package dev.jbang.source.generators;

import static dev.jbang.source.builders.BaseBuilder.getImageName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.*;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class JarCmdGenerator extends BaseCmdGenerator {
	protected final Code code;

	public JarCmdGenerator(Code code, RunContext ctx) {
		super(ctx);
		this.code = code;
	}

	@Override
	protected Code getCode() {
		return code;
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		if (ctx.isNativeImage()) {
			String imagename = getImageName(code.getJarFile()).toString();
			if (new File(imagename).exists()) {
				fullArgs.add(imagename);
			} else {
				Util.warnMsg("native built image not found - running in java mode.");
			}
		}

		if (fullArgs.isEmpty()) {
			String classpath = ctx.resolveClassPath(code).getClassPath();

			List<String> optionalArgs = new ArrayList<>();

			String requestedJavaVersion = ctx.getJavaVersionOr(getCode());
			String javacmd = JavaUtil.resolveInJavaHome("java", requestedJavaVersion);

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
																			Util.getBaseName(code	.getResourceRef()
																									.getFile()
																									.toString()));
				optionalArgs.add(jfropt);
				Util.verboseMsg("Flight recording enabled with:" + jfropt);
			}

			if (code.getJarFile() != null) {
				if (Util.isBlankString(classpath)) {
					classpath = code.getJarFile().toAbsolutePath().toString();
				} else {
					classpath = code.getJarFile().toAbsolutePath() + Settings.CP_SEPARATOR + classpath.trim();
				}
			}
			if (!Util.isBlankString(classpath)) {
				optionalArgs.add("-classpath");
				optionalArgs.add(classpath);
			}

			if (Optional.ofNullable(ctx.getClassDataSharing()).orElse(code.enableCDS())) {
				Path cdsJsa = code.getJarFile().toAbsolutePath();
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

			fullArgs.addAll(ctx.getRuntimeOptionsMerged(code));
			fullArgs.addAll(ctx.getAutoDetectedModuleArguments(code, requestedJavaVersion));
			fullArgs.addAll(optionalArgs);

			String mainClass = ctx.getMainClassOr(code);
			if (mainClass != null) {
				fullArgs.add(mainClass);
			} else if (ctx.isMainRequired()) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
						"no main class deduced, specified nor found in a manifest");
			}
		}

		fullArgs.addAll(ctx.getArguments());

		return fullArgs;
	}

	private static void addPropertyFlags(Map<String, String> properties, String def, List<String> result) {
		properties.forEach((k, e) -> result.add(def + k + "=" + e));
	}
}
