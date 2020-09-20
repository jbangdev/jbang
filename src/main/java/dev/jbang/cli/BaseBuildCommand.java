package dev.jbang.cli;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.*;

import io.quarkus.qute.Template;
import picocli.CommandLine;

public abstract class BaseBuildCommand extends BaseScriptCommand {
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

	@CommandLine.Option(names = { "-D" }, description = "set a system property")
	Map<String, String> properties = new HashMap<>();

	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image", defaultValue = "false")
	boolean nativeImage;

	@CommandLine.Option(names = { "--deps" }, description = "Add additional dependencies.")
	List<String> dependencies;

	@CommandLine.Option(names = { "--cp", "--class-path" }, description = "Add class path entries.")
	List<String> classpaths;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

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

				String val = jf.getManifest().getMainAttributes().getValue(Script.JBANG_JAVA_OPTIONS);
				if (val != null) {
					script.setPersistentJvmArgs(Arrays.asList( // should parse it but we are assuming it just gets
																// appendeed
							val // on command line anwyay
					));
				}
				script.setBuildJdk(
						JavaUtil.parseJavaVersion(jf.getManifest().getMainAttributes().getValue(Script.BUILD_JDK)));
			}
		}

		boolean nativeBuildRequired = nativeImage && !getImageName(outjar).exists();
		IntegrationResult integrationResult = new IntegrationResult(null, null, null);
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

			// add source files to compile
			optionList.addAll(Arrays.asList(script.getBackingFile().getPath()));
			optionList.addAll(
					script	.collectSources()
							.stream()
							.map(x -> script.getBackingFile()
											.getAbsoluteFile()
											.toPath()
											.getParent()
											.resolve(x.getDestination())
											.toString())
							.collect(Collectors.toList()));

			// add additional files
			List<FileRef> files = script.collectFiles();
			for (FileRef file : files) {
				file.copy(tmpJarDir.toPath(), Files.createTempDirectory(String.valueOf(System.currentTimeMillis())));
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

			script.setBuildJdk(JavaUtil.javaVersion(requestedJavaVersion));
			integrationResult = IntegrationManager.runIntegration(script.getRepositories(),
					script.getClassPath().getArtifacts(),
					tmpJarDir.toPath(), pomPath,
					script, nativeImage);
			if (integrationResult.mainClass != null) {
				script.setMainClass(integrationResult.mainClass);
			} else {
				try {
					// using Files.walk method with try-with-resources
					try (Stream<Path> paths = Files.walk(tmpJarDir.toPath())) {
						List<Path> items = paths.filter(Files::isRegularFile)
												.filter(f -> !f.toFile().getName().contains("$"))
												.filter(f -> f.toFile().getName().endsWith(".class"))
												.collect(Collectors.toList());

						if (items.size() > 1) { // todo: this feels like a very sketchy way to find the proper class
												// name
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
			}
			script.setPersistentJvmArgs(integrationResult.javaArgs);
			script.createJarFile(tmpJarDir, outjar);
		}

		if (nativeBuildRequired) {
			if (integrationResult.nativeImagePath != null) {
				Files.move(integrationResult.nativeImagePath, getImageName(outjar).toPath());
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

	/** based on jar what will the binary image name be. **/
	protected File getImageName(File outjar) {
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

}
