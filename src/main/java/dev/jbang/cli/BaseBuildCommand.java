package dev.jbang.cli;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jbang.net.JdkManager;
import dev.jbang.source.JarSource;
import dev.jbang.source.RunContext;
import dev.jbang.source.ScriptSource;
import dev.jbang.source.Source;
import dev.jbang.spi.IntegrationResult;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

public abstract class BaseBuildCommand extends BaseScriptDepsCommand {
	protected String javaVersion;

	@CommandLine.Option(names = { "-m",
			"--main" }, description = "Main class to use when running. Used primarily for running jar's.")
	String main;

	@CommandLine.Option(names = { "-j",
			"--java" }, description = "JDK version to use for running the script.")
	void setJavaVersion(String javaVersion) {
		if (!javaVersion.matches("\\d+[+]?")) {
			throw new IllegalArgumentException(
					"Invalid version, should be a number optionally followed by a plus sign");
		}
		this.javaVersion = javaVersion;
	}

	@CommandLine.Option(names = {
			"--cds" }, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)", negatable = true)
	Boolean cds;

	Optional<Boolean> cds() {
		return Optional.ofNullable(cds);
	}

	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image", defaultValue = "false")
	boolean nativeImage;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

	static Source buildIfNeeded(Source src, RunContext ctx) throws IOException {
		if (needsJar(src, ctx)) {
			src = build((ScriptSource) src, ctx);
		}
		return src;
	}

	static Source build(ScriptSource src, RunContext ctx) throws IOException {
		Source result = src;

		for (Map.Entry<String, String> entry : ctx.getProperties().entrySet()) {
			System.setProperty(entry.getKey(), entry.getValue());
		}

		File outjar = src.getJarFile();
		boolean nativeBuildRequired = ctx.isNativeImage() && !getImageName(outjar).exists();
		IntegrationResult integrationResult = new IntegrationResult(null, null, null);
		String requestedJavaVersion = ctx.getJavaVersion() != null ? ctx.getJavaVersion() : src.getJavaVersion();
		// always build the jar for native mode
		// it allows integrations the options to produce the native image
		boolean buildRequired = Util.isFresh() || nativeBuildRequired;
		if (!buildRequired && outjar.canRead()) {
			// We already have a Jar, check if we can still use it
			JarSource jarSrc = src.asJarSource();
			if (jarSrc == null
					|| !jarSrc.isUpToDate()
					|| JavaUtil.javaVersion(requestedJavaVersion) < JavaUtil.javaVersion(jarSrc.getJavaVersion())) {
				buildRequired = true;
			} else {
				result = ctx.importJarMetadataFor(jarSrc);
			}
		} else {
			buildRequired = true;
		}
		if (buildRequired) {
			// set up temporary folder for compilation
			File tmpJarDir = new File(outjar.getParentFile(), outjar.getName() + ".tmp");
			Util.deletePath(tmpJarDir.toPath(), true);
			tmpJarDir.mkdirs();
			// do the actual building
			try {
				integrationResult = src.buildJar(ctx, tmpJarDir, outjar, requestedJavaVersion);
			} finally {
				// clean up temporary folder
				Util.deletePath(tmpJarDir.toPath(), true);
			}
		}

		if (nativeBuildRequired) {
			if (integrationResult.nativeImagePath != null) {
				Files.move(integrationResult.nativeImagePath, getImageName(outjar).toPath());
			} else {
				buildNative(src, ctx, outjar, requestedJavaVersion);
			}
		}

		return result;
	}

	static private void buildNative(Source src, RunContext ctx, File outjar, String requestedJavaVersion)
			throws IOException {
		List<String> optionList = new ArrayList<>();
		optionList.add(resolveInGraalVMHome("native-image", requestedJavaVersion));

		optionList.add("-H:+ReportExceptionStackTraces");

		optionList.add("--enable-https");

		String classpath = ctx.resolveClassPath(src);
		if (!classpath.trim().isEmpty()) {
			optionList.add("--class-path=" + classpath);
		}

		optionList.add("-jar");
		optionList.add(outjar.toString());

		optionList.add(getImageName(outjar).toString());

		File nilog = File.createTempFile("jbang", "native-image");
		Util.verboseMsg("native-image: " + String.join(" ", optionList));
		Util.infoMsg("log: " + nilog.toString());

		Process process = new ProcessBuilder(optionList).inheritIO().redirectOutput(nilog).start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error during native-image");
		}
	}

	/** based on jar what will the binary image name be. **/
	static protected File getImageName(File outjar) {
		if (Util.isWindows()) {
			return new File(outjar.toString() + ".exe");
		} else {
			return new File(outjar.toString() + ".bin");
		}
	}

	static public String findMainClass(Path base, Path classfile) {
		StringBuilder mainClass = new StringBuilder(classfile.getFileName().toString().replace(".class", ""));
		while (!classfile.getParent().equals(base)) {
			classfile = classfile.getParent();
			mainClass.insert(0, classfile.getFileName().toString() + ".");
		}
		return mainClass.toString();
	}

	private static String resolveInGraalVMHome(String cmd, String requestedVersion) {
		String newcmd = resolveInEnv("GRAALVM_HOME", cmd);

		if (newcmd.equals(cmd) &&
				!new File(newcmd).exists()) {
			return JdkManager.resolveInJavaHome(cmd, requestedVersion);
		} else {
			return newcmd;
		}
	}

	private static String resolveInEnv(String env, String cmd) {
		if (System.getenv(env) != null) {
			if (Util.isWindows()) {
				cmd = cmd + ".exe";
			}
			return new File(System.getenv(env)).toPath().resolve("bin").resolve(cmd).toAbsolutePath().toString();
		} else {
			return cmd;
		}
	}

	// NB: This might not be a definitive list of safe characters
	static Pattern cmdSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:;@()-]*");
	// TODO: Figure out what the real list of safe characters is for PowerShell
	static Pattern pwrSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:;@()-]*");

	/**
	 * Escapes list of arguments where necessary using the current OS' way of
	 * escaping
	 */
	static List<String> escapeOSArguments(List<String> args) {
		return args.stream().map(BaseBuildCommand::escapeOSArgument).collect(Collectors.toList());
	}

	static String escapeOSArgument(String arg) {
		if (Util.isWindows()) {
			arg = escapeWindowsArgument(arg);
		} else {
			arg = Util.escapeUnixArgument(arg);
		}
		return arg;
	}

	static String escapeWindowsArgument(String arg) {
		if (Util.isUsingPowerShell()) {
			if (!pwrSafeChars.matcher(arg).matches()) {
				arg = arg.replaceAll("(['])", "''");
				arg = "'" + arg + "'";
			}
		} else {
			if (!cmdSafeChars.matcher(arg).matches()) {
				// Windows quoting is just weird
				arg = arg.replaceAll("([()!^<>&|% ])", "^$1");
				arg = arg.replaceAll("([\"])", "\\\\^$1");
				arg = "^\"" + arg + "^\"";
			}
		}
		return arg;
	}

}
