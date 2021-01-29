package dev.jbang.cli;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;

import dev.jbang.DecoratedSource;
import dev.jbang.ExitException;
import dev.jbang.FileRef;
import dev.jbang.IntegrationManager;
import dev.jbang.IntegrationResult;
import dev.jbang.JarUtil;
import dev.jbang.JavaUtil;
import dev.jbang.JdkManager;
import dev.jbang.KeyValue;
import dev.jbang.ScriptSource;
import dev.jbang.Settings;
import dev.jbang.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;

public abstract class BaseBuildCommand extends BaseScriptCommand {
	public static final Type STRINGARRAYTYPE = Type.create(DotName.createSimple("[Ljava.lang.String;"),
			Type.Kind.ARRAY);
	public static final Type STRINGTYPE = Type.create(DotName.createSimple("java.lang.String"), Type.Kind.CLASS);
	public static final Type INSTRUMENTATIONTYPE = Type.create(
			DotName.createSimple("java.lang.instrument.Instrumentation"), Type.Kind.CLASS);
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

	@CommandLine.Option(names = { "-D" }, description = "set a system property", mapFallbackValue = "true")
	Map<String, String> properties = new HashMap<>();

	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image", defaultValue = "false")
	boolean nativeImage;

	@CommandLine.Option(names = { "--deps" }, description = "Add additional dependencies.")
	List<String> dependencies;

	@CommandLine.Option(names = { "--cp", "--class-path" }, description = "Add class path entries.")
	List<String> classpaths;

	@CommandLine.Option(names = {
			"-f",
			"--fresh" }, description = "Make it a fresh run - i.e. a new build with fresh (i.e. non-cached) resources.", defaultValue = "false")
	boolean fresh;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

	protected boolean createdJar;

