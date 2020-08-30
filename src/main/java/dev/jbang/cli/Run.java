package dev.jbang.cli;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.*;

import org.apache.commons.text.StringEscapeUtils;

import dev.jbang.*;

import io.quarkus.qute.Template;
import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Builds and runs provided script.")
public class Run extends BaseScriptCommand {
	private String javaVersion;

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

	@CommandLine.Option(names = { "-r",
			"--jfr" }, fallbackValue = "filename={baseName}.jfc", parameterConsumer = KeyValueFallbackConsumer.class, arity = "0..1", description = "Launch with Java Flight Recorder enabled.")
	String flightRecorderString;

	boolean enableFlightRecording() {
		return flightRecorderString != null;
	}

	@CommandLine.Option(names = { "-d",
			"--debug" }, fallbackValue = "4004", parameterConsumer = DebugFallbackConsumer.class, description = "Launch with java debug enabled on specified port (default: ${FALLBACK-VALUE}) ")
	String debugString;

	@CommandLine.Option(names = {
			"--cds" }, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)", negatable = true)
	Boolean cds;

	Optional<Boolean> cds() {
		return Optional.ofNullable(cds);
	}

	boolean debug() {
		return debugString != null;
	}

	@CommandLine.Option(names = { "-D" }, description = "set a system property")
	Map<String, String> properties = new HashMap<>();

	@CommandLine.Option(names = { "--interactive" }, description = "activate interactive mode")
	boolean interactive;

	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build via native-image and run", defaultValue = "false")
	boolean nativeImage;

	@CommandLine.Option(names = { "--deps" }, description = "Add additional dependencies.")
	List<String> dependencies;

	@CommandLine.Option(names = { "--cp", "--class-path" }, description = "Add class path entries.")
	List<String> classpaths;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareScript(scriptOrFile, userParams, properties, dependencies, classpaths);

		if (script.needsJar()) {
			build(script);
		}

		String cmdline = generateCommandLine(script);
		debug("run: " + cmdline);
		out.println(cmdline);