	// build with javac and then jar... todo: split up in more testable chunks
	void build(DecoratedSource xrunit) throws IOException {
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			System.setProperty(entry.getKey(), entry.getValue());
		}
		File outjar = xrunit.getJar();
		if (outjar.exists()) {
			try (JarFile jf = new JarFile(outjar)) {
				xrunit.setMainClass(
						jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));

				String val = jf.getManifest().getMainAttributes().getValue(ScriptSource.JBANG_JAVA_OPTIONS);
				if (val != null) {
					xrunit.setPersistentJvmArgs(Arrays.asList( // should parse it but we are assuming it just gets
							// appendeed
							val // on command line anwyay
					));
				}
				xrunit.setBuildJdk(
						JavaUtil.parseJavaVersion(
								jf.getManifest().getMainAttributes().getValue(ScriptSource.BUILD_JDK)));
			}
		}

		boolean nativeBuildRequired = nativeImage && !getImageName(outjar).exists();
		IntegrationResult integrationResult = new IntegrationResult(null, null, null);
		String requestedJavaVersion = javaVersion != null ? javaVersion : xrunit.javaVersion();
		// always build the jar for native mode
		// it allows integrations the options to produce the native image
		if (!outjar.exists() || JavaUtil.javaVersion(requestedJavaVersion) < xrunit.getBuildJdk()
				|| nativeBuildRequired || fresh) {
			// set up temporary folder for compilation
			File tmpJarDir = new File(outjar.getParentFile(), outjar.getName() + ".tmp");
			Util.deletePath(tmpJarDir.toPath(), true);
			tmpJarDir.mkdirs();
			try {
				integrationResult = buildJar(xrunit, tmpJarDir, outjar, requestedJavaVersion);
			} finally {
				// clean up temporary folder
				Util.deletePath(tmpJarDir.toPath(), true);
			}
		}

		if (nativeBuildRequired) {
			if (integrationResult.nativeImagePath != null) {
				Files.move(integrationResult.nativeImagePath, getImageName(outjar).toPath());
			} else {
				buildNative(xrunit, outjar, requestedJavaVersion);
			}
		}
	}

	private IntegrationResult buildJar(DecoratedSource xrunit, File tmpJarDir, File outjar,
			String requestedJavaVersion)
			throws IOException {
		IntegrationResult integrationResult;
		List<String> optionList = new ArrayList<String>();
		optionList.add(resolveInJavaHome("javac", requestedJavaVersion));
		optionList.addAll(xrunit.script().getCompileOptions());
		String path = xrunit.resolveClassPath(offline);
		if (!path.trim().isEmpty()) {
			optionList.addAll(Arrays.asList("-classpath", path));
		}
		optionList.addAll(Arrays.asList("-d", tmpJarDir.getAbsolutePath()));

		// add source files to compile
		optionList.add(xrunit.getResourceRef().getFile().getPath());
		optionList.addAll(
				xrunit	.script()
						.getAllSources()
						.stream()
						.map(x -> x.getResourceRef().getFile().getPath())
						.collect(Collectors.toList()));

		// add additional files
		List<FileRef> files = xrunit.script().getAllFiles();
		for (FileRef file : files) {
			file.copy(tmpJarDir.toPath(), fresh);
		}

		Template pomTemplate = Settings.getTemplateEngine().getTemplate("pom.qute.xml");

		Path pomPath = null;
		if (pomTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate pom.xml template");
		} else {
			String pomfile = pomTemplate
										.data("baseName", Util.getBaseName(xrunit.getResourceRef().getFile().getName()))
										.data("dependencies", xrunit.getClassPath().getArtifacts())
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

		xrunit.setBuildJdk(JavaUtil.javaVersion(requestedJavaVersion));
		integrationResult = IntegrationManager.runIntegration(xrunit.script().getAllRepositories(),
				xrunit.getClassPath().getArtifacts(),
				tmpJarDir.toPath(), pomPath,
				xrunit.script(), nativeImage);
		if (integrationResult.mainClass != null) {
			xrunit.setMainClass(integrationResult.mainClass);
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
						String mainname = xrunit.getResourceRef().getFile().getName().replace(".java", ".class");
						items = items	.stream()
										.filter(f -> f.toFile().getName().equalsIgnoreCase(mainname))
										.collect(Collectors.toList());
					}

					if (items.size() != 1) {
						throw new ExitException(1,
								"Could not locate unique class. Found " + items.size() + " candidates.");
					} else {
						Path classfile = items.get(0);
						// TODO: could we use jandex to find the right main class more sanely ?
						// String mainClass = findMainClass(tmpJarDir.toPath(), classfile);

						Indexer indexer = new Indexer();
						InputStream stream = new FileInputStream(classfile.toFile());
						indexer.index(stream);
						Index index = indexer.complete();

						Collection<ClassInfo> clazz = index.getKnownClasses();

						Optional<ClassInfo> main = clazz.stream()
														.filter(pubClass -> pubClass.method("main",
																STRINGARRAYTYPE) != null)
														.findFirst();

						if (main.isPresent()) {
							xrunit.setMainClass(main.get().name().toString());
						}

						if (xrunit.script().isAgent()) {

							Optional<ClassInfo> agentmain = clazz	.stream()
																	.filter(pubClass -> pubClass.method("agentmain",
																			STRINGTYPE,
																			INSTRUMENTATIONTYPE) != null
																			||
																			pubClass.method("agentmain",
																					STRINGTYPE) != null)
																	.findFirst();

							if (agentmain.isPresent()) {
								xrunit.setAgentMainClass(agentmain.get().name().toString());
							}

							Optional<ClassInfo> premain = clazz	.stream()
																.filter(pubClass -> pubClass.method("premain",
																		STRINGTYPE,
																		INSTRUMENTATIONTYPE) != null
																		||
																		pubClass.method("premain",
																				STRINGTYPE) != null)
																.findFirst();

							if (premain.isPresent()) {
								xrunit.setPreMainClass(premain.get().name().toString());
							}
						}

					}
				}
			} catch (IOException e) {
				throw new ExitException(1, e);
			}
		}
		xrunit.setPersistentJvmArgs(integrationResult.javaArgs);
		createJarFile(xrunit, tmpJarDir, outjar);
		createdJar = true;
		return integrationResult;
	}

	private void buildNative(DecoratedSource xrunit, File outjar, String requestedJavaVersion) throws IOException {
		List<String> optionList = new ArrayList<String>();
		optionList.add(resolveInGraalVMHome("native-image", requestedJavaVersion));

		optionList.add("-H:+ReportExceptionStackTraces");

		optionList.add("--enable-https");

		String classpath = xrunit.resolveClassPath(offline);
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

	static void createJarFile(DecoratedSource xrunit, File path, File output) throws IOException {
		String mainclass = xrunit.getMainClass();
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if (mainclass != null) {
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainclass);
		}

		if (xrunit.script().isAgent()) {
			if (xrunit.getPreMainClass() != null) {
				manifest.getMainAttributes().put(new Attributes.Name("Premain-Class"), xrunit.getPreMainClass());
			}
			if (xrunit.getAgentMainClass() != null) {
				manifest.getMainAttributes().put(new Attributes.Name("Agent-Class"), xrunit.getAgentMainClass());
			}

			for (KeyValue kv : xrunit.script().getAllAgentOptions()) {
				if (kv.getKey().trim().isEmpty()) {
					continue;
				}
				Attributes.Name k = new Attributes.Name(kv.getKey());
				String v = kv.getValue() == null ? "true" : kv.getValue();
				manifest.getMainAttributes().put(k, v);
			}

			if (xrunit.getClassPath() != null) {
				String bootClasspath = xrunit.getClassPath().getManifestPath();
				if (!bootClasspath.isEmpty()) {
					manifest.getMainAttributes().put(new Attributes.Name("Boot-Class-Path"), bootClasspath);
				}
			}
		} else {
			if (xrunit.getClassPath() != null) {
				String classpath = xrunit.getClassPath().getManifestPath();
				if (!classpath.isEmpty()) {
					manifest.getMainAttributes().put(new Attributes.Name("Class-Path"), classpath);
				}
			}
		}

		if (xrunit.getPersistentJvmArgs() != null) {
			manifest.getMainAttributes()
					.putValue("JBang-Java-Options", String.join(" ", xrunit.getPersistentJvmArgs()));
		}
		int buildJdk = xrunit.getBuildJdk();
		if (buildJdk > 0) {
			String val = buildJdk >= 9 ? Integer.toString(buildJdk) : "1." + buildJdk;
			manifest.getMainAttributes().putValue("Build-Jdk", val);
		}

		FileOutputStream target = new FileOutputStream(output);
		JarUtil.jar(target, path.listFiles(), null, null, manifest);
		target.close();
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

	protected static String resolveInJavaHome(String cmd, String requestedVersion) {
		Path jdkHome = JdkManager.getCurrentJdk(requestedVersion);
		if (jdkHome != null) {
			if (Util.isWindows()) {
				cmd = cmd + ".exe";
			}
			return jdkHome.resolve("bin").resolve(cmd).toAbsolutePath().toString();
		}
		return cmd;
	}

	private static String resolveInGraalVMHome(String cmd, String requestedVersion) {
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