		return 0;
	}

	// build with javac and then jar... todo: split up in more testable chunks
	void build(Script script) throws IOException {
		File baseDir = Settings.getCacheDir(Settings.CacheClass.jars).toFile();
		File tmpJarDir = new File(baseDir, script.getBackingFile().getName() +
				"." + Util.getStableID(script.getBackingFile()));
		tmpJarDir.mkdirs();

		File outjar = new File(tmpJarDir.getParentFile(), tmpJarDir.getName() + ".jar");

		if (outjar.exists()) {
			try (JarFile jf = new JarFile(outjar)) {
				script.setMainClass(
						jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
				script.setBuildJdk(
						JavaUtil.parseJavaVersion(jf.getManifest().getMainAttributes().getValue("Build-Jdk")));
			}
		}

		boolean nativeBuildRequired = nativeImage && !getImageName(outjar).exists();
		Path externalNativeImage = null;
		String requestedJavaVersion = javaVersion != null ? javaVersion : script.javaVersion();
		// always build the jar for native mode
		// it allows integrations the options to produce the native image
		if (!outjar.exists() || JavaUtil.javaVersion(requestedJavaVersion) < script.getBuildJdk()
				|| nativeBuildRequired) {
			List<String> optionList = new ArrayList<String>();
			optionList.add(resolveInJavaHome("javac", requestedJavaVersion));
			optionList.addAll(script.collectCompileOptions());
			String path = script.resolveClassPath(offline);
			if (!path.trim().isEmpty()) {
				optionList.addAll(Arrays.asList("-classpath", path));
			}
			optionList.addAll(Arrays.asList("-d", tmpJarDir.getAbsolutePath()));
			optionList.addAll(Arrays.asList(script.getBackingFile().getPath()));

			// add additional files
			List<FileRef> files = script.collectFiles();
			for (FileRef file : files) {
				Path from = file.from();
				Path to = file.to(tmpJarDir.toPath());
				Util.verboseMsg("Copying " + from + " to " + to);
				try {
					if (!to.toFile().getParentFile().exists()) {
						to.toFile().getParentFile().mkdirs();
					}
					Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ioe) {
					throw new ExitException(CommandLine.ExitCode.USAGE, "Could not copy " + from + " to " + to, ioe);
				}
			}

			Template pomTemplate = Settings.getTemplateEngine().getTemplate("pom.qute.xml");

			Path pomPath = null;
			if (pomTemplate == null) {
				// ignore
				Util.warnMsg("Could not locate pom.xml template");
			} else {
				String pomfile = pomTemplate
											.data("baseName", Util.getBaseName(script.getBackingFile().getName()))
											.data("dependencies", script.getClassPath().getArtifacts())
											.render();
				pomPath = new File(tmpJarDir, "META-INF/maven/g/a/v/pom.xml").toPath();
				Files.createDirectories(pomPath.getParent());
				Util.writeString(pomPath, pomfile);
			}

			info("Building jar...");
			debug("compile: " + String.join(" ", optionList));

			Process process = new ProcessBuilder(optionList).inheritIO().start();
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				throw new ExitException(1, e);
			}

			if (process.exitValue() != 0) {
				throw new ExitException(1, "Error during compile");
			}

			try {
				// using Files.walk method with try-with-resources
				try (Stream<Path> paths = Files.walk(tmpJarDir.toPath())) {
					List<Path> items = paths.filter(Files::isRegularFile)
											.filter(f -> !f.toFile().getName().contains("$"))
											.filter(f -> f.toFile().getName().endsWith(".class"))
											.collect(Collectors.toList());

					if (items.size() > 1) { // todo: this feels like a very sketchy way to find the proper class name
						// but it works.
						String mainname = script.getBackingFile().getName().replace(".java", ".class");
						items = items	.stream()
										.filter(f -> f.toFile().getName().equalsIgnoreCase(mainname))
										.collect(Collectors.toList());
					}

					if (items.size() != 1) {
						throw new ExitException(1,
								"Could not locate unique class. Found " + items.size() + " candidates.");
					} else {
						Path classfile = items.get(0);
						String mainClass = findMainClass(tmpJarDir.toPath(), classfile);
						script.setMainClass(mainClass);
					}
				}
			} catch (IOException e) {
				throw new ExitException(1, e);
			}
			script.setBuildJdk(JavaUtil.javaVersion(requestedJavaVersion));
			externalNativeImage = IntegrationManager.runIntegration(script.getRepositories(),
					script.getClassPath().getArtifacts(),
					tmpJarDir.toPath(), pomPath,
					script, nativeImage);
			script.createJarFile(tmpJarDir, outjar);
		}

		if (nativeBuildRequired) {
			if (externalNativeImage != null) {
				Files.move(externalNativeImage, getImageName(outjar).toPath());
			} else {
				List<String> optionList = new ArrayList<String>();
				optionList.add(resolveInGraalVMHome("native-image", requestedJavaVersion));

				optionList.add("-H:+ReportExceptionStackTraces");

				optionList.add("--enable-https");

				String classpath = script.resolveClassPath(offline);
				if (!classpath.trim().isEmpty()) {
					optionList.add("--class-path=" + classpath);
				}

				optionList.add("-jar");
				optionList.add(outjar.toString());

				optionList.add(getImageName(outjar).toString());

				File nilog = File.createTempFile("jbang", "native-image");
				debug("native-image: " + String.join(" ", optionList));
				info("log: " + nilog.toString());

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
		}
		script.setJar(outjar);
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
			}

			fullArgs.add(javacmd);
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

	/** based on jar what will the binary image name be. **/
	private File getImageName(File outjar) {
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

	// There are probably more safe characters, but I couldn't find a definitive
	// list
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

	String generateArgs(List<String> args, Map<String, String> properties) {

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

	String resolveInJavaHome(String cmd, String requestedVersion) {
		Path jdkHome = JdkManager.getCurrentJdk(requestedVersion);
		if (jdkHome != null) {
			if (Util.isWindows()) {
				cmd = cmd + ".exe";
			}
			return jdkHome.resolve("bin").resolve(cmd).toAbsolutePath().toString();
		}
		return cmd;
	}

	String resolveInGraalVMHome(String cmd, String requestedVersion) {
		String newcmd = resolveInEnv("GRAALVM_HOME", cmd);

		if (newcmd.equals(cmd) &&
				!new File(newcmd).exists()) {
			return resolveInJavaHome(cmd, requestedVersion);
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
